package com.laker.postman.service.render;

import com.laker.postman.common.constants.ThemeColors;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.laker.postman.test.ThemeTokenTestSupport.remember;
import static com.laker.postman.test.ThemeTokenTestSupport.restore;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class HttpHtmlRendererTest {
    private Map<String, Object> previousThemeTokens;

    @BeforeMethod
    public void rememberThemeTokens() {
        previousThemeTokens = remember(
                ThemeColors.PRIMARY,
                ThemeColors.TEXT_PRIMARY,
                ThemeColors.TEXT_HINT,
                ThemeColors.SURFACE,
                ThemeColors.HOVER_BACKGROUND,
                ThemeColors.BORDER_LIGHT,
                ThemeColors.ERROR
        );
    }

    @AfterMethod
    public void restoreThemeTokens() {
        restore(previousThemeTokens);
    }

    @Test(description = "响应 body 的 HTML code block 应消除 pre 默认外边距，避免 Swing 详情页首行错位")
    public void testRenderResponseShouldWrapPreWithZeroMargin() {
        HttpResponse response = new HttpResponse();
        response.code = 101;
        response.protocol = "http/1.1";
        response.body = "[18:04:01] message";

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("<div style='background:"));
        assertTrue(html.contains("<pre style='margin:0;"));
    }

    @Test
    public void shouldNotRenderInternalPerformanceStreamHeaders() {
        HttpResponse response = new HttpResponse();
        response.code = 0;
        response.protocol = "-";
        response.headers = new LinkedHashMap<>();
        response.addHeader("Content-Type", List.of("text/plain"));
        response.addHeader("X-Easy-WS-Sent-Count", List.of("0"));
        response.addHeader("X-Easy-WS-Error", List.of("Resource temporarily unavailable"));

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("Content-Type"));
        assertFalse(html.contains("X-Easy-WS-Sent-Count"));
        assertFalse(html.contains("X-Easy-WS-Error"));
    }

    @Test
    public void shouldRenderZeroStatusAsError() {
        UIManager.put(ThemeColors.ERROR, new Color(31, 32, 33));
        HttpResponse response = new HttpResponse();
        response.code = 0;

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("color:#1f2021"));
    }

    @Test
    public void shouldRenderHtmlWithSemanticThemeTokens() {
        UIManager.put(ThemeColors.PRIMARY, new Color(1, 2, 3));
        UIManager.put(ThemeColors.TEXT_PRIMARY, new Color(4, 5, 6));
        UIManager.put(ThemeColors.TEXT_HINT, new Color(7, 8, 9));
        UIManager.put(ThemeColors.SURFACE, new Color(10, 11, 12));
        UIManager.put(ThemeColors.HOVER_BACKGROUND, new Color(13, 14, 15));
        UIManager.put(ThemeColors.BORDER_LIGHT, new Color(16, 17, 18));
        UIManager.put(ThemeColors.ERROR, new Color(19, 20, 21));

        HttpResponse response = new HttpResponse();
        response.code = 0;
        response.protocol = "http/1.1";
        response.body = "payload";

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("color:#040506"));
        assertTrue(html.contains("color:#010203"));
        assertTrue(html.contains("color:#070809"));
        assertTrue(html.contains("background:#0a0b0c"));
        assertTrue(html.contains("background:#0d0e0f"));
        assertTrue(html.contains("border:1px solid #131415"));
    }

    @Test
    public void shouldRenderKeyValueRowsWithoutFullWidthBackgroundBands() {
        PreparedRequest request = new PreparedRequest();
        request.url = "https://example.test/api";
        request.method = "POST";
        request.headersList = List.of(new HttpHeader(true, "Accept", "*/*"));

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("line-height:1.35"));
        assertFalse(html.contains("border-bottom:1px solid"));
        assertFalse(html.contains("padding:3px 8px;background:"));
    }

    @Test
    public void shouldRenderTimelineWithThemeAwareSurfaceAndTrackColors() {
        UIManager.put(ThemeColors.SURFACE, new Color(10, 11, 12));
        UIManager.put(ThemeColors.HOVER_BACKGROUND, new Color(13, 14, 15));
        UIManager.put(ThemeColors.BORDER_LIGHT, new Color(16, 17, 18));

        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setQueueStart(990L);
        eventInfo.setCallStart(1_000L);
        eventInfo.setConnectStart(1_010L);
        eventInfo.setConnectEnd(1_030L);
        eventInfo.setRequestHeadersStart(1_030L);
        eventInfo.setRequestHeadersEnd(1_040L);
        eventInfo.setResponseHeadersStart(1_080L);
        eventInfo.setResponseBodyStart(1_085L);
        eventInfo.setResponseBodyEnd(1_100L);
        eventInfo.setCallEnd(1_100L);

        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderTimingInfo(response);

        assertTrue(html.contains("Timeline"));
        assertTrue(html.contains("background:#0a0b0c"));
        assertTrue(html.contains("background:#0d0e0f"));
        assertTrue(html.contains("border-bottom:1px solid"));
        assertTrue(html.contains("border-top:1px solid"));
        assertFalse(html.contains("border-bottom:1px solid #101112"));
        assertFalse(html.contains("background:#101112;height:8px"));
    }

    @Test
    public void shouldRenderTransparentRouteDiagnosticsAndEscapeNetworkErrors() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setDnsHost("dual.example.test");
        eventInfo.replaceDnsAddresses(List.of("[2001:db8::10]", "192.0.2.10"));
        eventInfo.setRetryDecisionCount(2);
        eventInfo.setRetryCount(1);
        eventInfo.addRouteAttempt(new HttpRouteAttempt(
                "[2001:db8::10]:443", "IPv6", 100L, 125L, 25L,
                false, true, null, "canceled <after fallback>"
        ));
        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderEventInfo(response);

        assertTrue(html.contains("dual.example.test"));
        assertTrue(html.contains("[2001:db8::10]:443"));
        assertTrue(html.contains("canceled &lt;after fallback&gt;"));
        assertFalse(html.contains("canceled <after fallback>"));
        assertTrue(html.contains("1 / 2"));
    }

    @Test
    public void shouldRenderTimelinePhasesAsPeerRowsWithoutTreeGlyphs() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setCallStart(1_000L);
        eventInfo.setDnsStart(1_010L);
        eventInfo.setDnsEnd(1_020L);
        eventInfo.setConnectStart(1_020L);
        eventInfo.setSecureConnectStart(1_030L);
        eventInfo.setSecureConnectEnd(1_050L);
        eventInfo.setConnectEnd(1_050L);
        eventInfo.setResponseHeadersStart(1_080L);
        eventInfo.setCallEnd(1_100L);

        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderTimingInfo(response);

        assertTrue(html.contains("DNS Lookup"));
        assertTrue(html.contains("TCP Connect"));
        assertTrue(html.contains("TLS Handshake"));
        assertFalse(html.contains("↳"));
        assertFalse(html.contains("SSL/TLS"));
    }

    @Test
    public void shouldRenderTimelinePercentBesideBarTrack() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setCallStart(1_000L);
        eventInfo.setDnsStart(1_000L);
        eventInfo.setDnsEnd(1_025L);
        eventInfo.setResponseHeadersStart(1_050L);
        eventInfo.setCallEnd(1_100L);

        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderTimingInfo(response);

        assertTrue(html.contains("<td width='88%' style='padding:0;'>"));
        assertTrue(html.contains("<td width='12%' style='padding:0 0 0 6px;text-align:right;white-space:nowrap;"));
        assertFalse(html.contains("</table><span style='color:"));
    }

    @Test
    public void shouldRenderOtherPhaseSoDisplayedTimelineAddsUpToTotal() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setCallStart(1_000L);
        eventInfo.setRequestHeadersStart(1_005L);
        eventInfo.setRequestHeadersEnd(1_013L);
        eventInfo.setResponseHeadersStart(1_275L);
        eventInfo.setResponseBodyStart(1_279L);
        eventInfo.setResponseBodyEnd(1_279L);
        eventInfo.setCallEnd(1_279L);

        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderTimingInfo(response);

        assertTrue(html.contains("Total"));
        assertTrue(html.contains("279 ms"));
        assertTrue(html.contains("Other"));
        assertTrue(html.contains("4 ms"));
        assertTrue(html.contains(">2%</td>"));
        assertTrue(html.contains(">3%</td>"));
        assertTrue(html.contains(">94%</td>"));
        assertTrue(html.contains(">1%</td>"));
    }

    @Test
    public void shouldCalculateTimelinePhasesWithoutNestedDoubleCounting() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setCallStart(1_000L);
        eventInfo.setDnsStart(1_010L);
        eventInfo.setDnsEnd(1_143L);
        eventInfo.setConnectStart(1_143L);
        eventInfo.setSecureConnectStart(1_173L);
        eventInfo.setSecureConnectEnd(1_529L);
        eventInfo.setConnectEnd(1_529L);
        eventInfo.setRequestHeadersStart(1_530L);
        eventInfo.setRequestHeadersEnd(1_533L);
        eventInfo.setResponseHeadersStart(2_144L);
        eventInfo.setResponseBodyStart(2_144L);
        eventInfo.setResponseBodyEnd(2_144L);
        eventInfo.setCallEnd(2_353L);

        TimingCalculator calculator = new TimingCalculator(eventInfo);

        assertEquals(calculator.getStalled(), 10L);
        assertEquals(calculator.getDns(), 133L);
        assertEquals(calculator.getConnect(), 30L);
        assertEquals(calculator.getTls(), 356L);
        assertEquals(calculator.getRequestSent(), 3L);
        assertEquals(calculator.getServerCost(), 611L);
    }

    @Test
    public void shouldRenderConfiguredRequestWhenSentSnapshotMissing() {
        PreparedRequest request = new PreparedRequest();
        request.url = "https://example.test/api";
        request.method = "POST";
        request.headersList = List.of(
                new HttpHeader(true, "Cookie", "cf_clearance=clearance"),
                new HttpHeader(false, "Disabled", "hidden")
        );
        request.body = "{\"action\":\"next\"}";

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("Configured Headers"));
        assertTrue(html.contains("Cookie"));
        assertTrue(html.contains("cf_clearance=clearance"));
        assertFalse(html.contains("Disabled"));
        assertTrue(html.contains("Configured Body"));
        assertTrue(html.contains("{&quot;action&quot;:&quot;next&quot;}"));
    }

    @Test
    public void shouldPreferSentRequestSnapshotInRequestDetails() {
        PreparedRequest request = new PreparedRequest();
        request.url = "https://example.test/api";
        request.method = "POST";
        request.headersList = List.of(new HttpHeader(true, "Cookie", "configured-cookie"));
        request.body = "configured body";
        request.sentHeadersList = List.of(new HttpHeader(true, "Cookie", "sent-cookie"));
        request.sentRequestBody = "sent body";

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("Sent Headers"));
        assertTrue(html.contains("sent-cookie"));
        assertFalse(html.contains("configured-cookie"));
        assertTrue(html.contains("Sent Body"));
        assertTrue(html.contains("sent body"));
        assertFalse(html.contains("configured body"));
    }

    @Test
    public void shouldPreferSentUrlAndMethodInRequestDetails() {
        PreparedRequest request = new PreparedRequest();
        request.url = "https://example.test/start";
        request.method = "POST";
        request.sentUrl = "https://example.test/target?trace=1";
        request.sentMethod = "GET";
        request.sentHeadersList = List.of(new HttpHeader(true, "Host", "example.test"));

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("https://example.test/target?trace=1"));
        assertTrue(html.contains("GET"));
        assertFalse(html.contains("https://example.test/start"));
        assertFalse(html.contains(">POST<"));
    }

    @Test
    public void shouldRenderWebSocketUrlAndHandshakeUrlInRequestDetails() {
        PreparedRequest request = new PreparedRequest();
        request.url = "ws://example.test/socket";
        request.method = "GET";
        request.sentUrl = "http://example.test/socket";
        request.sentMethod = "GET";
        request.sentHeadersList = List.of(new HttpHeader(true, "Upgrade", "websocket"));

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("WebSocket URL"));
        assertTrue(html.contains("ws://example.test/socket"));
        assertTrue(html.contains("Handshake URL"));
        assertTrue(html.contains("http://example.test/socket"));
        assertTrue(html.contains("Handshake Method"));
    }

    @Test
    public void shouldKeepRequestBodyPreviewSmall() {
        String body = "x".repeat(3 * 1024);
        PreparedRequest request = new PreparedRequest();
        request.url = "https://example.test/api";
        request.method = "POST";
        request.sentRequestBody = body;

        String html = HttpHtmlRenderer.renderRequest(request);

        assertFalse(html.contains(body));
        assertTrue(html.contains("Truncated"));
        assertTrue(html.contains("showing first 2KB"));
    }

    @Test
    public void shouldNotShowConfiguredBodyWhenSentSnapshotHasNoBody() {
        PreparedRequest request = new PreparedRequest();
        request.url = "wss://example.test/socket";
        request.method = "GET";
        request.body = "configured websocket message";
        request.sentHeadersList = List.of(new HttpHeader(true, "Host", "example.test"));

        String html = HttpHtmlRenderer.renderRequest(request);

        assertTrue(html.contains("Sent Headers"));
        assertFalse(html.contains("Configured Body"));
        assertFalse(html.contains("configured websocket message"));
    }

    @Test
    public void shouldRenderThreadFromEventInfoWhenResponseThreadMissing() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setThreadName("SwingWorker-pool-3-thread-1");
        eventInfo.setLocalAddress("127.0.0.1:51000");
        eventInfo.setRemoteAddress("127.0.0.1:8080");

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.protocol = "http/1.1";
        response.body = "";
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("SwingWorker-pool-3-thread-1"));
        assertTrue(html.contains("127.0.0.1:51000"));
        assertTrue(html.contains("127.0.0.1:8080"));
    }

    @Test
    public void shouldRenderNetworkErrorMessageInResponseDetails() {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setErrorMessage("timeout");

        HttpResponse response = new HttpResponse();
        response.code = 0;
        response.body = "";
        response.httpEventInfo = eventInfo;

        String html = HttpHtmlRenderer.renderResponse(response);

        assertTrue(html.contains("Network Error"));
        assertTrue(html.contains("timeout"));
    }
}
