package com.laker.postman.codegen.request;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.util.FileMimeTypeUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.bodyCharset;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.bodyPayload;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.contentType;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.effectiveHeaders;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.enabledFormData;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.hasBinaryBody;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.isFormDataBody;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.method;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.nullToEmpty;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.urlWithParams;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.usesDigestAuth;
import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.usesTemplatedBasicAuth;

/** Generates a standalone C# console program using the .NET HTTP client. */
@UtilityClass
public class CSharpRequestCodeGenerator {
    private static final String INDENT = "        ";
    private static final Set<String> CONTENT_HEADERS = Set.of(
            "allow", "expires", "last-modified");

    public static String generate(HttpRequestItem request) {
        if (request == null) {
            return "";
        }
        StringBuilder code = new StringBuilder("""
                using System;
                using System.IO;
                using System.Linq;
                using System.Net;
                using System.Net.Http;
                using System.Net.Http.Headers;
                using System.Text;
                using System.Threading.Tasks;

                internal static class Program
                {
                    private static async Task Main()
                    {
                """);
        line(code, "var url = " + string(urlWithParams(request)) + ";");
        boolean digestAuth = usesDigestAuth(request);
        boolean disableRedirects = Boolean.FALSE.equals(request.getFollowRedirects());
        if (digestAuth) {
            // Digest challenges after redirects can occur outside the original URL's directory.
            line(code, "var credentialOrigin = new Uri(new Uri(url), \"/\");");
            line(code, "var credentials = new CredentialCache();");
            line(code, "credentials.Add(credentialOrigin, \"Digest\", new NetworkCredential("
                    + string(request.getAuthUsername()) + ", " + string(request.getAuthPassword()) + "));");
        }
        if (digestAuth || disableRedirects) {
            String options = digestAuth ? "Credentials = credentials" : "";
            if (disableRedirects) {
                options += (digestAuth ? ", " : "") + "AllowAutoRedirect = false";
            }
            line(code, "using var handler = new HttpClientHandler { " + options + " };");
            line(code, "using var client = new HttpClient(handler);");
        } else {
            line(code, "using var client = new HttpClient();");
        }
        line(code, "using var request = new HttpRequestMessage(new HttpMethod(" + string(method(request)) + "), url);");

        boolean hasBody = appendBody(code, request);
        List<HttpHeader> headers = effectiveHeaders(request);
        if (!hasBody && headers.stream().anyMatch(CSharpRequestCodeGenerator::isContentHeader)) {
            line(code, "request.Content = new ByteArrayContent(Array.Empty<byte>());");
        }
        for (HttpHeader header : headers) {
            String key = header.getKey().trim();
            // HttpClient computes framing from the generated content, including multipart boundaries.
            if ("Content-Length".equalsIgnoreCase(key)
                    || (hasBody && "Content-Type".equalsIgnoreCase(key))) {
                continue;
            }
            String target = isContentHeader(header) ? "request.Content.Headers" : "request.Headers";
            line(code, target + ".TryAddWithoutValidation(" + string(key) + ", " + string(header.getValue()) + ");");
        }
        if (usesTemplatedBasicAuth(request)) {
            line(code, "request.Headers.Authorization = new AuthenticationHeaderValue(\"Basic\",");
            line(code, "    Convert.ToBase64String(Encoding.UTF8.GetBytes("
                    + string(request.getAuthUsername() + ":" + nullToEmpty(request.getAuthPassword())) + ")));");
        }
        code.append('\n');
        line(code, "using var response = await client.SendAsync(request);");
        line(code, "Console.WriteLine(await response.Content.ReadAsStringAsync());");
        code.append("    }\n");
        if (isFormDataBody(request)) {
            appendMultipartParameterHelper(code);
        }
        return code.append("}\n").toString();
    }

