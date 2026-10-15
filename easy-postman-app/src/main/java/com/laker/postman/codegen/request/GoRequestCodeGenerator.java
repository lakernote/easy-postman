package com.laker.postman.codegen.request;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.util.FileMimeTypeUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@UtilityClass
public class GoRequestCodeGenerator {

    public static String generate(HttpRequestItem request) {
        boolean multipart = RequestCodeSnippetSupport.isFormDataBody(request);
        List<HttpFormData> formData = RequestCodeSnippetSupport.enabledFormData(request);
        boolean multipartFiles = multipart && formData.stream().anyMatch(HttpFormData::isFile);
        boolean binary = RequestCodeSnippetSupport.hasBinaryBody(request);
        boolean urlencoded = RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType());
        boolean raw = RequestBodyTypes.BODY_TYPE_RAW.equals(request.getBodyType());
        List<HttpHeader> headers = RequestCodeSnippetSupport.effectiveHeaders(request);
        String multipartContentType = headers.stream()
                .filter(header -> "Content-Type".equalsIgnoreCase(header.getKey()))
                .map(HttpHeader::getValue)
                .filter(value -> value != null && value.strip().toLowerCase(Locale.ROOT).startsWith("multipart/"))
                .findFirst().orElse(null);

        Set<String> imports = new LinkedHashSet<>(List.of("fmt", "io", "net/http", "os"));
        if (multipart) {
            imports.add("bytes");
            imports.add("mime/multipart");
        }
        if (multipart && multipartContentType != null) {
            imports.add("mime");
        }
        if (multipartFiles) {
            imports.add("net/textproto");
            imports.add("path/filepath");
        }
        if (raw || urlencoded || multipartFiles) {
            imports.add("strings");
        }

        StringBuilder code = new StringBuilder("package main\n\nimport (\n");
        imports.stream().sorted().forEach(name -> code.append("\t\"").append(name).append("\"\n"));
        code.append("""
                )

                func main() {
                \tif err := run(); err != nil {
                \t\tfmt.Fprintln(os.Stderr, err)
                \t\tos.Exit(1)
                \t}
                }

                func run() error {
                """);

        String body = "nil";
        if (multipart) {
            appendMultipartBody(code, formData);
            body = "body";
        } else if (binary) {
            code.append("\tbody, err := os.Open(").append(goString(request.getBody())).append(")\n")
                    .append("\tif err != nil {\n\t\treturn err\n\t}\n")
                    .append("\tdefer body.Close()\n")
                    .append("\tfileStat, err := body.Stat()\n")
                    .append("\tif err != nil {\n\t\treturn err\n\t}\n\n");
            body = "body";
        } else if (urlencoded) {
            code.append("\tbody := strings.NewReader(")
                    .append(goString(RequestCodeSnippetSupport.bodyPayload(request))).append(")\n\n");
            body = "body";
        } else if (raw) {
            Charset charset = RequestCodeSnippetSupport.bodyCharset(request);
            String payload = RequestCodeSnippetSupport.bodyPayload(request);
            code.append("\tbody := strings.NewReader(")
                    .append(StandardCharsets.UTF_8.equals(charset) ? goString(payload) : goByteString(payload, charset))
                    .append(")\n\n");
            body = "body";
        }

        code.append("\treq, err := http.NewRequest(")
                .append(goString(RequestCodeSnippetSupport.method(request))).append(", ")
                .append(goString(RequestCodeSnippetSupport.urlWithParams(request))).append(", ")
                .append(body).append(")\n")
                .append("\tif err != nil {\n\t\treturn err\n\t}\n");
        if (binary) {
            code.append("\treq.ContentLength = fileStat.Size()\n")
                    .append("\treq.GetBody = func() (io.ReadCloser, error) {\n")
                    .append("\t\treturn os.Open(").append(goString(request.getBody())).append(")\n")
                    .append("\t}\n");
        }

        for (HttpHeader header : headers) {
            if (multipart && "Content-Type".equalsIgnoreCase(header.getKey())) {
                continue;
            }
            if ("Host".equalsIgnoreCase(header.getKey())) {
                code.append("\treq.Host = ").append(goString(header.getValue())).append("\n");
            } else {
                code.append("\treq.Header.Add(").append(goString(header.getKey())).append(", ")
                        .append(goString(header.getValue())).append(")\n");
            }
        }
        if (multipart) {
            if (multipartContentType == null) {
                code.append("\treq.Header.Set(\"Content-Type\", writer.FormDataContentType())\n");
            } else {
                code.append("\tmediaType, mediaParams, err := mime.ParseMediaType(")
                        .append(goString(multipartContentType)).append(")\n")
                        .append("\tif err != nil {\n\t\treturn err\n\t}\n")
                        .append("\tmediaParams[\"boundary\"] = writer.Boundary()\n")
                        .append("\treq.Header.Set(\"Content-Type\", mime.FormatMediaType(mediaType, mediaParams))\n");
            }
        } else if ((binary || raw || urlencoded)
                && headers.stream().noneMatch(header -> "Content-Type".equalsIgnoreCase(header.getKey()))) {
            code.append("\treq.Header.Set(\"Content-Type\", ")
                    .append(goString(RequestCodeSnippetSupport.contentType(request))).append(")\n");
        }
        if (RequestCodeSnippetSupport.usesTemplatedBasicAuth(request)) {
            code.append("\treq.SetBasicAuth(").append(goString(request.getAuthUsername())).append(", ")
                    .append(goString(request.getAuthPassword())).append(")\n");
        }

