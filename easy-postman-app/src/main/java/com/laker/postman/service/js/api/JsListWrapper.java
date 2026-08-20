package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;

import lombok.Getter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.laker.postman.request.model.HttpFormData.TYPE_TEXT;

/**
 * JS 专用 List 包装类，支持 add 方法
 * 用于包装 List<HttpHeader>、List<HttpFormData>、List<HttpFormUrlencoded>
 */
public class JsListWrapper<T> {
    /**
     * -- GETTER --
     * 获取底层 List
     */
    @Getter
    private final List<T> list;
    private final ListType type;
    private List<ItemProxy> cachedProxies;

    public enum ListType {
        HEADER, FORM_DATA, URLENCODED, PARAM
    }

    public JsListWrapper(List<T> list, ListType type) {
        this.list = list;
        this.type = type;
    }

    /**
     * Postman API: pm.request.params.all()
     * 返回所有元素的列表，供 JavaScript 访问
     */
    public List<ItemProxy> all() {
        reconcileProxies(false);
        return cachedProxies;
    }

    public ItemProxy one(String key) {
        List<ItemProxy> proxies = all();
        for (int index = proxies.size() - 1; index >= 0; index--) {
            ItemProxy proxy = proxies.get(index);
            if (sameKey(proxy.key, key)) {
                return proxy;
            }
        }
        return null;
    }

    public ItemProxy idx(int index) {
        List<ItemProxy> proxies = all();
        return index >= 0 && index < proxies.size() ? proxies.get(index) : null;
    }

    public void sync() {
        if (cachedProxies == null) {
            return;
        }
        reconcileProxies(false);
        cachedProxies.forEach(ItemProxy::sync);
    }

    /**
     * JS 脚本调用：pm.request.headers.add({key: 'X-Custom', value: 'Value'})
     */
    public void add(Map<String, Object> obj) {
        if (obj == null) return;
        sync();

        Object k = ScriptRequestBodyAccessor.toJavaObject(obj.get("key"));
        Object v = ScriptRequestBodyAccessor.toJavaObject(obj.get("value"));
        Object src = ScriptRequestBodyAccessor.toJavaObject(obj.get("src"));
        if (k == null) return;

        String key = String.valueOf(k);
        String value = scalarOrFirst(v != null ? v : src);
        boolean enabled = isEnabled(obj);
        Object descriptionValue = ScriptRequestBodyAccessor.toJavaObject(obj.get("description"));
        String description = descriptionValue == null ? "" : String.valueOf(descriptionValue);
        switch (type) {
            case HEADER:
                HttpHeader header = new HttpHeader();
                header.setEnabled(enabled);
                header.setKey(key);
                header.setValue(value);
                header.setDescription(description);
                @SuppressWarnings("unchecked")
                List<HttpHeader> headerList = (List<HttpHeader>) list;
                headerList.add(header);
                break;

            case FORM_DATA:
                HttpFormData formData = new HttpFormData();
                formData.setEnabled(enabled);
                formData.setKey(key);
                formData.setValue(value);
                formData.setType("file".equalsIgnoreCase(String.valueOf(obj.get("type")))
                        ? HttpFormData.TYPE_FILE
                        : TYPE_TEXT);
                formData.setDescription(description);
                @SuppressWarnings("unchecked")
                List<HttpFormData> formDataList = (List<HttpFormData>) list;
                formDataList.add(formData);
                break;

            case URLENCODED:
                HttpFormUrlencoded urlencoded = new HttpFormUrlencoded();
                urlencoded.setEnabled(enabled);
                urlencoded.setKey(key);
                urlencoded.setValue(value);
                if (v == null) {
                    urlencoded.setValue(null);
                }
                urlencoded.setDescription(description);
                @SuppressWarnings("unchecked")
                List<HttpFormUrlencoded> urlencodedList = (List<HttpFormUrlencoded>) list;
                urlencodedList.add(urlencoded);
                break;

            case PARAM:
                HttpParam param = new HttpParam();
                param.setEnabled(enabled);
                param.setKey(key);
                param.setValue(value);
                param.setDescription(description);
                @SuppressWarnings("unchecked")
                List<HttpParam> paramList = (List<HttpParam>) list;
                if (v == null) {
                    param.setValue(null);
                }
                paramList.add(param);
                break;
        }
        reconcileProxies(true);
    }

