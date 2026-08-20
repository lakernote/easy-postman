package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.util.HttpUrlUtil;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * URL 包装器 - 用于在 JavaScript 中访问 pm.request.url
 * <p>
 * 提供对 URL 查询参数的访问，模拟 Postman 的 pm.request.url 对象。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // JavaScript 脚本中
 * var password = pm.request.url.query.all()[1].value;
 * pm.request.url.query.all()[1].value = "newValue";
 * }</pre>
 */
public class UrlWrapper {

    /**
     * URL 字符串
     */
    public final String url;

    private final String baseUrl;
    private final String fragment;

    /**
     * 查询参数包装器
     */
    public final QueryWrapper query;

    public UrlWrapper(String url, List<HttpParam> params) {
        this.url = url;
        String safeUrl = url == null ? "" : url;
        int fragmentIndex = safeUrl.indexOf('#');
        this.fragment = fragmentIndex >= 0 ? safeUrl.substring(fragmentIndex) : "";
        String withoutFragment = fragmentIndex >= 0 ? safeUrl.substring(0, fragmentIndex) : safeUrl;
        int queryIndex = withoutFragment.indexOf('?');
        this.baseUrl = queryIndex >= 0 ? withoutFragment.substring(0, queryIndex) : withoutFragment;

        List<HttpParam> target = params != null ? params : new ArrayList<>();
        mergeUrlQueryIntoParams(safeUrl, target);
        this.query = new QueryWrapper(target);
    }

