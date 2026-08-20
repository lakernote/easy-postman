package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Postman-compatible view of {@code pm.request.body}.
 *
 * <p>Postman exposes an SDK {@code RequestBody}, rather than the rendered body string. This
 * adapter keeps that object shape in scripts and translates mutations back to the request that
 * EasyPostman sends.</p>
 */
public class ScriptRequestBodyAccessor implements ProxyObject {
    private static final String[] BODY_MEMBER_KEYS = {
            "mode", "raw", "urlencoded", "formdata", "file", "options", "disabled"
    };

    public String mode;
    public Object raw;
    public Object urlencoded;
    public Object formdata;
    public Object file;
    public Object options;
    public Boolean disabled;

    private final PreparedRequest request;
    private final JsListWrapper<HttpFormData> sharedFormData;
    private final JsListWrapper<HttpFormUrlencoded> sharedUrlencoded;
    private final ScriptRequestMutationTracker mutationTracker;
    private PostmanRequestBodyCodec.BodyTransportSnapshot syncedTransportSnapshot;
    private boolean attached = true;

    public ScriptRequestBodyAccessor(PreparedRequest request) {
        this(request, null, null, new ScriptRequestMutationTracker());
    }

    ScriptRequestBodyAccessor(PreparedRequest request,
                              JsListWrapper<HttpFormData> sharedFormData,
                              JsListWrapper<HttpFormUrlencoded> sharedUrlencoded) {
        this(request, sharedFormData, sharedUrlencoded, new ScriptRequestMutationTracker());
    }

    ScriptRequestBodyAccessor(PreparedRequest request,
                              JsListWrapper<HttpFormData> sharedFormData,
                              JsListWrapper<HttpFormUrlencoded> sharedUrlencoded,
                              ScriptRequestMutationTracker mutationTracker) {
        this.request = request;
        this.sharedFormData = sharedFormData;
        this.sharedUrlencoded = sharedUrlencoded;
        this.mutationTracker = mutationTracker;
        loadFromRequest();
        this.syncedTransportSnapshot = transportSnapshot();
    }

    /**
     * Mirrors Postman's {@code RequestBody.update(options)}. A string selects raw mode.
     */
    public void update(Object options) {
        Object definition = ScriptValueConverter.toJavaObject(options);
        if (definition instanceof CharSequence text) {
            recordAppliedDefinition(Map.of("mode", "raw", "raw", text.toString()));
            return;
        }
        if (definition instanceof Map<?, ?> map) {
            recordAppliedDefinition(map);
        }
    }

    private void recordAppliedDefinition(Map<?, ?> definition) {
        PostmanRequestBodyCodec.BodyTransportSnapshot previous = transportSnapshot();
        if (applyDefinition(definition)) {
            commitCurrentView(!Objects.equals(transportSnapshot(), previous));
        }
    }

    /**
     * Replaces the current body with a new SDK-shaped RequestBody definition. Unlike
     * {@link #update(Object)}, a definition without a mode creates an empty RequestBody because
     * Postman's {@code Request.update({body: ...})} constructs a new RequestBody instance.
     *
     * @return {@code false} when the requested mode is not supported by EasyPostman
     */
    boolean replaceDefinition(Object definition) {
        Object converted = ScriptValueConverter.toJavaObject(definition);
        if (converted instanceof Map<?, ?> map
                && "graphql".equalsIgnoreCase(PostmanRequestBodyCodec.stringify(
                PostmanRequestBodyCodec.mapValue(map, "mode")))) {
            return false;
        }

        clearView();
        if (converted instanceof CharSequence text) {
            applyDefinition(Map.of("mode", "raw", "raw", text.toString()));
        } else if (converted instanceof Map<?, ?> map) {
            applyDefinition(map);
        }
        commitCurrentView(true);
        return true;
    }

    public boolean isEmpty() {
        return PostmanRequestBodyCodec.isEmpty(
                mode,
                raw,
                synchronizedCollection(urlencoded),
                synchronizedCollection(formdata),
                file
        );
    }

    /**
     * Matches the Postman SDK string conversion used by {@code JSON.parse(pm.request.body)} and
     * string concatenation. Form-data and file bodies stringify to an empty string in Postman.
     */
    @Override
    public String toString() {
        return PostmanRequestBodyCodec.render(mode, raw, synchronizedCollection(urlencoded));
    }

    /**
     * Supplies Postman's collection JSON shape to {@code JSON.stringify(pm.request.body)}.
     */
    public Object toJSON() {
        return ScriptValueConverter.toProxyValue(PostmanRequestBodyCodec.toJson(
                mode,
                raw,
                synchronizedCollection(urlencoded),
                synchronizedCollection(formdata),
                file,
                options,
                disabled
        ));
    }

    /**
     * JavaScript's {@code JSON.stringify} calls {@code toJSON(key)} with one argument.
     */
    public Object toJSON(Object ignoredKey) {
        return toJSON();
    }