        if (Boolean.FALSE.equals(request.getFollowRedirects())) {
            code.append("""

                    \tclient := &http.Client{
                    \t\tCheckRedirect: func(req *http.Request, via []*http.Request) error {
                    \t\t\treturn http.ErrUseLastResponse
                    \t\t},
                    \t}
                    """);
        } else {
            code.append("\n\tclient := &http.Client{}\n");
        }
        code.append("""
                \tresp, err := client.Do(req)
                \tif err != nil {
                \t\treturn err
                \t}
                \tdefer resp.Body.Close()

                \tfmt.Println(resp.Status)
                \t_, err = io.Copy(os.Stdout, resp.Body)
                \treturn err
                }
                """);
        if (multipartFiles) {
            appendFileHelper(code);
        }
        return code.toString();
    }

    private static void appendMultipartBody(StringBuilder code, List<HttpFormData> formData) {
        code.append("\tbody := &bytes.Buffer{}\n\twriter := multipart.NewWriter(body)\n");
        for (HttpFormData item : formData) {
            String operation = item.isFile() ? "addFilePart(writer, " : "writer.WriteField(";
            code.append("\tif err := ").append(operation)
                    .append(goString(item.getKey())).append(", ")
                    .append(goString(item.getValue()));
            if (item.isFile()) {
                code.append(", ").append(goString(FileMimeTypeUtil.detectMimeType(item.getValue())));
            }
            code.append("); err != nil {\n")
                    .append("\t\treturn err\n\t}\n");
        }
        code.append("\tif err := writer.Close(); err != nil {\n\t\treturn err\n\t}\n\n");
    }

    private static void appendFileHelper(StringBuilder code) {
        code.append("""

                func addFilePart(writer *multipart.Writer, fieldName, filePath, contentType string) error {
                \tfile, err := os.Open(filePath)
                \tif err != nil {
                \t\treturn err
                \t}
                \tdefer file.Close()

                """);
        code.append("\tquoteEscaper := strings.NewReplacer(")
                .append(goString("\\")).append(", ").append(goString("\\\\")).append(", ")
                .append(goString("\"")).append(", ").append(goString("\\\"")).append(", ")
                .append(goString("\r")).append(", ").append(goString("%0D")).append(", ")
                .append(goString("\n")).append(", ").append(goString("%0A")).append(")\n");
        code.append("""
                \theader := make(textproto.MIMEHeader)
                \theader.Set("Content-Disposition", fmt.Sprintf(`form-data; name="%s"; filename="%s"`,
                \t\tquoteEscaper.Replace(fieldName), quoteEscaper.Replace(filepath.Base(filePath))))
                \theader.Set("Content-Type", contentType)
                \tpart, err := writer.CreatePart(header)
                \tif err != nil {
                \t\treturn err
                \t}
                \t_, err = io.Copy(part, file)
                \treturn err
                }
                """);
    }

    private static String goByteString(String value, Charset charset) {
        StringBuilder result = new StringBuilder("\"");
        for (byte item : RequestCodeSnippetSupport.nullToEmpty(value).getBytes(charset)) {
            int unsigned = Byte.toUnsignedInt(item);
            if (unsigned == '"') {
                result.append("\\\"");
            } else if (unsigned == '\\') {
                result.append("\\\\");
            } else if (unsigned >= 0x20 && unsigned <= 0x7e) {
                result.append((char) unsigned);
            } else {
                result.append(String.format("\\x%02x", unsigned));
            }
        }
        return result.append('"').toString();
    }

    private static String goString(String value) {
        StringBuilder result = new StringBuilder("\"");
        String text = RequestCodeSnippetSupport.nullToEmpty(value);
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (Character.isHighSurrogate(character) && i + 1 < text.length()
                            && Character.isLowSurrogate(text.charAt(i + 1))) {
                        result.append(character).append(text.charAt(++i));
                    } else if (Character.isSurrogate(character)) {
                        result.append("\\ufffd");
                    } else if (character < 0x20 || character == 0x7f
                            || character == '\u2028' || character == '\u2029' || character == '\ufeff') {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}
