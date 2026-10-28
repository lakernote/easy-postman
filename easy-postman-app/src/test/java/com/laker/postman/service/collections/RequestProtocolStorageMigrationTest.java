package com.laker.postman.service.collections;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RequestProtocolStorageMigrationTest {
    @Test
    public void shouldLoadStoredSseRequestAsHttpWithoutChangingItsRequestData() {
        JSONObject storedRequest = JSONUtil.createObj();
        storedRequest.set("method", "POST");
        storedRequest.set("protocol", "SSE");
        storedRequest.set("url", "https://example.com/chat");
        storedRequest.set("body", "{\"stream\":false}");

        RequestProtocolStorageMigration.normalizeLegacySseProtocol(storedRequest);
        HttpRequestItem item = storedRequest.toBean(HttpRequestItem.class);

        assertEquals(item.getProtocol(), RequestItemProtocolEnum.HTTP);
        assertEquals(item.getMethod(), "POST");
        assertEquals(item.getBody(), "{\"stream\":false}");
    }
}