    private static boolean isEnabled(Map<String, Object> obj) {
        Object disabled = ScriptRequestBodyAccessor.toJavaObject(obj.get("disabled"));
        if (disabled instanceof Boolean disabledFlag) {
            return !disabledFlag;
        }
        Object enabled = ScriptRequestBodyAccessor.toJavaObject(obj.get("enabled"));
        return !(enabled instanceof Boolean enabledFlag) || enabledFlag;
    }

    private static String scalarOrFirst(Object value) {
        if (value instanceof List<?> values) {
            return values.isEmpty() ? "" : String.valueOf(values.get(0));
        }
        if (value instanceof Value jsValue && jsValue.hasArrayElements()) {
            return jsValue.getArraySize() == 0 ? "" : String.valueOf(
                    ScriptRequestBodyAccessor.toJavaObject(jsValue.getArrayElement(0))
            );
        }
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * JS 脚本调用：pm.request.headers.add('Content-Type: application/json')
     * 支持 "key: value" 格式的字符串
     */
    public void add(String headerString) {
        if (headerString == null) return;

        int colonIndex = headerString.indexOf(':');
        String key = (colonIndex >= 0 ? headerString.substring(0, colonIndex) : headerString).trim();
        String value = (colonIndex >= 0 ? headerString.substring(colonIndex + 1) : "").trim();
        add(key, value);
    }

    /**
     * JS 脚本调用：pm.request.headers.add('X-Custom', 'Value')
     */
    public void add(String key, String value) {
        if (key == null || value == null) return;
        sync();
        switch (type) {
            case HEADER:
                HttpHeader header = new HttpHeader();
                header.setEnabled(true);
                header.setKey(key);
                header.setValue(value);
                @SuppressWarnings("unchecked")
                List<HttpHeader> headerList = (List<HttpHeader>) list;
                headerList.add(header);
                break;

            case FORM_DATA:
                HttpFormData formData = new HttpFormData();
                formData.setEnabled(true);
                formData.setKey(key);
                formData.setValue(value);
                formData.setType(TYPE_TEXT);
                @SuppressWarnings("unchecked")
                List<HttpFormData> formDataList = (List<HttpFormData>) list;
                formDataList.add(formData);
                break;

            case URLENCODED:
                HttpFormUrlencoded urlencoded = new HttpFormUrlencoded();
                urlencoded.setEnabled(true);
                urlencoded.setKey(key);
                urlencoded.setValue(value);
                @SuppressWarnings("unchecked")
                List<HttpFormUrlencoded> urlencodedList = (List<HttpFormUrlencoded>) list;
                urlencodedList.add(urlencoded);
                break;

            case PARAM:
                HttpParam param = new HttpParam();
                param.setEnabled(true);
                param.setKey(key);
                param.setValue(value);
                @SuppressWarnings("unchecked")
                List<HttpParam> paramList = (List<HttpParam>) list;
                paramList.add(param);
                break;
        }
        reconcileProxies(true);
    }

    /**
     * Postman API: pm.request.headers.upsert({key: 'X-Custom', value: 'Value'})
     * 如果 key 已存在则更新，否则添加
     */
    public Boolean upsert(Map<String, Object> obj) {
        if (obj == null) return null;
        sync();

        Object k = ScriptRequestBodyAccessor.toJavaObject(obj.get("key"));
        if (k == null) return null;

        ItemProxy existing = one(String.valueOf(k));
        if (existing == null) {
            add(obj);
            return true;
        } else {
            existing.update(obj);
            existing.sync();
            reconcileProxies(true);
            return false;
        }
    }

    /**
     * Postman API: pm.request.headers.upsert('X-Custom', 'Value')
     */
    public Boolean upsert(String key, String value) {
        if (key == null || value == null) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("value", value);
        return upsert(item);
    }

    /**
     * Postman API: pm.request.headers.remove('X-Custom')
     * 删除指定 key 的项
     */
    public void remove(String key) {
        if (key == null) return;
        sync();
        switch (type) {
            case HEADER:
                @SuppressWarnings("unchecked")
                List<HttpHeader> headerList = (List<HttpHeader>) list;
                headerList.removeIf(header -> key.equalsIgnoreCase(header.getKey()));
                break;

            case FORM_DATA:
                @SuppressWarnings("unchecked")
                List<HttpFormData> formDataList = (List<HttpFormData>) list;
                formDataList.removeIf(formData -> key.equals(formData.getKey()));
                break;

            case URLENCODED:
                @SuppressWarnings("unchecked")
                List<HttpFormUrlencoded> urlencodedList = (List<HttpFormUrlencoded>) list;
                urlencodedList.removeIf(urlencoded -> key.equals(urlencoded.getKey()));
                break;

            case PARAM:
                @SuppressWarnings("unchecked")
                List<HttpParam> paramList = (List<HttpParam>) list;
                paramList.removeIf(param -> key.equals(param.getKey()));
                break;
        }
        reconcileProxies(false);
    }

    /**
     * Postman API: pm.request.headers.has('X-Custom')
     * 检查是否存在指定 key
     */
    public boolean has(String key) {
        if (key == null) return false;
        sync();
        return one(key) != null;
    }

    /**
     * Postman API: pm.request.headers.has('X-Custom', 'Value')
     * 检查是否存在同时匹配 key 和 value 的项
     */
    public boolean has(String key, Object value) {
        if (key == null) return false;
        sync();
        Object expected = ScriptRequestBodyAccessor.toJavaObject(value);
        return all().stream().anyMatch(item -> sameKey(item.key, key)
                && Objects.equals(item.value, expected));
    }

    /**
     * Postman API: pm.request.headers.get('X-Custom')
     * 获取指定 key 的值
     */
    public String get(String key) {
        if (key == null) return null;
        sync();
        ItemProxy item = one(key);
        return item == null ? null : item.value;
    }

    /**
     * Postman API: pm.request.headers.count()
     * 获取列表中元素的数量
     */
    public int count() {
        return list.size();
    }

    /**
     * Postman API: pm.request.headers.clear()
     * 清空所有元素
     */
    public void clear() {
        sync();
        list.clear();
        reconcileProxies(false);
    }

    /**
     * Postman API: pm.request.headers.each(callback)
     * 遍历所有元素，对每个元素执行回调函数
     */
    public void each(Value callback) {
        if (callback == null || !callback.canExecute()) {
            return;
        }

        List<ItemProxy> items = all();
        for (int index = 0; index < items.size(); index++) {
            callback.execute(items.get(index), index, items);
        }
    }

    /**
     * Postman API: pm.request.headers.toObject()
     * 将列表转换为 Map 对象（键值对形式）
     */
    public Map<String, Object> toObject() {
        sync();
        Map<String, Object> result = new LinkedHashMap<>();
        for (ItemProxy item : all()) {
            String key = type == ListType.HEADER && item.key != null
                    ? item.key.toLowerCase(Locale.ROOT)
                    : item.key;
            Object value = (type == ListType.PARAM || type == ListType.URLENCODED) && item.value == null
                    ? ""
                    : item.value;
            Object existing = result.get(key);
            if (!result.containsKey(key)) {
                result.put(key, value);
            } else if (existing instanceof List<?> values) {
                @SuppressWarnings("unchecked")
                List<Object> mutableValues = (List<Object>) values;
                mutableValues.add(value);
            } else {
                List<Object> values = new ArrayList<>();
                values.add(existing);
                values.add(value);
                result.put(key, values);
            }
        }
        return result;
    }

    private boolean sameKey(String left, String right) {
        return type == ListType.HEADER
                ? left != null && right != null && left.equalsIgnoreCase(right)
                : Objects.equals(left, right);
    }

    private void reconcileProxies(boolean refreshRetained) {
        if (cachedProxies == null) {
            cachedProxies = new ArrayList<>(list.size());
            for (T item : list) {
                cachedProxies.add(new ItemProxy(item, type));
            }
            return;
        }

        List<ItemProxy> reconciled = new ArrayList<>(list.size());
        for (T item : list) {
            ItemProxy retained = cachedProxies.stream()
                    .filter(proxy -> proxy.wraps(item))
                    .findFirst()
                    .orElse(null);
            if (retained == null) {
                reconciled.add(new ItemProxy(item, type));
            } else {
                if (refreshRetained) {
                    retained.refresh();
                }
                reconciled.add(retained);
            }
        }
        cachedProxies = reconciled;
    }

    /**
     * JavaScript-facing Postman property object. Postman uses {@code disabled}; {@code enabled}
     * remains available as an EasyPostman compatibility alias.
     */
    public static class ItemProxy {
        public String key;
        public String value;
        public String description;
        public String type;
        public Object src;
        public boolean disabled;
        public boolean enabled;

        private final Object item;
        private final ListType listType;
        private String syncedKey;
        private String syncedValue;
        private String syncedDescription;
        private String syncedType;
        private Object syncedSrc;
        private boolean syncedEnabled;

        ItemProxy(Object item, ListType listType) {
            this.item = item;
            this.listType = listType;
            load();
            snapshot();
        }

        void sync() {
            Boolean requestedEnabled = null;
            if (disabled != !syncedEnabled) {
                requestedEnabled = !disabled;
            } else if (enabled != syncedEnabled) {
                requestedEnabled = enabled;
            }
            switch (listType) {
                case HEADER -> syncHeader((HttpHeader) item, requestedEnabled);
                case FORM_DATA -> syncFormData((HttpFormData) item, requestedEnabled);
                case URLENCODED -> syncUrlencoded((HttpFormUrlencoded) item, requestedEnabled);
                case PARAM -> syncParam((HttpParam) item, requestedEnabled);
            }
            load();
            snapshot();
        }

        boolean wraps(Object candidate) {
            return item == candidate;
        }

        void refresh() {
            load();
            snapshot();
        }

        void update(Map<String, Object> definition) {
            if (definition.containsKey("key")) {
                Object requestedKey = ScriptRequestBodyAccessor.toJavaObject(definition.get("key"));
                key = requestedKey == null ? null : String.valueOf(requestedKey);
            }
            if (definition.containsKey("value")) {
                Object requestedValue = ScriptRequestBodyAccessor.toJavaObject(definition.get("value"));
                value = requestedValue == null ? null : String.valueOf(requestedValue);
            }
            if (definition.containsKey("description")) {
                Object requestedDescription = ScriptRequestBodyAccessor.toJavaObject(definition.get("description"));
                description = requestedDescription == null ? null : String.valueOf(requestedDescription);
            }
            if (definition.containsKey("type")) {
                Object requestedType = ScriptRequestBodyAccessor.toJavaObject(definition.get("type"));
                type = requestedType == null ? null : String.valueOf(requestedType);
            }
            if (definition.containsKey("src")) {
                src = ScriptRequestBodyAccessor.toJavaObject(definition.get("src"));
            }
            Object requestedDisabled = ScriptRequestBodyAccessor.toJavaObject(definition.get("disabled"));
            if (requestedDisabled instanceof Boolean disabledFlag) {
                disabled = disabledFlag;
            }
            Object requestedEnabled = ScriptRequestBodyAccessor.toJavaObject(definition.get("enabled"));
            if (requestedEnabled instanceof Boolean enabledFlag) {
                enabled = enabledFlag;
            }
        }

        /**
         * Keep {@code JSON.stringify(list.all())} aligned with Postman SDK property objects.
         */
        public Object toJSON() {
            sync();
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("key", key);
            if (type != null) {
                json.put("type", type);
            }
            if (src != null) {
                json.put("src", src);
            } else {
                json.put("value", value);
            }
            if (description != null && !description.isBlank()) {
                json.put("description", description);
            }
            if (disabled) {
                json.put("disabled", true);
            }
            return ProxyObject.fromMap(json);
        }

        public Object toJSON(Object ignoredKey) {
            return toJSON();
        }

        private void load() {
            switch (listType) {
                case HEADER -> loadHeader((HttpHeader) item);
                case FORM_DATA -> loadFormData((HttpFormData) item);
                case URLENCODED -> loadUrlencoded((HttpFormUrlencoded) item);
                case PARAM -> loadParam((HttpParam) item);
            }
            disabled = !enabled;
        }

        private void loadHeader(HttpHeader header) {
            key = header.getKey();
            value = header.getValue();
            description = header.getDescription();
            enabled = header.isEnabled();
            type = null;
            src = null;
        }

        private void loadFormData(HttpFormData formData) {
            key = formData.getKey();
            description = formData.getDescription();
            enabled = formData.isEnabled();
            type = formData.isFile() ? "file" : "text";
            value = formData.isFile() ? null : formData.getValue();
            if (formData.isFile()) {
                List<String> sources = new ArrayList<>(1);
                sources.add(formData.getValue());
                src = sources;
            } else {
                src = null;
            }
        }

        private void loadUrlencoded(HttpFormUrlencoded urlencoded) {
            key = urlencoded.getKey();
            value = urlencoded.getValue();
            description = urlencoded.getDescription();
            enabled = urlencoded.isEnabled();
            type = null;
            src = null;
        }

        private void loadParam(HttpParam param) {
            key = param.getKey();
            value = param.getValue();
            description = param.getDescription();
            enabled = param.isEnabled();
            type = null;
            src = null;
        }

        private void syncHeader(HttpHeader header, Boolean requestedEnabled) {
            changed(key, syncedKey, header::setKey);
            changed(value, syncedValue, header::setValue);
            changed(description, syncedDescription, header::setDescription);
            if (requestedEnabled != null) {
                header.setEnabled(requestedEnabled);
            }
        }

        private void syncFormData(HttpFormData formData, Boolean requestedEnabled) {
            changed(key, syncedKey, formData::setKey);
            changed(description, syncedDescription, formData::setDescription);
            changed(type, syncedType, formData::setType);
            if (formData.isFile()) {
                if (!Objects.equals(src, syncedSrc)) {
                    formData.setValue(scalarOrFirst(src));
                }
            } else {
                changed(value, syncedValue, formData::setValue);
            }
            if (requestedEnabled != null) {
                formData.setEnabled(requestedEnabled);
            }
        }

        private void syncUrlencoded(HttpFormUrlencoded urlencoded, Boolean requestedEnabled) {
            changed(key, syncedKey, urlencoded::setKey);
            changed(value, syncedValue, urlencoded::setValue);
            changed(description, syncedDescription, urlencoded::setDescription);
            if (requestedEnabled != null) {
                urlencoded.setEnabled(requestedEnabled);
            }
        }

        private void syncParam(HttpParam param, Boolean requestedEnabled) {
            changed(key, syncedKey, param::setKey);
            changed(value, syncedValue, param::setValue);
            changed(description, syncedDescription, param::setDescription);
            if (requestedEnabled != null) {
                param.setEnabled(requestedEnabled);
            }
        }

        private void snapshot() {
            syncedKey = key;
            syncedValue = value;
            syncedDescription = description;
            syncedType = type;
            syncedSrc = src instanceof List<?> values ? new ArrayList<>(values) : src;
            syncedEnabled = enabled;
        }

        private static void changed(String current,
                                    String previous,
                                    java.util.function.Consumer<String> setter) {
            if (!Objects.equals(current, previous)) {
                setter.accept(current);
            }
        }
    }

}