    /**
     * 获取 URL 的路径部分（不包含协议、域名、端口和查询参数）
     * <p>
     * 例如: {@code "https://api.example.com:8080/users/123?id=1" -> "/users/123"}
     * </p>
     *
     * @return URL 路径，如果解析失败则返回空字符串
     */
    public String getPath() {
        if (url == null || url.isEmpty()) {
            return "";
        }

        try {
            String currentUrl = toString();
            String urlWithoutProtocol = currentUrl;
            int protocolEnd = currentUrl.indexOf("://");
            if (protocolEnd > 0) {
                urlWithoutProtocol = currentUrl.substring(protocolEnd + 3);
            }

            int pathStart = urlWithoutProtocol.indexOf('/');
            if (pathStart == -1) {
                return "/";
            }

            int queryStart = urlWithoutProtocol.indexOf('?', pathStart);
            int fragmentStart = urlWithoutProtocol.indexOf('#', pathStart);
            int pathEnd = firstNonNegative(queryStart, fragmentStart, urlWithoutProtocol.length());
            return urlWithoutProtocol.substring(pathStart, pathEnd);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取 URL 的主机名部分（不包含协议和端口）
     * <p>
     * 例如: {@code "https://api.example.com:8080/users" -> "api.example.com"}
     * </p>
     *
     * @return 主机名，如果解析失败则返回空字符串
     */
    public String getHost() {
        if (url == null || url.isEmpty()) {
            return "";
        }

        try {
            String currentUrl = toString();
            String urlWithoutProtocol = currentUrl;
            int protocolEnd = currentUrl.indexOf("://");
            if (protocolEnd > 0) {
                urlWithoutProtocol = currentUrl.substring(protocolEnd + 3);
            }

            int authorityEnd = firstNonNegative(
                    urlWithoutProtocol.indexOf('/'),
                    urlWithoutProtocol.indexOf('?'),
                    urlWithoutProtocol.indexOf('#'),
                    urlWithoutProtocol.length()
            );
            String hostPart = urlWithoutProtocol.substring(0, authorityEnd);
            int credentialsEnd = hostPart.lastIndexOf('@');
            if (credentialsEnd >= 0) {
                hostPart = hostPart.substring(credentialsEnd + 1);
            }

            if (hostPart.startsWith("[")) {
                int ipv6End = hostPart.indexOf(']');
                return ipv6End >= 0 ? hostPart.substring(0, ipv6End + 1) : hostPart;
            }
            int portStart = hostPart.lastIndexOf(':');
            return portStart > 0 ? hostPart.substring(0, portStart) : hostPart;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取查询参数字符串（不包含 '?'）
     * <p>
     * 例如: {@code "https://api.example.com/users?id=1&name=test" -> "id=1&name=test"}
     * </p>
     *
     * @return 查询参数字符串，如果没有查询参数则返回空字符串
     */
    public String getQueryString() {
        if (url == null || url.isEmpty()) {
            return "";
        }

        try {
            return buildQueryString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取路径和查询参数（从第一个 '/' 开始）
     * <p>
     * 例如: {@code "https://api.example.com/users?id=1" -> "/users?id=1"}
     * </p>
     *
     * @return 路径和查询参数，如果解析失败则返回 "/"
     */
    public String getPathWithQuery() {
        if (url == null || url.isEmpty()) {
            return "/";
        }

        try {
            String queryString = getQueryString();
            return getPath() + (!queryString.isEmpty() ? "?" + queryString : "");
        } catch (Exception e) {
            return "/";
        }
    }

    private static int firstNonNegative(int... candidates) {
        int result = Integer.MAX_VALUE;
        for (int candidate : candidates) {
            if (candidate >= 0 && candidate < result) {
                result = candidate;
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    /**
     * 获取完整的 URL 字符串
     *
     * @return URL 字符串
     */
    public String toString() {
        String queryString = buildQueryString();
        return baseUrl + (hasEnabledQueryParameter() ? "?" + queryString : "") + fragment;
    }

    private String buildQueryString() {
        StringJoiner queryString = new StringJoiner("&");
        for (HttpParam param : query.params) {
            if (param == null || !param.isEnabled()) {
                continue;
            }
            String key = ScriptRequestBodyAccessor.normalizeQueryParamComponent(param.getKey(), true);
            String value = param.getValue();
            queryString.add(value == null ? key : key + "="
                    + ScriptRequestBodyAccessor.normalizeQueryParamComponent(value, false));
        }
        return queryString.toString();
    }

    private boolean hasEnabledQueryParameter() {
        return query.params.stream().anyMatch(param -> param != null && param.isEnabled());
    }

    /**
     * Resolves the String-or-definition contract accepted by Postman's {@code Url.update}.
     * A lone {@code raw} field is accepted as a permissive collection-export fallback.
     */
    static ResolvedUrl resolveDefinition(Object definition) {
        Object converted = ScriptRequestBodyAccessor.toJavaObject(definition);
        if (converted instanceof UrlWrapper wrapper) {
            String resolved = wrapper.toString();
            return new ResolvedUrl(resolved, parseUrlQuery(resolved));
        }
        if (converted instanceof CharSequence text) {
            String resolved = text.toString();
            return new ResolvedUrl(resolved, parseUrlQuery(resolved));
        }
        if (!(converted instanceof Map<?, ?> map)) {
            String resolved = Objects.toString(converted, "");
            return new ResolvedUrl(resolved, parseUrlQuery(resolved));
        }

        if (!hasParsedUrlFields(map)) {
            String resolved = Objects.toString(javaValue(map, "raw"), "");
            return new ResolvedUrl(resolved, parseUrlQuery(resolved));
        }

        List<HttpParam> queryParams = parseQueryDefinition(javaValue(map, "query"));
        String resolved = buildUrlFromDefinition(map, queryParams);
        return new ResolvedUrl(resolved, queryParams);
    }

    private static boolean hasParsedUrlFields(Map<?, ?> definition) {
        return definition.containsKey("protocol")
                || definition.containsKey("auth")
                || definition.containsKey("host")
                || definition.containsKey("port")
                || definition.containsKey("path")
                || definition.containsKey("query")
                || definition.containsKey("hash")
                || definition.containsKey("variable");
    }

    private static String buildUrlFromDefinition(Map<?, ?> definition, List<HttpParam> queryParams) {
        StringBuilder result = new StringBuilder();
        Object protocol = javaValue(definition, "protocol");
        if (protocol != null && !protocol.toString().isEmpty()) {
            String protocolText = protocol.toString();
            result.append(protocolText);
            if (!protocolText.endsWith("://")) {
                result.append("://");
            }
        }

        appendAuth(result, javaValue(definition, "auth"));
        appendJoined(result, javaValue(definition, "host"), ".", false);

        Object port = javaValue(definition, "port");
        if (port != null) {
            result.append(':').append(port);
        }

        Object path = javaValue(definition, "path");
        if (path instanceof CharSequence text) {
            String pathText = text.toString();
            if ("/".equals(pathText)) {
                result.append('/');
            } else if (!pathText.isEmpty()) {
                result.append('/').append(pathText.startsWith("/") ? pathText.substring(1) : pathText);
            }
        } else if (path instanceof Collection<?> pathSegments) {
            result.append('/');
            appendJoined(result, pathSegments, "/", true);
        }

        List<HttpParam> enabledQueryParams = queryParams.stream()
                .filter(Objects::nonNull)
                .filter(HttpParam::isEnabled)
                .toList();
        if (!enabledQueryParams.isEmpty()) {
            result.append('?').append(buildQueryString(enabledQueryParams));
        }

        Object hash = javaValue(definition, "hash");
        if (hash instanceof CharSequence) {
            result.append('#').append(hash);
        }
        return result.toString();
    }

    private static void appendAuth(StringBuilder result, Object authDefinition) {
        if (!(authDefinition instanceof Map<?, ?> auth)) {
            return;
        }
        Object user = javaValue(auth, "user");
        Object password = javaValue(auth, "password");
        if (!(user instanceof CharSequence) && !(password instanceof CharSequence)) {
            return;
        }
        if (user instanceof CharSequence) {
            result.append(user);
        }
        if (password instanceof CharSequence) {
            result.append(':').append(password);
        }
        result.append('@');
    }

    private static void appendJoined(StringBuilder result,
                                     Object value,
                                     String separator,
                                     boolean emptyCollectionAllowed) {
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty() && !emptyCollectionAllowed) {
                return;
            }
            StringJoiner joiner = new StringJoiner(separator);
            collection.forEach(item -> joiner.add(Objects.toString(
                    ScriptRequestBodyAccessor.toJavaObject(item), "")));
            result.append(joiner);
        } else if (value != null) {
            result.append(value);
        }
    }

    private static List<HttpParam> parseQueryDefinition(Object queryDefinition) {
        Object converted = ScriptRequestBodyAccessor.toJavaObject(queryDefinition);
        if (converted instanceof CharSequence queryString) {
            return parseQueryString(queryString.toString());
        }
        if (converted instanceof Collection<?> collection) {
            List<HttpParam> result = new ArrayList<>();
            collection.forEach(item -> addQueryItem(result, item));
            return result;
        }
        if (converted instanceof Map<?, ?> queryMap) {
            List<HttpParam> result = new ArrayList<>();
            queryMap.forEach((key, value) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", Objects.toString(key, ""));
                item.put("value", ScriptRequestBodyAccessor.toJavaObject(value));
                addQueryItem(result, item);
            });
            return result;
        }
        return new ArrayList<>();
    }

    private static List<HttpParam> parseQueryString(String queryString) {
        List<HttpParam> result = new ArrayList<>();
        if (queryString.isEmpty()) {
            return result;
        }
        String[] entries = queryString.split("&", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.isEmpty() && index < entries.length - 1) {
                result.add(new HttpParam(true, null, null));
                continue;
            }
            int equalsIndex = entry.indexOf('=');
            result.add(new HttpParam(
                    true,
                    equalsIndex < 0 ? entry : entry.substring(0, equalsIndex),
                    equalsIndex < 0 ? null : entry.substring(equalsIndex + 1)
            ));
        }
        return result;
    }

    private static List<HttpParam> parseUrlQuery(String url) {
        if (url == null) {
            return new ArrayList<>();
        }
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        if (queryIndex < 0 || (fragmentIndex >= 0 && fragmentIndex < queryIndex)) {
            return new ArrayList<>();
        }
        int queryEnd = fragmentIndex >= 0 ? fragmentIndex : url.length();
        String queryString = url.substring(queryIndex + 1, queryEnd);
        if (queryString.isEmpty()) {
            return new ArrayList<>(List.of(new HttpParam(true, "", null)));
        }
        return parseQueryString(queryString);
    }

    private static void addQueryItem(List<HttpParam> target, Object definition) {
        Object converted = ScriptRequestBodyAccessor.toJavaObject(definition);
        if (converted instanceof CharSequence text) {
            target.addAll(parseQueryString(text.toString()));
            return;
        }
        if (!(converted instanceof Map<?, ?> item)) {
            return;
        }
        Object disabled = javaValue(item, "disabled");
        Object enabled = javaValue(item, "enabled");
        boolean itemEnabled;
        if (disabled instanceof Boolean disabledFlag) {
            itemEnabled = !disabledFlag;
        } else {
            itemEnabled = !(enabled instanceof Boolean enabledFlag) || enabledFlag;
        }
        Object value = javaValue(item, "value");
        target.add(new HttpParam(
                itemEnabled,
                postmanQueryString(javaValue(item, "key")),
                postmanQueryString(value),
                Objects.toString(javaValue(item, "description"), "")
        ));
    }

    private static Object javaValue(Map<?, ?> map, String key) {
        return ScriptRequestBodyAccessor.toJavaObject(map.get(key));
    }

    private static String postmanQueryString(Object value) {
        return value instanceof CharSequence text ? text.toString() : null;
    }

    private static String buildQueryString(List<HttpParam> params) {
        StringJoiner queryString = new StringJoiner("&");
        for (HttpParam param : params) {
            String key = ScriptRequestBodyAccessor.normalizeQueryParamComponent(param.getKey(), true);
            String value = ScriptRequestBodyAccessor.normalizeQueryParamComponent(param.getValue(), false);
            queryString.add(param.getValue() == null ? key : key + "=" + value);
        }
        return queryString.toString();
    }

    private static void mergeUrlQueryIntoParams(String url, List<HttpParam> params) {
        List<HttpParam> parsed = parseUrlQuery(url);
        if (parsed.isEmpty()) {
            return;
        }

        List<HttpParam> existing = new ArrayList<>(params);
        boolean[] consumed = new boolean[existing.size()];
        List<HttpParam> merged = new ArrayList<>(Math.max(parsed.size(), existing.size()));
        List<String> rawQueryKeys = new ArrayList<>();
        for (HttpParam parsedParam : parsed) {
            rawQueryKeys.add(parsedParam.getKey());
            int match = findMatchingEnabledParam(parsedParam, existing, consumed, true);
            if (match >= 0) {
                consumed[match] = true;
                merged.add(existing.get(match));
                continue;
            }

            int keyMatch = findMatchingEnabledParam(parsedParam, existing, consumed, false);
            if (keyMatch >= 0) {
                consumed[keyMatch] = true;
                parsedParam.setDescription(existing.get(keyMatch).getDescription());
            }
            merged.add(parsedParam);
        }
        List<PositionedParam> disabledOrNullParams = new ArrayList<>();
        for (int index = 0; index < existing.size(); index++) {
            HttpParam candidate = existing.get(index);
            if (consumed[index]) {
                continue;
            }
            if (candidate == null || !candidate.isEnabled()) {
                disabledOrNullParams.add(new PositionedParam(index, candidate));
                continue;
            }
            if (containsEquivalentQueryKey(rawQueryKeys, candidate.getKey())) {
                continue;
            }
            merged.add(candidate);
        }
        for (PositionedParam positioned : disabledOrNullParams) {
            merged.add(Math.min(positioned.index(), merged.size()), positioned.param());
        }
        params.clear();
        params.addAll(merged);
    }

    private static int findMatchingEnabledParam(HttpParam parsed,
                                                List<HttpParam> existing,
                                                boolean[] consumed,
                                                boolean matchValue) {
        for (int index = 0; index < existing.size(); index++) {
            HttpParam candidate = existing.get(index);
            if (!consumed[index]
                    && candidate != null
                    && candidate.isEnabled()
                    && sameQueryComponent(parsed.getKey(), candidate.getKey())
                    && (!matchValue || sameQueryComponent(parsed.getValue(), candidate.getValue()))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsEquivalentQueryKey(List<String> rawQueryKeys, String candidateKey) {
        return rawQueryKeys.stream().anyMatch(rawQueryKey -> sameQueryComponent(rawQueryKey, candidateKey));
    }

    private static boolean sameQueryComponent(String parsedValue, String existingValue) {
        return Objects.equals(parsedValue, existingValue)
                || Objects.equals(HttpUrlUtil.decodeComponent(parsedValue), HttpUrlUtil.decodeComponent(existingValue));
    }

    record ResolvedUrl(String url, List<HttpParam> params) {
    }

    private record PositionedParam(int index, HttpParam param) {
    }

    /**
     * 查询参数包装器类
     */
    public static class QueryWrapper {
        private final List<HttpParam> params;
        private final JsListWrapper<HttpParam> delegate;

        public QueryWrapper(List<HttpParam> params) {
            this.params = params != null ? params : new ArrayList<>();
            this.delegate = new JsListWrapper<>(this.params, JsListWrapper.ListType.PARAM);
        }

        /**
         * 返回所有查询参数的 JavaScript 友好包装列表
         * 注意：返回的是缓存的代理列表，对代理字段的修改会保留
         */
        public List<JsListWrapper.ItemProxy> all() {
            return delegate.all();
        }

        public void add(Map<String, Object> item) {
            delegate.add(item);
        }

        public Boolean upsert(Map<String, Object> item) {
            return delegate.upsert(item);
        }

        public void remove(String key) {
            delegate.remove(key);
        }

        public String get(String key) {
            sync();
            return delegate.get(key);
        }

        public boolean has(String key) {
            sync();
            return delegate.has(key);
        }

        public boolean has(String key, Object value) {
            sync();
            return delegate.has(key, value);
        }

        public int count() {
            return params.size();
        }

        public void clear() {
            delegate.clear();
        }

        public JsListWrapper.ItemProxy one(String key) {
            return delegate.one(key);
        }

        public JsListWrapper.ItemProxy idx(int index) {
            return delegate.idx(index);
        }

        public void each(Value callback) {
            if (callback == null || !callback.canExecute()) {
                return;
            }
            delegate.each(callback);
        }

        public Map<String, Object> toObject() {
            sync();
            return delegate.toObject();
        }

        /**
         * 同步所有代理的修改回底层 HttpParam 对象
         */
        public void sync() {
            delegate.sync();
        }

        JsListWrapper<HttpParam> asListWrapper() {
            return delegate;
        }
    }
}
