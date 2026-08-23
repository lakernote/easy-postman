package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.RequestBodyTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 脚本请求访问器 (pm.request)
 * <p>
 * 该类为 JavaScript 脚本提供对 HTTP 请求的访问接口，模拟 Postman 的 pm.request 对象。
 * 主要用于在前置脚本中读取或修改即将发送的请求。
 * </p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>访问和修改请求 URL、方法、头部、Body 等信息</li>
 *   <li>在前置脚本中动态修改请求参数</li>
 *   <li>支持操作 headers、formData、urlencoded 等集合</li>
 *   <li>提供 JavaScript 友好的访问接口</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 在 PreRequest Script 中
 * console.log("Request URL:", pm.request.url);
 * console.log("Request Method:", pm.request.method);
 *
 * // 添加请求头
 * pm.request.headers.add({
 *     key: "Authorization",
 *     value: "Bearer " + pm.environment.get("token")
 * });
 *
 * // 修改 raw 请求体（Collection SDK 形态的 EasyPostman 迁移扩展）
 * pm.request.body.update(JSON.stringify({userId: 123}));
 *
 * // 添加表单数据
 * pm.request.body.formdata.add({
 *     key: "username",
 *     value: "john",
 *     type: "text"
 * });
 *
 * // 添加 URL 查询参数
 * pm.request.url.query.add({
 *     key: "timestamp",
 *     value: Date.now()
 * });
 * }</pre>
 *
 * @author laker
 * @see PreparedRequest
 */
public class ScriptRequestAccessor {

    /**
     * 原始请求对象，包含所有请求信息
     */
    public final PreparedRequest raw;

    /**
     * 请求头集合包装器，支持 JavaScript 操作
     */
    public JsListWrapper<HttpHeader> headers;

    /**
     * 表单数据（multipart/form-data）集合包装器
     */
    public JsListWrapper<HttpFormData> formData;

    /**
     * URL 编码表单数据（application/x-www-form-urlencoded）集合包装器
     */
    public JsListWrapper<HttpFormUrlencoded> urlencoded;

    /**
     * URL 查询参数集合包装器
     */
    public JsListWrapper<HttpParam> params;

    /**
     * 请求唯一标识
     */
    public String id;

    /**
     * 请求 URL 对象（支持 pm.request.url.query.all() 访问）
     */
    public UrlWrapper url;

    /**
     * 请求方法 (GET, POST, PUT, DELETE 等)
     */
    public String method;

    /**
     * Postman RequestBody 对象。声明为 Object 是为了保留 Collection SDK body definition
     * 形态，并支持 EasyPostman 的迁移扩展：整个 body definition 或字符串赋值。
     */
    public Object body;

    /**
     * 是否为 multipart 请求
     */
    public boolean isMultipart;

    /**
     * 是否跟随重定向
     */
    public boolean followRedirects;

    private final ScriptRequestMutationTracker mutationTracker = new ScriptRequestMutationTracker();
    private RequestSnapshot syncedSnapshot;
    private ScriptRequestBodyAccessor bodyAccessor;

    /**
     * 构造脚本请求访问器
     * <p>
     * 会确保所有集合字段非空，并创建可在 JavaScript 中操作的包装器。
     * 前置脚本对这些包装器的修改会直接反映到原始请求对象中。
     * </p>
     *
     * @param req 准备好的请求对象
     */
    public ScriptRequestAccessor(PreparedRequest req) {
        this.raw = req;
        ensureRequestCollections();
        reloadViewFromRaw();
    }

    private void ensureRequestCollections() {
        if (raw.headersList == null) {
            raw.headersList = new ArrayList<>();
        }
        if (raw.formDataList == null) {
            raw.formDataList = new ArrayList<>();
        }
        if (raw.urlencodedList == null) {
            raw.urlencodedList = new ArrayList<>();
        }
        if (raw.paramsList == null) {
            raw.paramsList = new ArrayList<>();
        }
    }

