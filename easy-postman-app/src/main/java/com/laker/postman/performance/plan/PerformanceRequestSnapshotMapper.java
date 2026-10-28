package com.laker.postman.performance.plan;

import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpRequestProxyPolicy;


import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.request.PerformanceAuthType;
import com.laker.postman.performance.core.request.PerformanceRequestExecutionScopeSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestFormDataPart;
import com.laker.postman.performance.core.request.PerformanceRequestKeyValue;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;

import com.laker.postman.service.variable.RequestExecutionScope;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class PerformanceRequestSnapshotMapper {

    public PerformanceRequestSnapshot fromHttpRequestItem(HttpRequestItem item, RequestExecutionScope scope) {
        if (item == null) {
            return null;
        }
        return PerformanceRequestSnapshot.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .url(item.getUrl())
                .method(item.getMethod())
                .protocol(toPerformanceProtocol(item))
                .headers(toKeyValues(item.getHeadersList()))
                .bodyType(item.getBodyType())
                .body(item.getBody())
                .params(toKeyValues(item.getParamsList()))
                .formData(toFormData(item.getFormDataList()))
                .urlencoded(toKeyValues(item.getUrlencodedList()))
                .authType(toPerformanceAuthType(item.getAuthType()))
                .authUsername(item.getAuthUsername())
                .authPassword(item.getAuthPassword())
                .authToken(item.getAuthToken())
                .authApiKeyName(item.getAuthApiKeyName())
                .authApiKeyValue(item.getAuthApiKeyValue())
                .authApiKeyPlacement(item.getAuthApiKeyPlacement())
                .followRedirects(item.getFollowRedirects())
                .cookieJarEnabled(item.getCookieJarEnabled())
                .proxyPolicy(item.resolveProxyPolicy().name())
                .httpVersion(item.resolveHttpVersion())
                .requestTimeoutMs(item.getRequestTimeoutMs())
                .webSocketPingIntervalMs(item.getWebSocketPingIntervalMs())
                .prescript(item.getPrescript())
                .postscript(item.getPostscript())
                .executionScope(toScopeSnapshot(scope))
                .build();
    }

    public PerformanceRequestSnapshot copyRequestSnapshot(PerformanceRequestSnapshot snapshot) {
        return snapshot == null ? null : snapshot.toBuilder().build();
    }

    public HttpRequestItem toHttpRequestItem(PerformanceRequestSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        HttpRequestItem item = new HttpRequestItem();
        item.setId(snapshot.getId());
        item.setName(snapshot.getName());
        item.setDescription(snapshot.getDescription());
        item.setUrl(snapshot.getUrl());
        item.setMethod(snapshot.getMethod());
        item.setProtocol(toRequestItemProtocol(snapshot.getProtocol()));
        item.setHeadersList(snapshot.getHeaders().stream()
                .map(value -> new HttpHeader(value.isEnabled(), value.getKey(), value.getValue(), value.getDescription()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        item.setBodyType(snapshot.getBodyType());
        item.setBody(snapshot.getBody());
        item.setParamsList(snapshot.getParams().stream()
                .map(value -> new HttpParam(value.isEnabled(), value.getKey(), value.getValue(), value.getDescription()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        item.setFormDataList(snapshot.getFormData().stream()
                .map(value -> new HttpFormData(
                        value.isEnabled(),
                        value.getKey(),
                        value.getType(),
                        value.getValue(),
                        value.getDescription()
                ))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        item.setUrlencodedList(snapshot.getUrlencoded().stream()
                .map(value -> new HttpFormUrlencoded(
                        value.isEnabled(),
                        value.getKey(),
                        value.getValue(),
                        value.getDescription()
                ))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        item.setAuthType(toRequestAuthType(snapshot.getAuthType()));
        item.setAuthUsername(snapshot.getAuthUsername());
        item.setAuthPassword(snapshot.getAuthPassword());
        item.setAuthToken(snapshot.getAuthToken());
        item.setAuthApiKeyName(snapshot.getAuthApiKeyName());
        item.setAuthApiKeyValue(snapshot.getAuthApiKeyValue());
        item.setAuthApiKeyPlacement(snapshot.getAuthApiKeyPlacement());
        item.setFollowRedirects(snapshot.getFollowRedirects());
        item.setCookieJarEnabled(snapshot.getCookieJarEnabled());
        item.setProxyPolicy(HttpRequestProxyPolicy.normalize(snapshot.getProxyPolicy()));
        item.setHttpVersion(snapshot.getHttpVersion());
        item.setRequestTimeoutMs(snapshot.getRequestTimeoutMs());
        item.setWebSocketPingIntervalMs(snapshot.getWebSocketPingIntervalMs());
        item.setPrescript(snapshot.getPrescript());
        item.setPostscript(snapshot.getPostscript());
        return item;
    }

    public RequestExecutionScope toRequestExecutionScope(PerformanceRequestSnapshot snapshot) {
        if (snapshot == null || snapshot.getExecutionScope() == null) {
            return RequestExecutionScope.empty();
        }
        return RequestExecutionScope.fromGroupVariables(snapshot.getExecutionScope().getGroupVariables());
    }

    public PerformanceRequestExecutionScopeSnapshot toScopeSnapshot(RequestExecutionScope scope) {
        if (scope == null) {
            return PerformanceRequestExecutionScopeSnapshot.empty();
        }
        return PerformanceRequestExecutionScopeSnapshot.fromGroupVariables(scope.getGroupVariables());
    }

    private PerformanceRequestProtocol toPerformanceProtocol(HttpRequestItem item) {
        RequestItemProtocolEnum protocol = item.getProtocol();
        if (protocol == RequestItemProtocolEnum.WEBSOCKET) {
            return PerformanceRequestProtocol.WEBSOCKET;
        }
        return PerformanceRequestProtocol.HTTP;
    }

    private RequestItemProtocolEnum toRequestItemProtocol(PerformanceRequestProtocol protocol) {
        if (protocol == PerformanceRequestProtocol.WEBSOCKET) {
            return RequestItemProtocolEnum.WEBSOCKET;
        }
        return RequestItemProtocolEnum.HTTP;
    }

    private PerformanceAuthType toPerformanceAuthType(String authType) {
        if (AuthType.NONE.getConstant().equals(authType)) {
            return PerformanceAuthType.NONE;
        }
        if (AuthType.API_KEY.getConstant().equals(authType)) {
            return PerformanceAuthType.API_KEY;
        }
        if (AuthType.BASIC.getConstant().equals(authType)) {
            return PerformanceAuthType.BASIC;
        }
        if (AuthType.BEARER.getConstant().equals(authType)) {
            return PerformanceAuthType.BEARER;
        }
        if (AuthType.DIGEST.getConstant().equals(authType)) {
            return PerformanceAuthType.DIGEST;
        }
        return PerformanceAuthType.INHERIT;
    }

    private String toRequestAuthType(PerformanceAuthType authType) {
        if (authType == PerformanceAuthType.NONE) {
            return AuthType.NONE.getConstant();
        }
        if (authType == PerformanceAuthType.API_KEY) {
            return AuthType.API_KEY.getConstant();
        }
        if (authType == PerformanceAuthType.BASIC) {
            return AuthType.BASIC.getConstant();
        }
        if (authType == PerformanceAuthType.BEARER) {
            return AuthType.BEARER.getConstant();
        }
        if (authType == PerformanceAuthType.DIGEST) {
            return AuthType.DIGEST.getConstant();
        }
        return AuthType.INHERIT.getConstant();
    }

    private List<PerformanceRequestKeyValue> toKeyValues(List<? extends Object> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(PerformanceRequestSnapshotMapper::toKeyValue)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private PerformanceRequestKeyValue toKeyValue(Object value) {
        if (value instanceof HttpHeader header) {
            return new PerformanceRequestKeyValue(header.isEnabled(), header.getKey(), header.getValue(), header.getDescription());
        }
        if (value instanceof HttpParam param) {
            return new PerformanceRequestKeyValue(param.isEnabled(), param.getKey(), param.getValue(), param.getDescription());
        }
        if (value instanceof HttpFormUrlencoded urlencoded) {
            return new PerformanceRequestKeyValue(
                    urlencoded.isEnabled(),
                    urlencoded.getKey(),
                    urlencoded.getValue(),
                    urlencoded.getDescription()
            );
        }
        return null;
    }

    private List<PerformanceRequestFormDataPart> toFormData(List<HttpFormData> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> new PerformanceRequestFormDataPart(
                        value.isEnabled(),
                        value.getKey(),
                        value.getType(),
                        value.getValue(),
                        value.getDescription()
                ))
                .toList();
    }
}