    /**
     * Writes body fields back only when the script changed this RequestBody view. This avoids
     * overwriting deliberate mutations made through the legacy {@code pm.request.raw} escape hatch.
     */
    public boolean syncToRaw() {
        if (!attached) {
            return false;
        }
        PostmanRequestBodyCodec.BodyTransportSnapshot current = transportSnapshot();
        boolean bodyChanged = !Objects.equals(current, syncedTransportSnapshot);
        boolean mutationRequested = mutationTracker.consumeBodyWrite();
        if (!bodyChanged && !mutationRequested) {
            return false;
        }

        if (bodyChanged) {
            applyToRequest();
            ensureActiveCollectionAdapter();
        }
        syncedTransportSnapshot = transportSnapshot();
        return true;
    }

    private boolean applyDefinition(Map<?, ?> definition) {
        Object requestedMode = PostmanRequestBodyCodec.mapValue(definition, "mode");
        if (requestedMode == null) {
            return false;
        }
        if ("graphql".equalsIgnoreCase(PostmanRequestBodyCodec.stringify(requestedMode))) {
            return false;
        }

        this.mode = PostmanRequestBodyCodec.normalizeMode(PostmanRequestBodyCodec.stringify(requestedMode));
        this.raw = PostmanRequestBodyCodec.mapValue(definition, "raw");
        this.urlencoded = PostmanRequestBodyCodec.mapValue(definition, "urlencoded");
        this.formdata = PostmanRequestBodyCodec.mapValue(definition, "formdata");
        this.file = PostmanRequestBodyCodec.mapValue(definition, "file");
        this.options = PostmanRequestBodyCodec.mapValue(definition, "options");
        Object disabledValue = PostmanRequestBodyCodec.mapValue(definition, "disabled");
        this.disabled = disabledValue instanceof Boolean value ? value : null;

        if ("raw".equals(mode) && raw == null) {
            raw = "";
        } else if ("urlencoded".equals(mode) && urlencoded == null) {
            urlencoded = new ArrayList<>();
        } else if ("formdata".equals(mode) && formdata == null) {
            formdata = new ArrayList<>();
        } else if ("file".equals(mode) && file instanceof CharSequence source) {
            file = new LinkedHashMap<>(Map.of("src", source.toString()));
        }
        normalizeDefinitionCollections();
        return true;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "mode" -> mode;
            case "raw" -> raw;
            case "urlencoded" -> urlencoded;
            case "formdata" -> formdata;
            case "file" -> file;
            case "options" -> options;
            case "disabled" -> disabled;
            case "update" -> (ProxyExecutable) arguments -> {
                update(arguments.length > 0 ? arguments[0] : null);
                return null;
            };
            case "isEmpty" -> (ProxyExecutable) arguments -> isEmpty();
            case "toJSON" -> (ProxyExecutable) arguments -> toJSON();
            case "toString" -> (ProxyExecutable) arguments -> toString();
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return BODY_MEMBER_KEYS;
    }

