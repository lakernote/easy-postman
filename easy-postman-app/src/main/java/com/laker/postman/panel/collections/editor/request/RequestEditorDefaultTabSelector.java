package com.laker.postman.panel.collections.editor.request;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.panel.collections.editor.request.sub.RequestBodyPanel;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class RequestEditorDefaultTabSelector {
    private final RequestViewComponents view;

    void selectByRequestType(RequestItemProtocolEnum effectiveProtocol, HttpRequestItem item) {
        if (item == null) {
            return;
        }

        selectDefaultTab(
                effectiveProtocol,
                item.getMethod(),
                item.getBodyType(),
                item.getBody(),
                CollUtil.isNotEmpty(item.getFormDataList()) || CollUtil.isNotEmpty(item.getUrlencodedList()),
                CollUtil.isNotEmpty(item.getPathVariablesList()) || CollUtil.isNotEmpty(item.getParamsList())
        );
    }

    void selectBySavedResponse(RequestItemProtocolEnum effectiveProtocol,
                               SavedResponse.OriginalRequest originalRequest) {
        if (originalRequest == null) {
            return;
        }

        selectDefaultTab(
                effectiveProtocol,
                originalRequest.getMethod(),
                originalRequest.getBodyType(),
                originalRequest.getBody(),
                CollUtil.isNotEmpty(originalRequest.getFormDataList())
                        || CollUtil.isNotEmpty(originalRequest.getUrlencodedList()),
                CollUtil.isNotEmpty(originalRequest.getPathVariables()) || CollUtil.isNotEmpty(originalRequest.getParams())
        );
    }

    private void selectDefaultTab(RequestItemProtocolEnum effectiveProtocol,
                                  String method,
                                  String bodyType,
                                  String body,
                                  boolean hasFormData,
                                  boolean hasParams) {
        if (isBodyFirstProtocol(effectiveProtocol)) {
            RequestTabSelector.selectFirstVisible(view.reqTabs, view.requestBodyPanel, view.paramsTabPanel);
            return;
        }
        if (RequestBodyPanel.BODY_TYPE_BINARY.equals(bodyType)) {
            RequestTabSelector.selectFirstVisible(view.reqTabs, view.requestBodyPanel, view.paramsTabPanel);
            return;
        }
        if (CharSequenceUtil.isNotBlank(body) && !RequestBodyPanel.BODY_TYPE_NONE.equals(bodyType)) {
            RequestTabSelector.selectFirstVisible(view.reqTabs, view.requestBodyPanel, view.paramsTabPanel);
            return;
        }
        if (hasFormData) {
            RequestTabSelector.selectFirstVisible(view.reqTabs, view.requestBodyPanel, view.paramsTabPanel);
            return;
        }
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            if (hasParams) {
                RequestTabSelector.selectFirstVisible(view.reqTabs, view.paramsTabPanel, view.requestBodyPanel);
            } else {
                RequestTabSelector.selectFirstVisible(view.reqTabs, view.requestBodyPanel, view.paramsTabPanel);
            }
            return;
        }
        RequestTabSelector.selectFirstVisible(view.reqTabs, view.paramsTabPanel, view.requestBodyPanel);
    }

    private boolean isBodyFirstProtocol(RequestItemProtocolEnum protocol) {
        return protocol != null && protocol.isWebSocketProtocol();
    }
}
