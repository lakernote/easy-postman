package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.util.HttpUrlUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Owns Postman-compatible query parsing, rendering, and raw-URL/parameter-table reconciliation.
 */
@UtilityClass
final class PostmanQueryCodec {

    String normalizeComponent(String value, boolean encodeEquals) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            if (value.startsWith("{{", index)) {
                int variableEnd = value.indexOf("}}", index + 2);
                if (variableEnd >= 0) {
                    normalized.append(value, index, variableEnd + 2);
                    index = variableEnd + 2;
                    continue;
                }
            }

            char current = value.charAt(index++);
            switch (current) {
                case '&' -> normalized.append("%26");
                case '#' -> normalized.append("%23");
                case '=' -> normalized.append(encodeEquals ? "%3D" : '=');
                default -> normalized.append(current);
            }
        }
        return normalized.toString();
    }

    String build(List<HttpParam> params) {
        StringJoiner query = new StringJoiner("&");
        for (HttpParam param : params) {
            String key = normalizeComponent(param.getKey(), true);
            String value = normalizeComponent(param.getValue(), false);
            query.add(param.getValue() == null ? key : key + "=" + value);
        }
        return query.toString();
    }

    List<HttpParam> parse(String queryString) {
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

    List<HttpParam> parseUrl(String url) {
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
        return parse(queryString);
    }

    void reconcileUrlWithParams(String url, List<HttpParam> params) {
        List<HttpParam> parsed = parseUrl(url);
        if (parsed.isEmpty()) {
            return;
        }

        List<HttpParam> existing = new ArrayList<>(params);
        boolean[] consumed = new boolean[existing.size()];
        List<HttpParam> merged = new ArrayList<>(Math.max(parsed.size(), existing.size()));
        List<String> rawQueryKeys = new ArrayList<>();
        for (HttpParam parsedParam : parsed) {
            rawQueryKeys.add(parsedParam.getKey());
            int exactMatch = findEnabledMatch(parsedParam, existing, consumed, true);
            if (exactMatch >= 0) {
                consumed[exactMatch] = true;
                merged.add(existing.get(exactMatch));
                continue;
            }

            int keyMatch = findEnabledMatch(parsedParam, existing, consumed, false);
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
            } else if (!containsEquivalentKey(rawQueryKeys, candidate.getKey())) {
                merged.add(candidate);
            }
        }
        for (PositionedParam positioned : disabledOrNullParams) {
            merged.add(Math.min(positioned.index(), merged.size()), positioned.param());
        }
        params.clear();
        params.addAll(merged);
    }

    private int findEnabledMatch(HttpParam parsed,
                                 List<HttpParam> existing,
                                 boolean[] consumed,
                                 boolean matchValue) {
        for (int index = 0; index < existing.size(); index++) {
            HttpParam candidate = existing.get(index);
            if (!consumed[index]
                    && candidate != null
                    && candidate.isEnabled()
                    && semanticallyEqual(parsed.getKey(), candidate.getKey())
                    && (!matchValue || semanticallyEqual(parsed.getValue(), candidate.getValue()))) {
                return index;
            }
        }
        return -1;
    }

    private boolean containsEquivalentKey(List<String> rawQueryKeys, String candidateKey) {
        return rawQueryKeys.stream().anyMatch(rawKey -> semanticallyEqual(rawKey, candidateKey));
    }

    private boolean semanticallyEqual(String left, String right) {
        return Objects.equals(left, right)
                || Objects.equals(HttpUrlUtil.decodeComponent(left), HttpUrlUtil.decodeComponent(right));
    }

    private record PositionedParam(int index, HttpParam param) {
    }
}
