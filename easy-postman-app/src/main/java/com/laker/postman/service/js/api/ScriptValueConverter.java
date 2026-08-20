package com.laker.postman.service.js.api;

import lombok.experimental.UtilityClass;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts values at the GraalJS boundary without coupling request/body/URL adapters to each other.
 */
@UtilityClass
final class ScriptValueConverter {

    Object toJavaObject(Object value) {
        if (!(value instanceof Value jsValue)) {
            return value;
        }
        if (jsValue.isNull()) {
            return null;
        }
        if (jsValue.isHostObject()) {
            return jsValue.asHostObject();
        }
        if (jsValue.isString()) {
            return jsValue.asString();
        }
        if (jsValue.isBoolean()) {
            return jsValue.asBoolean();
        }
        if (jsValue.isNumber()) {
            return jsValue.fitsInLong() ? jsValue.asLong() : jsValue.asDouble();
        }
        if (jsValue.hasArrayElements()) {
            List<Object> items = new ArrayList<>((int) jsValue.getArraySize());
            for (long index = 0; index < jsValue.getArraySize(); index++) {
                items.add(toJavaObject(jsValue.getArrayElement(index)));
            }
            return items;
        }
        if (jsValue.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : jsValue.getMemberKeys()) {
                result.put(key, toJavaObject(jsValue.getMember(key)));
            }
            return result;
        }
        return jsValue.toString();
    }

    Object toProxyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> proxyValues = new LinkedHashMap<>();
            map.forEach((key, item) -> proxyValues.put(String.valueOf(key), toProxyValue(item)));
            return ProxyObject.fromMap(proxyValues);
        }
        if (value instanceof List<?> list) {
            return ProxyArray.fromList(list.stream().map(ScriptValueConverter::toProxyValue).toList());
        }
        return value;
    }
}
