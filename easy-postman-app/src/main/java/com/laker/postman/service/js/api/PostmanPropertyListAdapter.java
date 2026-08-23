package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Type-specific mapping between EasyPostman request rows and Postman's PropertyList item shape.
 *
 * <p>{@link JsListWrapper} owns collection behavior such as {@code add}, {@code one},
 * {@code upsert}, and proxy identity. This adapter owns the row schema for one request model,
 * keeping header, query, urlencoded, and form-data rules out of the collection algorithm.</p>
 */
interface PostmanPropertyListAdapter {
    Object create(PostmanPropertyListItemState state);

    PostmanPropertyListItemState read(Object item);

    void write(Object item,
               PostmanPropertyListItemState current,
               PostmanPropertyListItemState previous,
               Boolean requestedEnabled);

    default boolean sameKey(String left, String right) {
        return Objects.equals(left, right);
    }

    default String toObjectKey(String key) {
        return key;
    }

    default Object toObjectValue(String value) {
        return value;
    }

    default String valueFromDefinition(Object value, Object src) {
        return PostmanPropertyListAdapters.scalarOrFirst(value != null ? value : src);
    }

    /**
     * Applies the item-type defaults used by the Postman SDK's element {@code update} method.
     * Missing fields and explicitly null fields are distinct in the Collection SDK, so adapters
     * only fill properties that are absent from the definition.
     */
    default Map<String, Object> normalizeUpdateDefinition(Map<String, Object> definition) {
        return new LinkedHashMap<>(definition);
    }
}

record PostmanPropertyListItemState(String key,
                                    String value,
                                    String description,
                                    String type,
                                    Object src,
                                    boolean enabled) {
}

@UtilityClass
class PostmanPropertyListAdapters {
    private static final PostmanPropertyListAdapter HEADER = new HeaderAdapter();
    private static final PostmanPropertyListAdapter FORM_DATA = new FormDataAdapter();
    private static final PostmanPropertyListAdapter URLENCODED = new UrlencodedAdapter();
    private static final PostmanPropertyListAdapter PARAM = new ParamAdapter();

    static PostmanPropertyListAdapter forType(JsListWrapper.ListType type) {
        return switch (type) {
            case HEADER -> HEADER;
            case FORM_DATA -> FORM_DATA;
            case URLENCODED -> URLENCODED;
            case PARAM -> PARAM;
        };
    }

