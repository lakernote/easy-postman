package com.laker.postman.plugin.host;

import com.laker.postman.model.RequestImportBodyTypes;
import com.laker.postman.model.RequestImportDraft;
import com.laker.postman.model.RequestImportHeader;
import com.laker.postman.model.RequestImportProtocol;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;


import cn.hutool.core.util.IdUtil;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
class RequestImportDraftMapper {

    static HttpRequestItem toHttpRequestItem(RequestImportDraft draft) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(draft.id().isBlank() ? IdUtil.simpleUUID() : draft.id());
        item.setName(draft.name());
        item.setUrl(draft.url());
        item.setMethod(draft.method());
        item.setProtocol(toProtocol(draft.protocol()));
        item.setHeadersList(toHeaders(draft.headers()));
        item.setDescription(draft.description());
        item.setBodyType(toBodyType(draft.bodyType()));
        item.setBody(draft.body());
        return item;
    }

    private static List<HttpHeader> toHeaders(List<RequestImportHeader> headers) {
        return headers.stream()
                .map(header -> new HttpHeader(header.enabled(), header.key(), header.value()))
                .toList();
    }

    private static RequestItemProtocolEnum toProtocol(RequestImportProtocol protocol) {
        return switch (protocol) {
            case WEBSOCKET -> RequestItemProtocolEnum.WEBSOCKET;
            case HTTP -> RequestItemProtocolEnum.HTTP;
        };
    }

    private static String toBodyType(String bodyType) {
        return switch (bodyType) {
            case RequestImportBodyTypes.RAW -> RequestBodyTypes.BODY_TYPE_RAW;
            case RequestImportBodyTypes.FORM_DATA -> RequestBodyTypes.BODY_TYPE_FORM_DATA;
            case RequestImportBodyTypes.FORM_URLENCODED -> RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
            case RequestImportBodyTypes.BINARY -> RequestBodyTypes.BODY_TYPE_BINARY;
            default -> RequestBodyTypes.BODY_TYPE_NONE;
        };
    }
}
