package com.laker.postman.http.request;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.util.HttpUrlUtil;
import com.laker.postman.service.collections.InheritanceService;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.VariableResolver;
import com.laker.postman.util.JsonUtil;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Prepares an independent request snapshot for code generation without running scripts.
 */
@UtilityClass
public class RequestCodeSnippetPreparer {

    private static final InheritanceService INHERITANCE_SERVICE = new InheritanceService();

    public static HttpRequestItem prepare(HttpRequestItem item, boolean resolveVariables) {
        if (item == null) {
            return null;
        }

        RequestExecutionScope previousScope = RequestExecutionContext.captureCurrentScope();
        try {
            // A failed collection lookup must not expose a previous request's group variables.
            RequestExecutionContext.setCurrentScope(RequestExecutionScope.empty());
            HttpRequestItem effectiveItem = INHERITANCE_SERVICE.applyInheritance(item);
            // Inherited headers can still reference parent group rows; copy after merging.
            HttpRequestItem snapshot = JsonUtil.deepCopy(effectiveItem, HttpRequestItem.class);
            if (resolveVariables) {
                resolveVariables(snapshot);
            }
            snapshot.setUrl(HttpUrlUtil.normalizeIpv6Url(HttpUrlUtil.replacePathVariables(
                    snapshot.getUrl(), snapshot.getPathVariablesList()
            )));
            return snapshot;
        } finally {
            if (previousScope == null) {
                RequestExecutionContext.clearCurrentScope();
            } else {
                RequestExecutionContext.setCurrentScope(previousScope);
            }
        }
    }

    private static void resolveVariables(HttpRequestItem item) {
        item.setUrl(VariableResolver.resolve(item.getUrl()));
        item.setBody(VariableResolver.resolve(item.getBody()));
        item.setAuthUsername(VariableResolver.resolve(item.getAuthUsername()));
        item.setAuthPassword(VariableResolver.resolve(item.getAuthPassword()));
        item.setAuthToken(VariableResolver.resolve(item.getAuthToken()));
        item.setAuthApiKeyName(VariableResolver.resolve(item.getAuthApiKeyName()));
        item.setAuthApiKeyValue(VariableResolver.resolve(item.getAuthApiKeyValue()));

        if (item.getHeadersList() != null) {
            for (HttpHeader header : item.getHeadersList()) {
                if (header != null && header.isEnabled()) {
                    header.setKey(VariableResolver.resolve(header.getKey()));
                    header.setValue(VariableResolver.resolve(header.getValue()));
                }
            }
        }
        resolveParams(item.getParamsList());
        resolveParams(item.getPathVariablesList());
        if (item.getFormDataList() != null) {
            for (HttpFormData row : item.getFormDataList()) {
                if (row != null && row.isEnabled()) {
                    row.setKey(VariableResolver.resolve(row.getKey()));
                    row.setValue(VariableResolver.resolve(row.getValue()));
                }
            }
        }
        if (item.getUrlencodedList() != null) {
            for (HttpFormUrlencoded row : item.getUrlencodedList()) {
                if (row != null && row.isEnabled()) {
                    row.setKey(VariableResolver.resolve(row.getKey()));
                    row.setValue(VariableResolver.resolve(row.getValue()));
                }
            }
        }
    }

    private static void resolveParams(List<HttpParam> params) {
        if (params == null) {
            return;
        }
        for (HttpParam param : params) {
            if (param != null && param.isEnabled()) {
                param.setKey(VariableResolver.resolve(param.getKey()));
                param.setValue(VariableResolver.resolve(param.getValue()));
            }
        }
    }
}
