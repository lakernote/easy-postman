package com.laker.postman.service.js;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;


import com.laker.postman.service.js.api.ResponseAssertion;
import com.laker.postman.http.runtime.transport.DefaultHttpTransport;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.request.PreparedRequestFinalizer;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行脚本里的 pm.sendRequest。
 * <p>
 * 脚本请求不属于当前 Collection 节点，因此没有继承配置；但它仍必须复用发送前收尾逻辑，
 * 确保变量替换、URL 编码和请求体处理规则与普通请求保持一致。
 */
@Slf4j
public final class ScriptSendRequestExecutor {
    // Scripts only read the body as text/JSON, so keep it in memory; FULL mode would spill large bodies to temp files
    // that nobody deletes (fatal when a performance run calls pm.sendRequest every iteration).
    static final int SCRIPT_RESPONSE_BODY_LIMIT_BYTES = 8 * 1024 * 1024;

    private final HttpTransport httpTransport;

    public ScriptSendRequestExecutor() {
        this(new DefaultHttpTransport());
    }

    public ScriptSendRequestExecutor(HttpTransport httpTransport) {
        this.httpTransport = httpTransport == null ? new DefaultHttpTransport() : httpTransport;
    }

    public void sendRequest(Object requestOptions, Value callback) throws Exception {
        if (requestOptions == null) {
            return;
        }

        PreparedRequest preparedRequest = buildPreparedRequest(requestOptions);
        validate(preparedRequest);
        PreparedRequestFinalizer.finalizeForSend(preparedRequest);
        preparedRequest.responseBodyMode = PreparedRequest.ResponseBodyMode.PREVIEW;
        preparedRequest.responseBodyPreviewLimitBytes = SCRIPT_RESPONSE_BODY_LIMIT_BYTES;

        HttpResponse httpResponse = httpTransport.execute(preparedRequest, HttpExchangeOptions.defaults());
        if (httpResponse != null && httpResponse.bodyPreviewTruncated) {
            log.warn("pm.sendRequest response body truncated to {} bytes (actual {} bytes): {}",
                    SCRIPT_RESPONSE_BODY_LIMIT_BYTES, httpResponse.bodySize, preparedRequest.url);
        }
        ResponseAssertion responseWrapper = new ResponseAssertion(httpResponse);
        executeSuccessCallback(callback, responseWrapper);
    }

    private static void validate(PreparedRequest preparedRequest) {
        if (preparedRequest.url == null || preparedRequest.url.isEmpty()) {
            throw new IllegalArgumentException("pm.sendRequest: URL is required");
        }
        if (preparedRequest.method == null || preparedRequest.method.isEmpty()) {
            throw new IllegalArgumentException("pm.sendRequest: HTTP method is required");
        }
    }

    private static void executeSuccessCallback(Value callback, ResponseAssertion responseWrapper) {
        if (callback != null && callback.canExecute()) {
            callback.execute(Value.asValue(null), responseWrapper);
        }
    }

    /**
     * 从脚本传入的字符串或对象构建一次性 PreparedRequest。
     */
    private static PreparedRequest buildPreparedRequest(Object requestOptions) {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.headersList = new ArrayList<>();
        preparedRequest.paramsList = new ArrayList<>();
        preparedRequest.method = "GET";

        if (requestOptions instanceof String urlStr) {
            preparedRequest.url = urlStr;
        } else if (requestOptions instanceof Map<?, ?> optionsMap) {
            populateFromOptionsMap(preparedRequest, optionsMap);
        }

        return preparedRequest;
    }

    private static void populateFromOptionsMap(PreparedRequest preparedRequest, Map<?, ?> optionsMap) {
        Object urlObj = optionsMap.get("url");
        if (urlObj != null) {
            String url = String.valueOf(urlObj);
            if (!url.isEmpty() && !url.equals("null") && !url.equals("undefined")) {
                preparedRequest.url = url;
            }
        }

        Object methodObj = optionsMap.get("method");
        if (methodObj != null) {
            String method = String.valueOf(methodObj);
            if (!method.isEmpty() && !method.equals("null") && !method.equals("undefined")) {
                preparedRequest.method = method.toUpperCase();
            }
        }

        populateHeaders(preparedRequest, optionsMap.get("header"));
        populateBody(preparedRequest, optionsMap.get("body"));
    }

