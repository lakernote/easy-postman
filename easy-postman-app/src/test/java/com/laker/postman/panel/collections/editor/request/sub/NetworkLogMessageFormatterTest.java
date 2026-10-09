package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.util.I18nUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class NetworkLogMessageFormatterTest {
    private boolean originalChinese;

    @BeforeMethod
    public void useChineseLocale() {
        originalChinese = I18nUtil.isChinese();
        I18nUtil.setLocale("zh");
    }

    @AfterMethod
    public void restoreLocale() {
        I18nUtil.setLocale(originalChinese ? "zh" : "en");
    }

    @Test
    public void shouldLocalizeStageDisplayNameWithoutChangingTechnicalName() {
        assertEquals(NetworkLogStage.CALL_START.getStageName(), "RequestStart");
        assertEquals(NetworkLogStage.CALL_START.getDisplayName(), "请求开始");
        assertEquals(NetworkLogStage.CALL_END.getDisplayName(), "请求结束");
        assertEquals(NetworkLogStage.REQUEST_COMPLETE.getDisplayName(), "请求流程完成");
        assertEquals(NetworkLogStage.CONNECTION_RELEASED.getDisplayName(), "连接占用已释放");
    }

    @Test
    public void shouldLocalizeFollowUpDecisionAndKeepRequestDetails() {
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.FOLLOW_UP_DECISION,
                "Follow-up: true, response: 401, next: GET https://example.test/private");

        assertEquals(formatted, "后续请求：GET https://example.test/private（响应码 401）");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.FOLLOW_UP_DECISION,
                "Follow-up: false, response: 200, next: none"), "");
    }

    @Test
    public void shouldLocalizeProxyConfigurationAndPreserveRouteDetails() {
        assertEquals(NetworkLogStage.PROXY_SELECT.getDisplayName(), "代理配置");
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.PROXY_SELECT,
                "requestPolicy=DEFAULT, appProxyEnabled=false, appProxyMode=MANUAL, "
                        + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=true, "
                        + "manualProxyConfig=NOT_USED");

        assertEquals(formatted, "客户端代理：直连 · 请求策略：使用默认值");
    }

    @Test
    public void shouldLocalizeSystemSelectorAndKeepManualProxyEndpoint() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                        "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=SYSTEM, "
                                + "clientProxy=SYSTEM_SELECTOR, directHttpSocketFactoryJvmSocksBypass=true, "
                                + "manualProxyConfig=NOT_USED"),
                "客户端代理：系统代理选择器 · 请求策略：使用代理");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                        "requestPolicy=NO_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                                + "clientProxy=SOCKS:127.0.0.1:1080, directHttpSocketFactoryJvmSocksBypass=true, "
                                + "manualProxyConfig=FIELDS_PRESENT"),
                "客户端代理：SOCKS:127.0.0.1:1080 · 请求策略：不使用代理");
    }

    @Test
    public void shouldKeepProxyHostContainingCommaWhileLocalizingDiagnostic() {
        String formatted = NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                        + "clientProxy=HTTP:proxy,internal:8080, directHttpSocketFactoryJvmSocksBypass=true, "
                        + "manualProxyConfig=FIELDS_PRESENT");

        assertEquals(formatted, "客户端代理：HTTP:proxy,internal:8080 · 请求策略：使用代理");
    }

    @Test
    public void shouldLocalizeManualProxyFallbackReasons() {
        String diagnostic = "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=true, manualProxyConfig=";

        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "MISSING_HOST").contains("手动代理配置：缺少主机"));
        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "INVALID_HOST").contains("手动代理配置：主机格式无效"));
        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "INVALID_PORT").contains("手动代理配置：端口无效"));
    }

    @Test
    public void shouldRetainJvmSocksDiagnosticWhenBypassIsUnavailable() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                "requestPolicy=DEFAULT, appProxyEnabled=false, appProxyMode=MANUAL, "
                        + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=false, "
                        + "manualProxyConfig=NOT_USED"),
                "客户端代理：直连 · 请求策略：使用默认值\n底层 Socket 未绕过 JVM SOCKS 设置");
    }

    @Test
    public void shouldDistinguishReusedAndNewConnectionWithoutRepeatingReleaseRoute() {
        String route = "proxy=DIRECT, protocol=h2, remote=example.test:443";
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.CONNECTION_ACQUIRED,
                "Connection reused: " + route), "复用连接：" + route);
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.CONNECTION_ACQUIRED,
                "Connection acquired: " + route), "新连接：" + route);
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.CONNECTION_RELEASED,
                "Connection use released"), "");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.CALL_END, "done"), "");
    }

    @Test
    public void shouldExplainHeaderSnapshotWithoutChangingHeaderValues() {
        String headers = "\n:authority: example.test\nX-Custom: Status: 200";
        for (String leadingNewline : new String[]{"", "\n"}) {
            assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_HEADERS_END,
                    leadingNewline + "HTTP/2 header view (regular headers and :authority):"
                            + headers),
                    leadingNewline + "HTTP/2 请求头视图（常规头与 :authority）：" + headers);
            assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_HEADERS_END,
                    leadingNewline + "Header snapshot:" + headers),
                    leadingNewline + "请求头快照：" + headers);
            assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_HEADERS_END,
                    leadingNewline + "Headers unchanged"), leadingNewline + "请求头未变化");
        }
    }

    @Test
    public void shouldLocalizeResponseWaitWithoutChangingResponseHeaders() {
        String headers = "\nWait: 123ms\nX-Custom: Wait: 456ms\ncontent-type: application/json";
        for (NetworkLogEventStage stage : new NetworkLogEventStage[]{
                NetworkLogEventStage.RESPONSE_HEADERS_END, NetworkLogEventStage.RESPONSE_HEADERS_END_REDIRECT}) {
            for (String leadingNewline : new String[]{"", "\n"}) {
                assertEquals(NetworkLogMessageFormatter.format(stage,
                        leadingNewline + "HTTP/2 200\nWait: 1000ms" + headers),
                        leadingNewline + "HTTP/2 200\n等待响应：1000ms" + headers);
            }
        }
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.RESPONSE_HEADERS_END,
                "Wait: 1000ms" + headers), "Wait: 1000ms" + headers);
    }

    @Test
    public void shouldDistinguishRetryFromTerminalFailure() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.RETRY_DECISION,
                "Retry: true, reason: connection reset"), "继续重试：是 · 原因：connection reset");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.RETRY_DECISION,
                "Retry: false, reason: canceled"), "继续重试：否 · 原因：canceled");
    }

    @Test
    public void shouldExplainUnavailablePreviewWithoutChangingRealRequestBody() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_BODY_START,
                "Request body preview unavailable"), "请求体预览不可用");
        String actualBody = "\nRequest body preview unavailable";
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_BODY_START, actualBody), actualBody);
    }

    @Test
    public void shouldLocalizeRedirectDetailsAndFlowSummary() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REDIRECT,
                        "Redirect #1\nStatus: 302\nFrom: POST https://example.test/redirect\n"
                                + "To: GET https://other.test/get\nCross-Origin: true\n"
                                + "Removed Headers: Authorization, Cookie\nMethod Changed: POST → GET"),
                "第 1 次重定向\n状态码：302\n来源：POST https://example.test/redirect\n"
                        + "目标：GET https://other.test/get\n跨源：是\n"
                        + "已移除请求头：Authorization, Cookie\n请求方法变更：POST → GET");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_COMPLETE,
                        "Status: 200\nFinal: GET https://other.test/get\nRedirects: 1\nTotal: 457ms"),
                "状态码：200\n最终请求：GET https://other.test/get\n重定向次数：1\n总耗时：457ms");
    }

    @Test
    public void shouldExplainWhyRedirectWasNotFollowed() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REDIRECT,
                        "Redirect stopped: disabled, status: 302"),
                "未继续跟随：已关闭重定向（状态码 302）");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REDIRECT,
                        "Redirect stopped: missing Location, status: 302"),
                "未继续跟随：缺少 Location 响应头（状态码 302）");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REDIRECT,
                        "Redirect stopped: max redirects reached, status: 302, max: 20"),
                "未继续跟随：已达到重定向次数上限（状态码 302）\n重定向次数上限：20");
    }

    @Test
    public void shouldUseConciseEnglishWording() {
        I18nUtil.setLocale("en");
        assertEquals(NetworkLogStage.REQUEST_COMPLETE.getDisplayName(), "Request flow finished");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                        "requestPolicy=DEFAULT, appProxyEnabled=false, appProxyMode=MANUAL, "
                                + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=true, "
                                + "manualProxyConfig=NOT_USED"),
                "Client proxy: direct · Request policy: Use Default");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.REQUEST_COMPLETE,
                        "Status: 200\nRedirects: 1\nTotal: 457ms"),
                "Status: 200\nRedirects: 1\nTotal: 457ms");
    }

    @Test
    public void shouldLocalizeUnavailableProxyDiagnostics() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                "proxy diagnostics unavailable"), "无法获取代理诊断信息");
    }

    @Test
    public void shouldLocalizeTlsLabelsWithoutChangingCertificateValues() {
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.SECURE_CONNECT_END,
                "SSL connection using TLS_1_2\n"
                        + "Server certificate:\n"
                        + " subject: CN=example.test\n"
                        + "SSL certificate verify ok.\n");

        assertTrue(formatted.contains("SSL 连接：TLS_1_2"));
        assertTrue(formatted.contains("服务器证书："));
        assertTrue(formatted.contains("主题： CN=example.test"));
        assertTrue(formatted.contains("SSL 证书校验通过。"));
    }

    @Test
    public void shouldStateDisabledVerificationWithoutClaimingSuccess() {
        String tlsConnection = "TLSv1.2 / TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.SECURE_CONNECT_END,
                        "TLS connection: " + tlsConnection + "\nVerification: disabled"),
                "TLS 连接：" + tlsConnection + "\n证书校验：已关闭");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.SECURE_CONNECT_END,
                        "TLS connection: " + tlsConnection + "\nVerification: passed"),
                "TLS 连接：" + tlsConnection + "\n证书校验：通过");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.SECURE_CONNECT_END,
                        "TLS connection: " + tlsConnection + "\nVerification: enabled"),
                "TLS 连接：" + tlsConnection + "\n证书校验：已启用");
    }

    @Test
    public void shouldPreserveCertificateWarningAndUtcDates() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.SECURE_CONNECT_END,
                        "TLS connection: TLSv1.3 / TLS_AES_128_GCM_SHA256\nVerification: disabled\n"
                                + "Server certificate:\n subject: CN=example.test issuer: literal text\n"
                                + " start date: 2026-01-01T00:00:00Z\n expire date: 2027-01-01T00:00:00Z\n"
                                + " issuer: CN=Test CA\n⚠️  Certificate Warning: certificate expired"),
                "TLS 连接：TLSv1.3 / TLS_AES_128_GCM_SHA256\n证书校验：已关闭\n"
                        + "服务器证书：\n主题： CN=example.test issuer: literal text\n"
                        + "开始日期： 2026-01-01T00:00:00Z\n到期日期： 2027-01-01T00:00:00Z\n"
                        + "颁发者： CN=Test CA\n⚠️  证书警告：certificate expired");
    }
}