    private static boolean appendBody(StringBuilder code, HttpRequestItem request) {
        if (isFormDataBody(request)) {
            appendMultipart(code, request);
            return true;
        }
        if (hasBinaryBody(request)) {
            line(code, "request.Content = new StreamContent(File.OpenRead(" + string(request.getBody()) + "));");
        } else if (RequestBodyTypes.BODY_TYPE_RAW.equals(request.getBodyType())
                && !StandardCharsets.UTF_8.equals(bodyCharset(request))) {
            StringJoiner bytes = new StringJoiner(", ", "new byte[] { ", " }");
            for (byte value : bodyPayload(request).getBytes(bodyCharset(request))) {
                bytes.add(String.format(Locale.ROOT, "0x%02X", value & 0xff));
            }
            line(code, "request.Content = new ByteArrayContent(" + bytes + ");");
        } else if (RequestBodyTypes.BODY_TYPE_RAW.equals(request.getBodyType())
                || RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType())) {
            // Use the same form encoding and raw content normalization as the app transport.
            line(code, "request.Content = new ByteArrayContent(Encoding.UTF8.GetBytes("
                    + string(bodyPayload(request)) + "));");
        } else {
            return false;
        }
        line(code, "request.Content.Headers.TryAddWithoutValidation(\"Content-Type\", "
                + string(contentType(request)) + ");");
        return true;
    }

    private static void appendMultipart(StringBuilder code, HttpRequestItem request) {
        line(code, "using var multipart = new MultipartFormDataContent();");
        line(code, "multipart.HeaderEncodingSelector = (_, _) => Encoding.UTF8;");
        line(code, "request.Content = multipart;");
        effectiveHeaders(request).stream()
                .filter(header -> "Content-Type".equalsIgnoreCase(header.getKey().trim()))
                .map(HttpHeader::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .ifPresent(type -> appendMultipartContentType(code, type));
        int fileIndex = 0;
        int textIndex = 0;
        for (HttpFormData item : enabledFormData(request)) {
            if (item.isFile()) {
                String variable = "fileContent" + ++fileIndex;
                line(code, "using var " + variable + " = new StreamContent(File.OpenRead(" + string(item.getValue()) + "));");
                line(code, variable + ".Headers.TryAddWithoutValidation(\"Content-Type\", "
                        + string(FileMimeTypeUtil.detectMimeType(item.getValue())) + ");");
                appendMultipartDisposition(code, variable, item.getKey(), nullToEmpty(item.getValue()));
                line(code, "multipart.Add(" + variable + ");");
            } else {
                String variable = "textContent" + ++textIndex;
                line(code, "using var " + variable + " = new StringContent(" + string(item.getValue()) + ", Encoding.UTF8);");
                appendMultipartDisposition(code, variable, item.getKey(), null);
                line(code, "multipart.Add(" + variable + ");");
            }
        }
    }

    private static void appendMultipartDisposition(StringBuilder code, String variable, String name, String filePath) {
        String value = string("form-data; name=\"") + " + EscapeMultipartParameter(" + string(name) + ") + ";
        if (filePath == null) {
            value += string("\"");
        } else {
            value += string("\"; filename=\"") + " + EscapeMultipartParameter(Path.GetFileName("
                    + string(filePath) + ")) + " + string("\"");
        }
        line(code, variable + ".Headers.TryAddWithoutValidation(\"Content-Disposition\", " + value + ");");
    }

    private static void appendMultipartParameterHelper(StringBuilder code) {
        code.append("\n    private static string EscapeMultipartParameter(string value)\n    {\n");
        line(code, "return value.Replace(" + string("\\") + ", " + string("\\\\") + ")");
        line(code, "    .Replace(" + string("\"") + ", " + string("\\\"") + ")");
        line(code, "    .Replace(" + string("\r") + ", " + string("%0D") + ")");
        line(code, "    .Replace(" + string("\n") + ", " + string("%0A") + ");");
        code.append("    }\n");
    }

    private static void appendMultipartContentType(StringBuilder code, String contentType) {
        line(code, "var multipartContentType = MediaTypeHeaderValue.Parse(" + string(contentType) + ");");
        line(code, "var multipartBoundary = multipart.Headers.ContentType!.Parameters.First(parameter =>");
        line(code, "    string.Equals(parameter.Name, \"boundary\", StringComparison.OrdinalIgnoreCase));");
        line(code, "foreach (var parameter in multipartContentType.Parameters.Where(parameter =>");
        line(code, "    string.Equals(parameter.Name, \"boundary\", StringComparison.OrdinalIgnoreCase)).ToArray())");
        line(code, "{");
        line(code, "    multipartContentType.Parameters.Remove(parameter);");
        line(code, "}");
        line(code, "multipartContentType.Parameters.Add(new NameValueHeaderValue(\"boundary\", multipartBoundary.Value));");
        line(code, "multipart.Headers.ContentType = multipartContentType;");
    }

    private static boolean isContentHeader(HttpHeader header) {
        String name = header.getKey().trim().toLowerCase(Locale.ROOT);
        return name.startsWith("content-") || CONTENT_HEADERS.contains(name);
    }

    private static void line(StringBuilder code, String line) {
        code.append(INDENT).append(line).append('\n');
    }

    private static String string(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char character : nullToEmpty(value).toCharArray()) {
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(character) || Character.isSurrogate(character)
                            || Character.getType(character) == Character.FORMAT
                            || character == '\u2028' || character == '\u2029') {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
