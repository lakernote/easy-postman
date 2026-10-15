package com.laker.postman.panel.collections.editor.request;

import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.codegen.request.CSharpRequestCodeGenerator;
import com.laker.postman.codegen.request.GoRequestCodeGenerator;
import com.laker.postman.http.request.RequestCodeSnippetPreparer;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.util.FileMimeTypeUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import java.util.List;

import static com.laker.postman.codegen.request.RequestCodeSnippetSupport.*;

@UtilityClass
class RequestCodeSnippetGenerator {
    static String generate(HttpRequestItem request, RequestCodeSnippetLanguage language) {
        return generate(request, language, false);
    }

    static String generate(HttpRequestItem request, RequestCodeSnippetLanguage language, boolean resolveVariables) {
        if (request == null) {
            return "";
        }
        HttpRequestItem snapshot = RequestCodeSnippetPreparer.prepare(request, resolveVariables);
        String code = switch (language) {
            case CURL -> generateCurl(snapshot);
            case JAVA_OKHTTP -> generateJavaOkHttp(snapshot);
            case JAVASCRIPT_FETCH -> generateJavaScriptFetch(snapshot);
            case PYTHON_REQUESTS -> generatePythonRequests(snapshot);
            case GO_NET_HTTP -> GoRequestCodeGenerator.generate(snapshot);
            case CSHARP_HTTP_CLIENT -> CSharpRequestCodeGenerator.generate(snapshot);
        };
        if (usesDigestAuth(snapshot)
                && (language == RequestCodeSnippetLanguage.JAVA_OKHTTP
                || language == RequestCodeSnippetLanguage.JAVASCRIPT_FETCH
                || language == RequestCodeSnippetLanguage.GO_NET_HTTP)) {
            return "// " + I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_DIGEST_UNSUPPORTED) + "\n\n" + code;
        }
        return code;
    }

    private static String generateCurl(HttpRequestItem request) {
        StringBuilder code = new StringBuilder("curl");
        String method = method(request);
        if (!"GET".equalsIgnoreCase(method)) {
            code.append(" \\\n  -X ").append(method);
        }
        String url = urlWithParams(request);
        if (CharSequenceUtil.isNotBlank(url)) {
            code.append(" \\\n  ").append(shellQuote(url));
        }
        for (HttpHeader header : effectiveHeaders(request)) {
            code.append(" \\\n  -H ").append(shellQuote(header.getKey() + ": " + nullToEmpty(header.getValue())));
        }
        if (usesDigestAuth(request)) {
            code.append(" \\\n  --digest");
        }
        if (usesDigestAuth(request) || usesTemplatedBasicAuth(request)) {
            code.append(" \\\n  --user ").append(shellQuote(request.getAuthUsername() + ":" + nullToEmpty(request.getAuthPassword())));
        }

        if (hasBinaryBody(request)) {
            code.append(" \\\n  --data-binary ").append(shellQuote("@" + request.getBody()));
        } else if (isFormDataBody(request)) {
            for (HttpFormData item : enabledFormData(request)) {
                String value = item.isFile()
                        ? item.getKey() + "=@" + nullToEmpty(item.getValue())
                        : item.getKey() + "=" + nullToEmpty(item.getValue());
                code.append(" \\\n  -F ").append(shellQuote(value));
            }
        } else if (RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType())) {
            code.append(" \\\n  --data-raw ").append(shellQuote(bodyPayload(request)));
        } else if (CharSequenceUtil.isNotBlank(bodyPayload(request))) {
            code.append(" \\\n  --data-raw ").append(shellQuote(bodyPayload(request)));
        }
        return code.toString();
    }

    private static String generateJavaOkHttp(HttpRequestItem request) {
        StringBuilder code = new StringBuilder();
        code.append("OkHttpClient client = new OkHttpClient().newBuilder()\n")
                .append("    .build();\n");

        String bodyVariable = "null";
        String bodyType = request.getBodyType();
        if (hasBinaryBody(request)) {
            code.append("MediaType mediaType = MediaType.parse(")
                    .append(javaString(contentType(request)))
                    .append(");\n")
                    .append("RequestBody body = RequestBody.create(new File(")
                    .append(javaString(request.getBody()))
                    .append("), mediaType);\n");
            bodyVariable = "body";
        } else if (RequestBodyTypes.BODY_TYPE_FORM_DATA.equals(bodyType) && !enabledFormData(request).isEmpty()) {
            code.append("RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)");
            for (HttpFormData item : enabledFormData(request)) {
                if (item.isFile()) {
                    code.append("\n    .addFormDataPart(")
                            .append(javaString(item.getKey()))
                            .append(", ")
                            .append(javaString(nullToEmpty(item.getValue())))
                            .append(", RequestBody.create(new File(")
                            .append(javaString(nullToEmpty(item.getValue())))
                            .append("), MediaType.parse(")
                            .append(javaString(FileMimeTypeUtil.detectMimeType(item.getValue())))
                            .append(")))");
                } else {
                    code.append("\n    .addFormDataPart(")
                            .append(javaString(item.getKey()))
                            .append(", ")
                            .append(javaString(nullToEmpty(item.getValue())))
                            .append(")");
                }
            }
            code.append("\n    .build();\n");
            bodyVariable = "body";
        } else {
            String payload = bodyPayload(request);
            if (CharSequenceUtil.isNotBlank(payload) || methodRequiresBody(method(request))) {
                code.append("MediaType mediaType = MediaType.parse(")
                        .append(javaString(contentType(request)))
                        .append(");\n")
                        .append("RequestBody body = RequestBody.create(mediaType, ")
                        .append(javaString(payload))
                        .append(");\n");
                bodyVariable = "body";
            }
        }

        code.append("Request request = new Request.Builder()\n")
                .append("    .url(")
                .append(javaString(urlWithParams(request)))
                .append(")\n")
                .append("    .method(")
                .append(javaString(method(request)))
                .append(", ")
                .append(bodyVariable)
                .append(")");
        for (HttpHeader header : effectiveHeaders(request)) {
            code.append("\n    .addHeader(")
                    .append(javaString(header.getKey()))
                    .append(", ")
                    .append(javaString(nullToEmpty(header.getValue())))
                    .append(")");
        }
        if (usesTemplatedBasicAuth(request)) {
            code.append("\n    .addHeader(\"Authorization\", Credentials.basic(")
                    .append(javaString(request.getAuthUsername()))
                    .append(", ")
                    .append(javaString(request.getAuthPassword()))
                    .append(", java.nio.charset.StandardCharsets.UTF_8))");
        }
        code.append("\n    .build();\n")
                .append("Response response = client.newCall(request).execute();");
        return code.toString();
    }

    private static String generateJavaScriptFetch(HttpRequestItem request) {
        StringBuilder code = new StringBuilder();
        if (hasFormDataBody(request)) {
            code.append("const formData = new FormData();\n");
            for (HttpFormData item : enabledFormData(request)) {
                if (item.isFile()) {
                    code.append("// Attach a File or Blob for ")
                            .append(item.getKey())
                            .append(" before sending.\n");
                }
                code.append("formData.append(")
                        .append(jsString(item.getKey()))
                        .append(", ")
                        .append(jsString(nullToEmpty(item.getValue())))
                        .append(");\n");
            }
            code.append("\n");
        } else if (hasBinaryBody(request)) {
            code.append("// Node.js: import { readFile } from \"node:fs/promises\";\n")
                    .append("const binaryBody = await readFile(")
                    .append(jsString(request.getBody()))
                    .append(");\n\n");
        }

        code.append("const response = await fetch(")
                .append(jsString(urlWithParams(request)))
                .append(", {\n")
                .append("  method: ")
                .append(jsString(method(request)));
        List<HttpHeader> headers = effectiveHeaders(request);
        boolean templatedBasicAuth = usesTemplatedBasicAuth(request);
        if (!headers.isEmpty() || templatedBasicAuth) {
            code.append(",\n  headers: {\n");
            for (int i = 0; i < headers.size(); i++) {
                HttpHeader header = headers.get(i);
                code.append("    ")
                        .append(jsString(header.getKey()))
                        .append(": ")
                        .append(jsString(nullToEmpty(header.getValue())));
                if (i < headers.size() - 1 || templatedBasicAuth) {
                    code.append(",");
                }
                code.append("\n");
            }
            if (templatedBasicAuth) {
                code.append("    \"Authorization\": \"Basic \" + btoa(Array.from(new TextEncoder().encode(")
                        .append(jsString(request.getAuthUsername() + ":" + nullToEmpty(request.getAuthPassword())))
                        .append("), byte => String.fromCharCode(byte)).join(\"\"))\n");
            }
            code.append("  }");
        }
        if (hasFormDataBody(request)) {
            code.append(",\n  body: formData");
        } else if (hasBinaryBody(request)) {
            code.append(",\n  body: binaryBody");
        } else if (CharSequenceUtil.isNotBlank(bodyPayload(request))) {
            code.append(",\n  body: ").append(jsString(bodyPayload(request)));
        }
        code.append("\n});\n\nconst data = await response.text();");
        return code.toString();
    }

    private static String generatePythonRequests(HttpRequestItem request) {
        StringBuilder code = new StringBuilder("import requests\n");
        if (usesDigestAuth(request)) {
            code.append("from requests.auth import HTTPDigestAuth\n");
        }
        code.append("\n");
        code.append("url = ").append(pyString(urlWithParams(request))).append("\n");
        List<HttpHeader> headers = effectiveHeaders(request);
        if (!headers.isEmpty()) {
            code.append("headers = {\n");
            for (HttpHeader header : headers) {
                code.append("    ")
                        .append(pyString(header.getKey()))
                        .append(": ")
                        .append(pyString(nullToEmpty(header.getValue())))
                        .append(",\n");
            }
            code.append("}\n");
        }

        boolean hasBinaryBody = hasBinaryBody(request);
        boolean hasFormData = hasFormDataBody(request);
        if (hasFormData) {
            List<HttpFormData> textParts = enabledFormData(request).stream().filter(HttpFormData::isText).toList();
            List<HttpFormData> fileParts = enabledFormData(request).stream().filter(HttpFormData::isFile).toList();
            if (!textParts.isEmpty()) {
                code.append("data = {\n");
                for (HttpFormData item : textParts) {
                    code.append("    ")
                            .append(pyString(item.getKey()))
                            .append(": ")
                            .append(pyString(nullToEmpty(item.getValue())))
                            .append(",\n");
                }
                code.append("}\n");
            }
            if (!fileParts.isEmpty()) {
                code.append("files = {\n");
                for (HttpFormData item : fileParts) {
                    code.append("    ")
                            .append(pyString(item.getKey()))
                            .append(": open(")
                            .append(pyString(nullToEmpty(item.getValue())))
                            .append(", \"rb\"),\n");
                }
                code.append("}\n");
            }
        } else if (hasBinaryBody) {
            code.append("payload = open(")
                    .append(pyString(request.getBody()))
                    .append(", \"rb\")\n");
        } else if (CharSequenceUtil.isNotBlank(bodyPayload(request))) {
            code.append("payload = ").append(pyString(bodyPayload(request))).append("\n");
        }

        code.append("\nresponse = requests.request(\n")
                .append("    ")
                .append(pyString(method(request)))
                .append(",\n")
                .append("    url");
        if (!headers.isEmpty()) {
            code.append(",\n    headers=headers");
        }
        if (usesDigestAuth(request)) {
            code.append(",\n    auth=HTTPDigestAuth(")
                    .append(pyString(request.getAuthUsername())).append(", ")
                    .append(pyString(request.getAuthPassword())).append(")");
        } else if (usesTemplatedBasicAuth(request)) {
            code.append(",\n    auth=(")
                    .append(pyString(request.getAuthUsername())).append(".encode('utf-8'), ")
                    .append(pyString(request.getAuthPassword())).append(".encode('utf-8'))");
        }
        if (hasFormData) {
            if (enabledFormData(request).stream().anyMatch(HttpFormData::isText)) {
                code.append(",\n    data=data");
            }
            if (enabledFormData(request).stream().anyMatch(HttpFormData::isFile)) {
                code.append(",\n    files=files");
            }
        } else if (hasBinaryBody) {
            code.append(",\n    data=payload");
        } else if (CharSequenceUtil.isNotBlank(bodyPayload(request))) {
            code.append(",\n    data=payload");
        }
        code.append("\n)\n\nprint(response.text)");
        return code.toString();
    }

    private static String shellQuote(String value) {
        return "'" + nullToEmpty(value).replace("'", "'\\''") + "'";
    }

    private static String javaString(String value) {
        return "\"" + escapeCommon(value).replace("\n", "\\n") + "\"";
    }

    private static String jsString(String value) {
        return "\"" + escapeCommon(value).replace("\n", "\\n") + "\"";
    }

    private static String pyString(String value) {
        String escaped = nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "'" + escaped + "'";
    }

    private static String escapeCommon(String value) {
        return nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}
