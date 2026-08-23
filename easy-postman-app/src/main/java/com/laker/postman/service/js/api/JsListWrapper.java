package com.laker.postman.service.js.api;

import lombok.Getter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JS 专用 List 包装类，支持 add 方法
 * 用于包装 List<HttpHeader>、List<HttpFormData>、List<HttpFormUrlencoded>
 */
public class JsListWrapper<T> {
    private static final Runnable NOOP_MUTATION_CALLBACK = () -> {
    };

    /**
     * -- GETTER --
     * 获取底层 List
     */
    @Getter
    private final List<T> list;
    private final PostmanPropertyListAdapter adapter;
    private final Runnable mutationCallback;
    private List<ItemProxy> cachedProxies;

    public enum ListType {
        HEADER, FORM_DATA, URLENCODED, PARAM
    }

    public JsListWrapper(List<T> list, ListType type) {
        this(list, type, NOOP_MUTATION_CALLBACK);
    }

    JsListWrapper(List<T> list, ListType type, Runnable mutationCallback) {
        this.list = list;
        this.adapter = PostmanPropertyListAdapters.forType(type);
        this.mutationCallback = mutationCallback != null ? mutationCallback : NOOP_MUTATION_CALLBACK;
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

        Object k = ScriptValueConverter.toJavaObject(obj.get("key"));
        Object v = ScriptValueConverter.toJavaObject(obj.get("value"));
        Object src = ScriptValueConverter.toJavaObject(obj.get("src"));
        if (k == null) return;

        String key = String.valueOf(k);
        String value = adapter.valueFromDefinition(v, src);
        boolean enabled = isEnabled(obj);
        Object descriptionValue = ScriptValueConverter.toJavaObject(obj.get("description"));
        String description = descriptionValue == null ? "" : String.valueOf(descriptionValue);
        Object typeValue = ScriptValueConverter.toJavaObject(obj.get("type"));
        PostmanPropertyListItemState state = new PostmanPropertyListItemState(
                key,
                value,
                description,
                typeValue == null ? null : String.valueOf(typeValue),
                src,
                enabled
        );
        addAdapted(state);
        mutationCallback.run();
        reconcileProxies(true);
    }

    private static boolean isEnabled(Map<String, Object> obj) {
        Object disabled = ScriptValueConverter.toJavaObject(obj.get("disabled"));
        if (disabled instanceof Boolean disabledFlag) {
            return !disabledFlag;
        }
        Object enabled = ScriptValueConverter.toJavaObject(obj.get("enabled"));
        return !(enabled instanceof Boolean enabledFlag) || enabledFlag;
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
        addAdapted(new PostmanPropertyListItemState(key, value, null, null, null, true));
        mutationCallback.run();
        reconcileProxies(true);
    }

    @SuppressWarnings("unchecked")
    private void addAdapted(PostmanPropertyListItemState state) {
        list.add((T) adapter.create(state));
    }