    @Override
    public boolean hasMember(String key) {
        return switch (key) {
            case "mode", "raw", "urlencoded", "formdata", "file", "options", "disabled",
                 "update", "isEmpty", "toJSON", "toString" -> true;
            default -> false;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        PostmanRequestBodyCodec.BodyTransportSnapshot previous = transportSnapshot();
        Object converted = ScriptValueConverter.toJavaObject(value);
        switch (key) {
            case "mode" -> mode = converted == null ? null : converted.toString();
            case "raw" -> raw = converted;
            case "urlencoded" -> urlencoded = converted;
            case "formdata" -> formdata = converted;
            case "file" -> file = converted;
            case "options" -> options = converted;
            case "disabled" -> disabled = converted instanceof Boolean flag ? flag : null;
            default -> {
                return;
            }
        }
        commitCurrentView(!Objects.equals(transportSnapshot(), previous));
    }

    @Override
    public boolean removeMember(String key) {
        PostmanRequestBodyCodec.BodyTransportSnapshot previous = transportSnapshot();
        switch (key) {
            case "mode" -> mode = null;
            case "raw" -> raw = null;
            case "urlencoded" -> urlencoded = null;
            case "formdata" -> formdata = null;
            case "file" -> file = null;
            case "options" -> options = null;
            case "disabled" -> disabled = null;
            default -> {
                return false;
            }
        }
        commitCurrentView(!Objects.equals(transportSnapshot(), previous));
        return true;
    }

    /**
     * Body scalar mutations are applied immediately so writes through the SDK-shaped adapter and
     * the legacy {@code pm.request.raw} escape hatch obey normal JavaScript last-write-wins order.
     * Collection item proxies still flush through {@link #syncToRaw()}.
     */
    private void commitCurrentView(boolean bodyChanged) {
        if (!attached) {
            return;
        }
        mutationTracker.recordBodyWrite();
        if (!bodyChanged) {
            return;
        }
        applyToRequest();
        ensureActiveCollectionAdapter();
        syncedTransportSnapshot = transportSnapshot();
    }

    /**
     * Detaches this SDK view when {@code Request.update()} installs a new RequestBody. Existing
     * JavaScript references may still mutate this object, but—as in Postman—they no longer mutate
     * the request's replacement body.
     */
    void detach() {
        attached = false;
    }

    private void clearView() {
        mode = null;
        raw = null;
        urlencoded = null;
        formdata = null;
        file = null;
        options = null;
        disabled = null;
    }

    private void normalizeDefinitionCollections() {
        if (urlencoded != null) {
            urlencoded = new JsListWrapper<>(
                    PostmanRequestBodyCodec.toUrlencodedList(synchronizedCollection(urlencoded)),
                    JsListWrapper.ListType.URLENCODED,
                    mutationTracker.bodyWriteCallback()
            );
        }
        if (formdata != null) {
            formdata = new JsListWrapper<>(
                    PostmanRequestBodyCodec.toFormDataList(synchronizedCollection(formdata)),
                    JsListWrapper.ListType.FORM_DATA,
                    mutationTracker.bodyWriteCallback()
            );
        }
    }

    /**
     * Gives a directly assigned active collection the Postman PropertyList API without replacing
     * an existing adapter. Postman's RequestBody keeps collection identity when only the mode
     * changes, so the script-side collection remains the source of truth and is projected to the
     * transport request separately.
     */
    private void ensureActiveCollectionAdapter() {
        if (Boolean.TRUE.equals(disabled) || mode == null) {
            return;
        }
        switch (PostmanRequestBodyCodec.normalizeMode(mode)) {
            case "formdata" -> {
                if (!(formdata instanceof JsListWrapper<?>)) {
                    formdata = new JsListWrapper<>(
                            PostmanRequestBodyCodec.toFormDataList(synchronizedCollection(formdata)),
                            JsListWrapper.ListType.FORM_DATA,
                            mutationTracker.bodyWriteCallback()
                    );
                }
            }
            case "urlencoded" -> {
                if (!(urlencoded instanceof JsListWrapper<?>)) {
                    urlencoded = new JsListWrapper<>(
                            PostmanRequestBodyCodec.toUrlencodedList(synchronizedCollection(urlencoded)),
                            JsListWrapper.ListType.URLENCODED,
                            mutationTracker.bodyWriteCallback()
                    );
                }
            }
            default -> {
                // Scalar modes already retain their SDK values.
            }
        }
    }

    private void applyToRequest() {
        PostmanRequestBodyCodec.applyToRequest(
                request,
                mode,
                raw,
                synchronizedCollection(urlencoded),
                synchronizedCollection(formdata),
                file,
                disabled
        );
    }

    private void loadFromRequest() {
        String requestMode = PostmanRequestBodyCodec.resolveMode(request);
        this.mode = requestMode;
        this.raw = "raw".equals(requestMode) ? Objects.toString(request.body, "") : null;
        List<HttpFormData> formDataList = ensureFormDataList();
        List<HttpFormUrlencoded> urlencodedList = ensureUrlencodedList();
        this.formdata = "formdata".equals(requestMode)
                ? reuseOrCreate(sharedFormData, formDataList, JsListWrapper.ListType.FORM_DATA)
                : null;
        this.urlencoded = "urlencoded".equals(requestMode)
                ? reuseOrCreate(sharedUrlencoded, urlencodedList, JsListWrapper.ListType.URLENCODED)
                : null;
        this.file = "file".equals(requestMode)
                ? new LinkedHashMap<>(Map.of("src", Objects.toString(request.body, "")))
                : null;
        this.options = null;
        this.disabled = null;
    }

    private <T> JsListWrapper<T> reuseOrCreate(JsListWrapper<T> shared,
                                               List<T> current,
                                               JsListWrapper.ListType type) {
        return shared != null && shared.getList() == current
                ? shared
                : new JsListWrapper<>(
                        current,
                        type,
                        mutationTracker.bodyWriteCallback()
                );
    }

    private List<HttpFormData> ensureFormDataList() {
        if (request.formDataList == null) {
            request.formDataList = new ArrayList<>();
        }
        return request.formDataList;
    }

    private List<HttpFormUrlencoded> ensureUrlencodedList() {
        if (request.urlencodedList == null) {
            request.urlencodedList = new ArrayList<>();
        }
        return request.urlencodedList;
    }

    private PostmanRequestBodyCodec.BodyTransportSnapshot transportSnapshot() {
        return PostmanRequestBodyCodec.transportSnapshot(
                mode,
                raw,
                synchronizedCollection(urlencoded),
                synchronizedCollection(formdata),
                file,
                disabled
        );
    }

    private Object synchronizedCollection(Object value) {
        if (value instanceof JsListWrapper<?> wrapper) {
            wrapper.sync();
            return wrapper.getList();
        }
        return value;
    }

    static boolean hasBody(PreparedRequest request) {
        return PostmanRequestBodyCodec.hasBody(request);
    }
}