    static String scalarOrFirst(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        if (converted instanceof java.util.List<?> values) {
            return values.isEmpty() ? "" : stringify(values.get(0));
        }
        return stringify(converted);
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void changed(String current, String previous, Consumer<String> setter) {
        if (!Objects.equals(current, previous)) {
            setter.accept(current);
        }
    }

    private static void updateEnabled(Boolean requestedEnabled, Consumer<Boolean> setter) {
        if (requestedEnabled != null) {
            setter.accept(requestedEnabled);
        }
    }

    private static final class HeaderAdapter implements PostmanPropertyListAdapter {
        @Override
        public Object create(PostmanPropertyListItemState state) {
            HttpHeader item = new HttpHeader();
            item.setEnabled(state.enabled());
            item.setKey(state.key());
            item.setValue(state.value());
            item.setDescription(state.description());
            return item;
        }

        @Override
        public PostmanPropertyListItemState read(Object item) {
            HttpHeader header = (HttpHeader) item;
            return new PostmanPropertyListItemState(
                    header.getKey(),
                    header.getValue(),
                    header.getDescription(),
                    null,
                    null,
                    header.isEnabled()
            );
        }

        @Override
        public void write(Object item,
                          PostmanPropertyListItemState current,
                          PostmanPropertyListItemState previous,
                          Boolean requestedEnabled) {
            HttpHeader header = (HttpHeader) item;
            changed(current.key(), previous.key(), header::setKey);
            changed(current.value(), previous.value(), header::setValue);
            changed(current.description(), previous.description(), header::setDescription);
            updateEnabled(requestedEnabled, header::setEnabled);
        }

        @Override
        public boolean sameKey(String left, String right) {
            return left != null && right != null && left.equalsIgnoreCase(right);
        }

        @Override
        public String toObjectKey(String key) {
            return key == null ? null : key.toLowerCase(Locale.ROOT);
        }

        @Override
        public Map<String, Object> normalizeUpdateDefinition(Map<String, Object> definition) {
            Map<String, Object> normalized = PostmanPropertyListAdapter.super
                    .normalizeUpdateDefinition(definition);
            if (!normalized.containsKey("key")) {
                normalized.put("key", "");
            }
            if (!normalized.containsKey("value")) {
                normalized.put("value", "");
            }
            return normalized;
        }
    }

    private static final class FormDataAdapter implements PostmanPropertyListAdapter {
        @Override
        public Object create(PostmanPropertyListItemState state) {
            HttpFormData item = new HttpFormData();
            item.setEnabled(state.enabled());
            item.setKey(state.key());
            item.setValue(state.value());
            item.setType("file".equalsIgnoreCase(state.type())
                    ? HttpFormData.TYPE_FILE
                    : HttpFormData.TYPE_TEXT);
            item.setDescription(state.description());
            return item;
        }

        @Override
        public PostmanPropertyListItemState read(Object item) {
            HttpFormData formData = (HttpFormData) item;
            Object src = null;
            String value = formData.getValue();
            if (formData.isFile()) {
                ArrayList<String> sources = new ArrayList<>(1);
                sources.add(value);
                src = sources;
                value = null;
            }
            return new PostmanPropertyListItemState(
                    formData.getKey(),
                    value,
                    formData.getDescription(),
                    formData.isFile() ? "file" : "text",
                    src,
                    formData.isEnabled()
            );
        }

        @Override
        public void write(Object item,
                          PostmanPropertyListItemState current,
                          PostmanPropertyListItemState previous,
                          Boolean requestedEnabled) {
            HttpFormData formData = (HttpFormData) item;
            changed(current.key(), previous.key(), formData::setKey);
            changed(current.description(), previous.description(), formData::setDescription);
            changed(current.type(), previous.type(), formData::setType);
            if (formData.isFile()) {
                if (!Objects.equals(current.src(), previous.src())) {
                    formData.setValue(scalarOrFirst(current.src()));
                }
            } else {
                changed(current.value(), previous.value(), formData::setValue);
            }
            updateEnabled(requestedEnabled, formData::setEnabled);
        }
    }

    private static final class UrlencodedAdapter implements PostmanPropertyListAdapter {
        @Override
        public Object create(PostmanPropertyListItemState state) {
            HttpFormUrlencoded item = new HttpFormUrlencoded();
            item.setEnabled(state.enabled());
            item.setKey(state.key());
            item.setValue(state.value());
            item.setDescription(state.description());
            return item;
        }

        @Override
        public PostmanPropertyListItemState read(Object item) {
            HttpFormUrlencoded urlencoded = (HttpFormUrlencoded) item;
            return new PostmanPropertyListItemState(
                    urlencoded.getKey(),
                    urlencoded.getValue(),
                    urlencoded.getDescription(),
                    null,
                    null,
                    urlencoded.isEnabled()
            );
        }

        @Override
        public void write(Object item,
                          PostmanPropertyListItemState current,
                          PostmanPropertyListItemState previous,
                          Boolean requestedEnabled) {
            HttpFormUrlencoded urlencoded = (HttpFormUrlencoded) item;
            changed(current.key(), previous.key(), urlencoded::setKey);
            changed(current.value(), previous.value(), urlencoded::setValue);
            changed(current.description(), previous.description(), urlencoded::setDescription);
            updateEnabled(requestedEnabled, urlencoded::setEnabled);
        }

        @Override
        public Object toObjectValue(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String valueFromDefinition(Object value, Object src) {
            return value == null ? null : PostmanPropertyListAdapters.scalarOrFirst(value);
        }

        @Override
        public Map<String, Object> normalizeUpdateDefinition(Map<String, Object> definition) {
            Map<String, Object> normalized = PostmanPropertyListAdapter.super
                    .normalizeUpdateDefinition(definition);
            if (!normalized.containsKey("key")) {
                normalized.put("key", null);
            }
            if (!normalized.containsKey("value")) {
                normalized.put("value", null);
            }
            return normalized;
        }
    }

    private static final class ParamAdapter implements PostmanPropertyListAdapter {
        @Override
        public Object create(PostmanPropertyListItemState state) {
            HttpParam item = new HttpParam();
            item.setEnabled(state.enabled());
            item.setKey(state.key());
            item.setValue(state.value());
            item.setDescription(state.description());
            return item;
        }

        @Override
        public PostmanPropertyListItemState read(Object item) {
            HttpParam param = (HttpParam) item;
            return new PostmanPropertyListItemState(
                    param.getKey(),
                    param.getValue(),
                    param.getDescription(),
                    null,
                    null,
                    param.isEnabled()
            );
        }

        @Override
        public void write(Object item,
                          PostmanPropertyListItemState current,
                          PostmanPropertyListItemState previous,
                          Boolean requestedEnabled) {
            HttpParam param = (HttpParam) item;
            changed(current.key(), previous.key(), param::setKey);
            changed(current.value(), previous.value(), param::setValue);
            changed(current.description(), previous.description(), param::setDescription);
            updateEnabled(requestedEnabled, param::setEnabled);
        }

        @Override
        public Object toObjectValue(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String valueFromDefinition(Object value, Object src) {
            return value == null ? null : PostmanPropertyListAdapters.scalarOrFirst(value);
        }

        @Override
        public Map<String, Object> normalizeUpdateDefinition(Map<String, Object> definition) {
            Map<String, Object> normalized = PostmanPropertyListAdapter.super
                    .normalizeUpdateDefinition(definition);
            if (!normalized.containsKey("key")) {
                normalized.put("key", null);
            }
            if (!normalized.containsKey("value")) {
                normalized.put("value", null);
            }
            return normalized;
        }
    }
}