    /**
     * Postman API: pm.request.headers.upsert({key: 'X-Custom', value: 'Value'})
     * 如果 key 已存在则更新，否则添加
     */
    public Boolean upsert(Map<String, Object> obj) {
        if (obj == null) return null;
        sync();

        Object k = ScriptValueConverter.toJavaObject(obj.get("key"));
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
        int previousSize = list.size();
        list.removeIf(item -> adapter.sameKey(adapter.read(item).key(), key));
        if (list.size() != previousSize) {
            mutationCallback.run();
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
        Object expected = ScriptValueConverter.toJavaObject(value);
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
        boolean hadItems = !list.isEmpty();
        list.clear();
        if (hadItems) {
            mutationCallback.run();
        }
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
            String key = adapter.toObjectKey(item.key);
            Object value = adapter.toObjectValue(item.value);
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
        return adapter.sameKey(left, right);
    }

    private void reconcileProxies(boolean refreshRetained) {
        if (cachedProxies == null) {
            cachedProxies = new ArrayList<>(list.size());
            for (T item : list) {
                cachedProxies.add(new ItemProxy(item, adapter, mutationCallback));
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
                reconciled.add(new ItemProxy(item, adapter, mutationCallback));
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
    public static class ItemProxy implements ProxyObject {
        private static final String[] MEMBER_KEYS = {
                "key", "value", "description", "type", "src", "disabled", "enabled"
        };

        public String key;
        public String value;
        public String description;
        public String type;
        public Object src;
        public boolean disabled;
        public boolean enabled;

        private final Object item;
        private final PostmanPropertyListAdapter adapter;
        private final Runnable mutationCallback;
        private String syncedKey;
        private String syncedValue;
        private String syncedDescription;
        private String syncedType;
        private Object syncedSrc;
        private boolean syncedEnabled;

        ItemProxy(Object item, PostmanPropertyListAdapter adapter, Runnable mutationCallback) {
            this.item = item;
            this.adapter = adapter;
            this.mutationCallback = mutationCallback;
            load();
            snapshot();
        }

        void sync() {
            if (hasUnsyncedChanges()) {
                mutationCallback.run();
            }
            Boolean requestedEnabled = null;
            if (disabled != !syncedEnabled) {
                requestedEnabled = !disabled;
            } else if (enabled != syncedEnabled) {
                requestedEnabled = enabled;
            }
            adapter.write(item, state(), syncedState(), requestedEnabled);
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
            definition = adapter.normalizeUpdateDefinition(definition);
            boolean recognized = false;
            if (definition.containsKey("key")) {
                Object requestedKey = ScriptValueConverter.toJavaObject(definition.get("key"));
                key = requestedKey == null ? null : String.valueOf(requestedKey);
                recognized = true;
            }
            if (definition.containsKey("value")) {
                Object requestedValue = ScriptValueConverter.toJavaObject(definition.get("value"));
                value = requestedValue == null ? null : String.valueOf(requestedValue);
                recognized = true;
            }
            if (definition.containsKey("description")) {
                Object requestedDescription = ScriptValueConverter.toJavaObject(definition.get("description"));
                description = requestedDescription == null ? null : String.valueOf(requestedDescription);
                recognized = true;
            }
            if (definition.containsKey("type")) {
                Object requestedType = ScriptValueConverter.toJavaObject(definition.get("type"));
                type = requestedType == null ? null : String.valueOf(requestedType);
                recognized = true;
            }
            if (definition.containsKey("src")) {
                src = ScriptValueConverter.toJavaObject(definition.get("src"));
                recognized = true;
            }
            Object requestedDisabled = ScriptValueConverter.toJavaObject(definition.get("disabled"));
            if (requestedDisabled instanceof Boolean disabledFlag) {
                disabled = disabledFlag;
                recognized = true;
            }
            Object requestedEnabled = ScriptValueConverter.toJavaObject(definition.get("enabled"));
            if (requestedEnabled instanceof Boolean enabledFlag) {
                enabled = enabledFlag;
                recognized = true;
            }
            if (recognized) {
                mutationCallback.run();
            }
        }

        @Override
        public Object getMember(String member) {
            return switch (member) {
                case "key" -> key;
                case "value" -> value;
                case "description" -> description;
                case "type" -> type;
                case "src" -> src;
                case "disabled" -> disabled;
                case "enabled" -> enabled;
                case "update" -> (ProxyExecutable) arguments -> {
                    Object definition = arguments.length > 0
                            ? ScriptValueConverter.toJavaObject(arguments[0])
                            : null;
                    if (definition instanceof Map<?, ?> map) {
                        Map<String, Object> normalized = new LinkedHashMap<>();
                        map.forEach((mapKey, mapValue) -> normalized.put(String.valueOf(mapKey), mapValue));
                        update(normalized);
                    }
                    return null;
                };
                case "toJSON" -> (ProxyExecutable) arguments -> toJSON();
                default -> null;
            };
        }

        @Override
        public Object getMemberKeys() {
            return MEMBER_KEYS;
        }

        @Override
        public boolean hasMember(String member) {
            return switch (member) {
                case "key", "value", "description", "type", "src", "disabled", "enabled",
                     "update", "toJSON" -> true;
                default -> false;
            };
        }

        @Override
        public void putMember(String member, Value value) {
            Object converted = ScriptValueConverter.toJavaObject(value);
            switch (member) {
                case "key" -> key = converted == null ? null : String.valueOf(converted);
                case "value" -> this.value = converted == null ? null : String.valueOf(converted);
                case "description" -> description = converted == null ? null : String.valueOf(converted);
                case "type" -> type = converted == null ? null : String.valueOf(converted);
                case "src" -> src = converted;
                case "disabled" -> disabled = Boolean.TRUE.equals(converted);
                case "enabled" -> enabled = Boolean.TRUE.equals(converted);
                default -> {
                    return;
                }
            }
            mutationCallback.run();
        }

        @Override
        public boolean removeMember(String member) {
            switch (member) {
                case "key" -> key = null;
                case "value" -> value = null;
                case "description" -> description = null;
                case "type" -> type = null;
                case "src" -> src = null;
                case "disabled" -> disabled = false;
                case "enabled" -> enabled = true;
                default -> {
                    return false;
                }
            }
            mutationCallback.run();
            return true;
        }

        /**
         * Keep {@code JSON.stringify(list.all())} aligned with Postman SDK property objects.
         */
        public Object toJSON() {
            return ProxyObject.fromMap(toDefinition());
        }

        Map<String, Object> toDefinition() {
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
            return json;
        }

        Object unwrap() {
            sync();
            return item;
        }

        public Object toJSON(Object ignoredKey) {
            return toJSON();
        }

        private void load() {
            PostmanPropertyListItemState state = adapter.read(item);
            key = state.key();
            value = state.value();
            description = state.description();
            type = state.type();
            src = state.src();
            enabled = state.enabled();
            disabled = !enabled;
        }

        private PostmanPropertyListItemState state() {
            return new PostmanPropertyListItemState(key, value, description, type, src, enabled);
        }

        private PostmanPropertyListItemState syncedState() {
            return new PostmanPropertyListItemState(
                    syncedKey,
                    syncedValue,
                    syncedDescription,
                    syncedType,
                    syncedSrc,
                    syncedEnabled
            );
        }

        private void snapshot() {
            syncedKey = key;
            syncedValue = value;
            syncedDescription = description;
            syncedType = type;
            syncedSrc = src instanceof List<?> values ? new ArrayList<>(values) : src;
            syncedEnabled = enabled;
        }

        private boolean hasUnsyncedChanges() {
            return !Objects.equals(key, syncedKey)
                    || !Objects.equals(value, syncedValue)
                    || !Objects.equals(description, syncedDescription)
                    || !Objects.equals(type, syncedType)
                    || !Objects.equals(src, syncedSrc)
                    || disabled != !syncedEnabled
                    || enabled != syncedEnabled;
        }

    }

}
