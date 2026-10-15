package com.laker.postman.codegen.request;

import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.http.request.AppRequestHeaderDefaults;
import com.laker.postman.http.runtime.okhttp.OkHttpRequestBuilder;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestAuthTypes;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.util.HttpUrlUtil;
import com.laker.postman.util.FileMimeTypeUtil;
import lombok.experimental.UtilityClass;
import okhttp3.MediaType;
import okhttp3.FormBody;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared HTTP request semantics for the supported code snippet clients. */
@UtilityClass
public class RequestCodeSnippetSupport {
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final Pattern VARIABLE_PLACEHOLDER = Pattern.compile("\\{\\{.+?}}");

    public static List<HttpHeader> effectiveHeaders(HttpRequestItem request) {
        List<HttpHeader> headers = new ArrayList<>();
        if (request.getHeadersList() != null) {
            for (HttpHeader header : request.getHeadersList()) {
                if (header != null && header.isEnabled() && CharSequenceUtil.isNotBlank(header.getKey())) {
                    if (isGeneratedDefaultHeader(header)) {
                        continue;
                    }
                    headers.add(new HttpHeader(true, header.getKey(), nullToEmpty(header.getValue()), header.getDescription()));
                }
            }
        }
        HttpHeader authHeader = authHeader(request);
        if (authHeader != null
                && headers.stream().noneMatch(header -> authHeader.getKey().equalsIgnoreCase(header.getKey()))) {
            headers.add(authHeader);
        }
        if (headers.stream().noneMatch(header -> CONTENT_TYPE.equalsIgnoreCase(header.getKey()))) {
            String payload = bodyPayload(request);
            if (CharSequenceUtil.isNotBlank(payload)
                    || RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType())
                    || hasBinaryBody(request)) {
                headers.add(new HttpHeader(true, CONTENT_TYPE, contentType(request), ""));
            }
        }
        return headers;
    }

    private static HttpHeader authHeader(HttpRequestItem request) {
        String authType = request.getAuthType();
        if (RequestAuthTypes.AUTH_TYPE_BEARER.equals(authType) && CharSequenceUtil.isNotBlank(request.getAuthToken())) {
            return new HttpHeader(true, "Authorization", "Bearer " + request.getAuthToken(), "");
        }
        if (RequestAuthTypes.AUTH_TYPE_BASIC.equals(authType)
                && CharSequenceUtil.isNotBlank(request.getAuthUsername())
                && !usesTemplatedBasicAuth(request)) {
            String credentials = request.getAuthUsername() + ":" + nullToEmpty(request.getAuthPassword());
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return new HttpHeader(true, "Authorization", "Basic " + encoded, "");
        }
        if (RequestAuthTypes.AUTH_TYPE_API_KEY.equals(authType)
                && AuthApiKeyPlacement.HEADER == AuthApiKeyPlacement.fromConstant(request.getAuthApiKeyPlacement())
                && CharSequenceUtil.isNotBlank(request.getAuthApiKeyName())
                && CharSequenceUtil.isNotBlank(request.getAuthApiKeyValue())) {
            return new HttpHeader(true, request.getAuthApiKeyName(), request.getAuthApiKeyValue(), "");
        }
        return null;
    }

    public static boolean usesTemplatedBasicAuth(HttpRequestItem request) {
        return RequestAuthTypes.AUTH_TYPE_BASIC.equals(request.getAuthType())
                && CharSequenceUtil.isNotBlank(request.getAuthUsername())
                && (containsPlaceholder(request.getAuthUsername()) || containsPlaceholder(request.getAuthPassword()))
                && !hasExplicitAuthorization(request);
    }

    public static boolean usesDigestAuth(HttpRequestItem request) {
        return RequestAuthTypes.AUTH_TYPE_DIGEST.equals(request.getAuthType())
                && CharSequenceUtil.isNotBlank(request.getAuthUsername())
                && !hasExplicitAuthorization(request);
    }

    public static boolean hasExplicitAuthorization(HttpRequestItem request) {
        return effectiveHeadersWithoutDefaults(request).stream()
                .anyMatch(header -> "Authorization".equalsIgnoreCase(header.getKey()));
    }

    private static boolean containsPlaceholder(String value) {
        return value != null && value.contains("{{") && value.contains("}}");
    }

    public static String bodyPayload(HttpRequestItem request) {
        if (RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType())) {
            StringBuilder payload = new StringBuilder();
            for (HttpFormUrlencoded item : enabledUrlencoded(request)) {
                if (!payload.isEmpty()) {
                    payload.append("&");
                }
                payload.append(encodeFormComponent(nullToEmpty(item.getKey())))
                        .append("=")
                        .append(encodeFormComponent(nullToEmpty(item.getValue())));
            }
            return payload.toString();
        }
        if (RequestBodyTypes.BODY_TYPE_RAW.equals(request.getBodyType())) {
            return OkHttpRequestBuilder.processBodyContent(nullToEmpty(request.getBody()), contentType(request));
        }
        return "";
    }

    private static String encodeFormComponent(String value) {
        // Leave unresolved templates editable, while encoding literal text exactly like FormBody.add.
        Matcher placeholders = VARIABLE_PLACEHOLDER.matcher(value);
        StringBuilder encoded = new StringBuilder();
        int end = 0;
        while (placeholders.find()) {
            encoded.append(encodeFormLiteral(value.substring(end, placeholders.start())));
            encoded.append(placeholders.group());
            end = placeholders.end();
        }
        return encoded.append(encodeFormLiteral(value.substring(end))).toString();
    }

    private static String encodeFormLiteral(String value) {
        return new FormBody.Builder().add("", value).build().encodedValue(0);
    }

    public static Charset bodyCharset(HttpRequestItem request) {
        MediaType mediaType = MediaType.parse(contentType(request));
        return mediaType == null ? StandardCharsets.UTF_8 : mediaType.charset(StandardCharsets.UTF_8);
    }

    public static boolean hasBinaryBody(HttpRequestItem request) {
        return RequestBodyTypes.BODY_TYPE_BINARY.equals(request.getBodyType())
                && CharSequenceUtil.isNotBlank(request.getBody());
    }

    public static boolean isFormDataBody(HttpRequestItem request) {
        return RequestBodyTypes.BODY_TYPE_FORM_DATA.equals(request.getBodyType());
    }

    public static boolean hasFormDataBody(HttpRequestItem request) {
        return isFormDataBody(request) && !enabledFormData(request).isEmpty();
    }

    public static String contentType(HttpRequestItem request) {
        String existing = effectiveHeadersWithoutDefaults(request).stream()
                .filter(header -> CONTENT_TYPE.equalsIgnoreCase(header.getKey()))
                .map(HttpHeader::getValue)
                .filter(CharSequenceUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        if (RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.getBodyType())) {
            return FORM_URLENCODED_CONTENT_TYPE;
        }
        if (RequestBodyTypes.BODY_TYPE_BINARY.equals(request.getBodyType())) {
            return FileMimeTypeUtil.detectMimeType(request.getBody());
        }
        return "application/json";
    }

    private static List<HttpHeader> effectiveHeadersWithoutDefaults(HttpRequestItem request) {
        if (request.getHeadersList() == null) {
            return List.of();
        }
        return request.getHeadersList().stream()
                .filter(header -> header != null && header.isEnabled() && CharSequenceUtil.isNotBlank(header.getKey()))
                .toList();
    }

    public static List<HttpFormData> enabledFormData(HttpRequestItem request) {
        if (request.getFormDataList() == null) {
            return List.of();
        }
        return request.getFormDataList().stream()
                .filter(item -> item != null && item.isEnabled() && CharSequenceUtil.isNotBlank(item.getKey()))
                .toList();
    }

    public static List<HttpFormUrlencoded> enabledUrlencoded(HttpRequestItem request) {
        if (request.getUrlencodedList() == null) {
            return List.of();
        }
        return request.getUrlencodedList().stream()
                .filter(item -> item != null && item.isEnabled() && item.getKey() != null && !item.getKey().isEmpty())
                .toList();
    }

    public static String urlWithParams(HttpRequestItem request) {
        List<HttpParam> params = request.getParamsList() == null
                ? new ArrayList<>() : new ArrayList<>(request.getParamsList().stream().filter(Objects::nonNull).toList());
        if (RequestAuthTypes.AUTH_TYPE_API_KEY.equals(request.getAuthType())
                && AuthApiKeyPlacement.QUERY_PARAMS == AuthApiKeyPlacement.fromConstant(request.getAuthApiKeyPlacement())
                && CharSequenceUtil.isNotBlank(request.getAuthApiKeyName())
                && CharSequenceUtil.isNotBlank(request.getAuthApiKeyValue())
                && params.stream().noneMatch(param -> param != null && param.isEnabled()
                && request.getAuthApiKeyName().equals(param.getKey()))) {
            params.add(new HttpParam(true, request.getAuthApiKeyName(), request.getAuthApiKeyValue()));
        }
        return HttpUrlUtil.buildEncodedUrl(nullToEmpty(request.getUrl()), params);
    }

    private static boolean isGeneratedDefaultHeader(HttpHeader header) {
        for (HttpHeader generatedHeader : AppRequestHeaderDefaults.generatedHeaders()) {
            if (sameHeaderName(header.getKey(), generatedHeader.getKey())
                    && nullToEmpty(header.getValue()).equals(nullToEmpty(generatedHeader.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameHeaderName(String left, String right) {
        return nullToEmpty(left).trim().equalsIgnoreCase(nullToEmpty(right).trim());
    }

    public static String method(HttpRequestItem request) {
        return CharSequenceUtil.isBlank(request.getMethod()) ? "GET" : request.getMethod().trim().toUpperCase();
    }

    public static boolean methodRequiresBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