    private void reloadViewFromRaw() {
        Runnable bodyMutation = mutationTracker.bodyWriteCallback();
        this.headers = new JsListWrapper<>(raw.headersList, JsListWrapper.ListType.HEADER);
        this.formData = new JsListWrapper<>(raw.formDataList, JsListWrapper.ListType.FORM_DATA, bodyMutation);
        this.urlencoded = new JsListWrapper<>(raw.urlencodedList, JsListWrapper.ListType.URLENCODED, bodyMutation);
        this.id = raw.id;
        this.url = new UrlWrapper(raw.url, raw.paramsList);
        this.params = this.url.query.asListWrapper();
        this.method = raw.method;
        this.bodyAccessor = ScriptRequestBodyAccessor.hasBody(raw)
                ? new ScriptRequestBodyAccessor(raw, formData, urlencoded, mutationTracker)
                : null;
        this.body = bodyAccessor;
        this.isMultipart = raw.isMultipart;
        this.followRedirects = raw.followRedirects;
        this.syncedSnapshot = snapshot();
    }

    /**
     * Mirrors the Postman Collection SDK {@code Request.update(options)} method for the request
     * properties that EasyPostman's HTTP runtime can send.
     */
    public void update(Object options) {
        Object converted = ScriptValueConverter.toJavaObject(options);
        if (!(converted instanceof Map<?, ?> definition)) {
            return;
        }

        if (definition.containsKey("url")) {
            UrlWrapper.ResolvedUrl resolvedUrl = UrlWrapper.resolveDefinition(definition.get("url"));
            raw.url = resolvedUrl.url();
            raw.paramsList.clear();
            raw.paramsList.addAll(resolvedUrl.params());
            this.url = new UrlWrapper(raw.url, raw.paramsList);
            this.params = this.url.query.asListWrapper();
        }
        if (definition.containsKey("method")) {
            Object requestedMethod = definition.get("method");
            this.method = requestedMethod == null ? "GET" : requestedMethod.toString().toUpperCase(Locale.ROOT);
        }
        if (definition.containsKey("header") && isPostmanTruthy(definition.get("header"))) {
            replaceHeaders(definition.get("header"));
        }
        if (definition.containsKey("body")) {
            replaceBody(definition.get("body"));
        }
    }

    /**
     * 将 JavaScript 对公共字段的修改同步回真正用于发送的请求。
     * <p>
     * 集合方法直接操作底层 List，集合元素代理、标量字段和 URL 查询参数代理
     * 则在前置脚本结束后写回。同步时只覆盖脚本确实修改过的字段，
     * 避免覆盖通过 {@code pm.request.raw} 或 {@code request} 直接完成的修改。
     * </p>
     */
    public boolean syncToRaw() {
        headers.sync();
        formData.sync();
        urlencoded.sync();
        if (!Objects.equals(method, syncedSnapshot.method())) {
            raw.method = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        }
        boolean bodyMutated = syncBody();
        bodyMutated |= mutationTracker.consumeBodyWrite();
        if (followRedirects != syncedSnapshot.followRedirects()) {
            raw.followRedirects = followRedirects;
        }

        syncUrl();

        boolean rawMultipartChangedDirectly = raw.isMultipart != syncedSnapshot.isMultipart();
        if (isMultipart != syncedSnapshot.isMultipart()) {
            raw.isMultipart = isMultipart;
        } else if (!rawMultipartChangedDirectly) {
            raw.isMultipart = hasEnabledFormData();
        }

        reloadViewFromRaw();
        return bodyMutated;
    }

    private boolean hasEnabledFormData() {
        return raw.formDataList != null && raw.formDataList.stream()
                .anyMatch(item -> item != null && item.isEnabled() && (item.isText() || item.isFile()));
    }

    private void syncUrl() {
        if (url == null || url.query == null) {
            return;
        }
        url.query.sync();
        String currentUrl = url.toString();
        if (!Objects.equals(currentUrl, syncedSnapshot.url())) {
            raw.url = currentUrl;
        } else if (!Objects.equals(raw.url, syncedSnapshot.rawUrl())) {
            url = new UrlWrapper(raw.url, raw.paramsList);
            params = url.query.asListWrapper();
        }
    }

    private void replaceHeaders(Object headerDefinition) {
        List<HttpHeader> replacements = new ArrayList<>();
        JsListWrapper<HttpHeader> replacementHeaders = new JsListWrapper<>(
                replacements,
                JsListWrapper.ListType.HEADER
        );
        Object converted = ScriptValueConverter.toJavaObject(headerDefinition);
        if (converted instanceof CharSequence headerLines) {
            for (String line : headerLines.toString().split("\\R")) {
                if (!line.isBlank()) {
                    replacementHeaders.add(line);
                }
            }
        } else if (converted instanceof Collection<?> collection) {
            for (Object item : collection) {
                addHeaderDefinition(replacementHeaders, item);
            }
        } else if (converted instanceof Map<?, ?> map) {
            // Postman's PropertyList.populate treats every top-level plain object as a key/value
            // map, even when the object itself contains properties named "key" and "value".
            map.forEach((key, value) -> addHeaderMapEntry(replacementHeaders, key, value));
        } else {
            addHeaderDefinition(replacementHeaders, converted);
        }

        // Convert first so aliases such as pm.request.raw.headersList and
        // pm.request.headers.all() stay readable until every replacement has been captured.
        raw.headersList.clear();
        raw.headersList.addAll(replacements);
    }