    private static void populateHeaders(PreparedRequest preparedRequest, Object headerObj) {
        if (headerObj instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                preparedRequest.headersList.add(createEnabledHeader(
                        String.valueOf(entry.getKey()),
                        String.valueOf(entry.getValue())
                ));
            }
            return;
        }

        if (headerObj instanceof List<?> headerList) {
            for (Object item : headerList) {
                if (!(item instanceof Map<?, ?> headerItem)) {
                    continue;
                }
                Object keyObj = headerItem.get("key");
                Object valueObj = headerItem.get("value");
                if (keyObj != null && valueObj != null) {
                    preparedRequest.headersList.add(createEnabledHeader(
                            String.valueOf(keyObj),
                            String.valueOf(valueObj)
                    ));
                }
            }
        }
    }

    private static HttpHeader createEnabledHeader(String key, String value) {
        HttpHeader header = new HttpHeader();
        header.setEnabled(true);
        header.setKey(key);
        header.setValue(value);
        return header;
    }

    private static void populateBody(PreparedRequest preparedRequest, Object bodyObj) {
        if (!(bodyObj instanceof Map<?, ?> bodyMap)) {
            return;
        }

        Object modeObj = bodyMap.get("mode");
        if (modeObj == null) {
            return;
        }

        String mode = String.valueOf(modeObj);
        if ("raw".equals(mode)) {
            Object rawObj = bodyMap.get("raw");
            if (rawObj != null) {
                preparedRequest.bodyType = "raw";
                preparedRequest.body = String.valueOf(rawObj);
            }
        } else if ("formdata".equals(mode)) {
            populateFormData(preparedRequest, bodyMap.get("formdata"));
        } else if ("urlencoded".equals(mode)) {
            populateUrlencoded(preparedRequest, bodyMap.get("urlencoded"));
        }
    }

    private static void populateFormData(PreparedRequest preparedRequest, Object formDataObj) {
        if (!(formDataObj instanceof List<?> formDataList)) {
            return;
        }
        preparedRequest.bodyType = "formdata";
        preparedRequest.formDataList = new ArrayList<>();
        preparedRequest.isMultipart = true;
        for (Object item : formDataList) {
            if (!(item instanceof Map<?, ?> formDataItem)) {
                continue;
            }
            Object keyObj = formDataItem.get("key");
            Object valueObj = formDataItem.get("value");
            if (keyObj != null && valueObj != null) {
                HttpFormData formData = new HttpFormData();
                formData.setEnabled(true);
                formData.setKey(String.valueOf(keyObj));
                formData.setValue(String.valueOf(valueObj));
                formData.setType(HttpFormData.TYPE_TEXT);
                preparedRequest.formDataList.add(formData);
            }
        }
    }

    private static void populateUrlencoded(PreparedRequest preparedRequest, Object urlencodedObj) {
        if (!(urlencodedObj instanceof List<?> urlencodedList)) {
            return;
        }
        preparedRequest.bodyType = "x-www-form-urlencoded";
        preparedRequest.urlencodedList = new ArrayList<>();
        for (Object item : urlencodedList) {
            if (!(item instanceof Map<?, ?> urlencodedItem)) {
                continue;
            }
            Object keyObj = urlencodedItem.get("key");
            Object valueObj = urlencodedItem.get("value");
            if (keyObj != null && valueObj != null) {
                HttpFormUrlencoded urlencoded = new HttpFormUrlencoded();
                urlencoded.setEnabled(true);
                urlencoded.setKey(String.valueOf(keyObj));
                urlencoded.setValue(String.valueOf(valueObj));
                preparedRequest.urlencodedList.add(urlencoded);
            }
        }
    }
}
