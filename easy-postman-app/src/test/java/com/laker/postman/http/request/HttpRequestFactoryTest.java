package com.laker.postman.http.request;

import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class HttpRequestFactoryTest {

    @Test
    public void shouldUseVersionedUserAgentInDefaultRequest() {
        HttpRequestItem request = HttpRequestFactory.createDefaultRequest();

        assertNotNull(request.getHeadersList());
        assertEquals(findHeaderValues(request.getHeadersList(), HttpRequestFactory.USER_AGENT),
                List.of(HttpRequestFactory.EASY_POSTMAN_CLIENT));
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(HttpRequestFactory.EASY_POSTMAN_CLIENT));
    }

    @Test
    public void shouldIncludeExpectedDefaultHeadersOnce() {
        HttpRequestItem request = HttpRequestFactory.createDefaultRequest();

        assertEquals(findHeaderValues(request.getHeadersList(), HttpRequestFactory.USER_AGENT).size(), 1);
        assertEquals(findHeaderValues(request.getHeadersList(), HttpRequestFactory.ACCEPT),
                List.of("*/*"));
        assertEquals(findHeaderValues(request.getHeadersList(), HttpRequestFactory.ACCEPT_ENCODING),
                List.of(HttpRequestFactory.ACCEPT_ENCODING_VALUE));
        assertEquals(findHeaderValues(request.getHeadersList(), HttpRequestFactory.CONNECTION),
                List.of(HttpRequestFactory.CONNECTION_VALUE));
    }

    @Test
    public void shouldCreateBlankRequestsForAddDialogProtocols() {
        HttpRequestItem http = HttpRequestFactory.createBlankRequest(RequestItemProtocolEnum.HTTP);
        HttpRequestItem webSocket = HttpRequestFactory.createBlankRequest(RequestItemProtocolEnum.WEBSOCKET);
        HttpRequestItem eventStream = HttpRequestFactory.createDefaultEventStreamRequest();

        assertEquals(http.getUrl(), "");
        assertEquals(http.getMethod(), "GET");
        assertEquals(webSocket.getProtocol(), RequestItemProtocolEnum.WEBSOCKET);
        assertEquals(webSocket.getUrl(), "");
        assertEquals(webSocket.getBodyType(), RequestBodyTypes.BODY_TYPE_RAW);
        assertEquals(findHeaderValues(webSocket.getHeadersList(), HttpRequestFactory.ACCEPT_ENCODING),
                List.of(HttpRequestFactory.IDENTITY_ACCEPT_ENCODING));
        assertEquals(eventStream.getProtocol(), RequestItemProtocolEnum.HTTP);
        assertEquals(findHeaderValues(eventStream.getHeadersList(), HttpRequestFactory.ACCEPT),
                List.of(HttpRequestFactory.TEXT_EVENT_STREAM));
        assertEquals(findHeaderValues(eventStream.getHeadersList(), HttpRequestFactory.ACCEPT_ENCODING),
                List.of(HttpRequestFactory.IDENTITY_ACCEPT_ENCODING));
    }

    private List<String> findHeaderValues(List<HttpHeader> headers, String key) {
        return headers.stream()
                .filter(HttpHeader::isEnabled)
                .filter(header -> key.equalsIgnoreCase(header.getKey()))
                .map(HttpHeader::getValue)
                .toList();
    }
}