    private void addHeaderDefinition(JsListWrapper<HttpHeader> target, Object definition) {
        Object converted = ScriptValueConverter.toJavaObject(definition);
        if (converted instanceof JsListWrapper.ItemProxy proxy) {
            Object item = proxy.unwrap();
            if (item instanceof HttpHeader header) {
                target.getList().add(header);
            } else {
                target.add(proxy.toDefinition());
            }
            return;
        }
        if (converted instanceof HttpHeader header) {
            target.getList().add(header);
            return;
        }
        if (converted instanceof CharSequence headerLine) {
            target.add(headerLine.toString());
            return;
        }
        if (converted instanceof Map<?, ?> map) {
            Map<String, Object> header = new LinkedHashMap<>();
            map.forEach((key, value) -> header.put(String.valueOf(key), value));
            target.add(header);
        }
    }

    private void addHeaderMapEntry(JsListWrapper<HttpHeader> target, Object key, Object value) {
        Object convertedValue = ScriptValueConverter.toJavaObject(value);
        if (convertedValue == null
                || (convertedValue instanceof Double number && number.isNaN())
                || (convertedValue instanceof Float number && number.isNaN())) {
            return;
        }
        if (convertedValue instanceof CharSequence text) {
            target.add(String.valueOf(key), text.toString());
            return;
        }
        if (convertedValue instanceof Map<?, ?> definition) {
            Map<String, Object> header = new LinkedHashMap<>();
            definition.forEach((itemKey, itemValue) -> header.put(String.valueOf(itemKey), itemValue));
            target.add(header);
            return;
        }

        // Header.create(value, key) only uses the map key when value is a string. Other scalar
        // values construct an empty Header definition in postman-collection.
        target.add("", "");
    }

    private void replaceBody(Object definition) {
        ScriptRequestBodyAccessor replacement = new ScriptRequestBodyAccessor(
                raw,
                null,
                null,
                mutationTracker
        );
        if (!replacement.replaceDefinition(definition)) {
            return;
        }

        if (bodyAccessor != null) {
            bodyAccessor.detach();
        }
        Runnable bodyMutation = mutationTracker.bodyWriteCallback();
        formData = new JsListWrapper<>(raw.formDataList, JsListWrapper.ListType.FORM_DATA, bodyMutation);
        urlencoded = new JsListWrapper<>(raw.urlencodedList, JsListWrapper.ListType.URLENCODED, bodyMutation);
        bodyAccessor = replacement;
        body = bodyAccessor;
    }

    private static boolean isPostmanTruthy(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        if (converted == null || Boolean.FALSE.equals(converted)) {
            return false;
        }
        if (converted instanceof Number number) {
            double numericValue = number.doubleValue();
            return numericValue != 0 && !Double.isNaN(numericValue);
        }
        return !(converted instanceof CharSequence text) || !text.isEmpty();
    }

    private boolean syncBody() {
        if (body == bodyAccessor) {
            return bodyAccessor != null && bodyAccessor.syncToRaw();
        }

        mutationTracker.recordBodyWrite();
        if (body == null) {
            raw.body = null;
            raw.bodyType = RequestBodyTypes.BODY_TYPE_NONE;
            raw.formDataList = new ArrayList<>();
            raw.urlencodedList = new ArrayList<>();
            raw.isMultipart = false;
            return true;
        }

        ScriptRequestBodyAccessor replacement = new ScriptRequestBodyAccessor(
                raw,
                null,
                null,
                mutationTracker
        );
        replacement.update(body);
        replacement.syncToRaw();
        return true;
    }

    private RequestSnapshot snapshot() {
        return new RequestSnapshot(method, isMultipart, followRedirects, url.toString(), raw.url);
    }

    private record RequestSnapshot(String method,
                                   boolean isMultipart,
                                   boolean followRedirects,
                                   String url,
                                   String rawUrl) {
    }
}
