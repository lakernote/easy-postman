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
    }

    @Test
    public void shouldLocalizeFollowUpDecisionAndKeepRequestDetails() {
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.FOLLOW_UP_DECISION,
                "Follow-up: false, response: 200, next: none");

        assertEquals(formatted, "是否需要后续请求：否，响应码：200，下一请求：无");
    }

    @Test
    public void shouldLocalizeProxyConfigurationAndPreserveRouteDetails() {
        assertEquals(NetworkLogStage.PROXY_SELECT.getDisplayName(), "代理配置");
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.PROXY_SELECT,
                "requestPolicy=DEFAULT, appProxyEnabled=false, appProxyMode=MANUAL, "
                        + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=true, "
                        + "manualProxyConfig=NOT_USED");

        assertEquals(formatted, "代理配置：请求策略=使用默认值，应用代理启用=否，应用代理模式=手动配置，"
                + "客户端代理=直连，直连/HTTP 代理底层 Socket 绕过 JVM 隐式 SOCKS=是，手动代理配置=本次未使用");
    }

    @Test
    public void shouldLocalizeSystemSelectorAndKeepManualProxyEndpoint() {
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                        "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=SYSTEM, "
                                + "clientProxy=SYSTEM_SELECTOR, directHttpSocketFactoryJvmSocksBypass=true, "
                                + "manualProxyConfig=NOT_USED"),
                "代理配置：请求策略=使用代理，应用代理启用=是，应用代理模式=自动检测系统代理，"
                        + "客户端代理=系统代理选择器，直连/HTTP 代理底层 Socket 绕过 JVM 隐式 SOCKS=是，"
                        + "手动代理配置=本次未使用");
        assertEquals(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                        "requestPolicy=NO_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                                + "clientProxy=SOCKS:127.0.0.1:1080, directHttpSocketFactoryJvmSocksBypass=true, "
                                + "manualProxyConfig=FIELDS_PRESENT"),
                "代理配置：请求策略=不使用代理，应用代理启用=是，应用代理模式=手动配置，"
                        + "客户端代理=SOCKS:127.0.0.1:1080，直连/HTTP 代理底层 Socket 绕过 JVM 隐式 SOCKS=是，"
                        + "手动代理配置=主机与端口已填写");
    }

    @Test
    public void shouldKeepProxyHostContainingCommaWhileLocalizingDiagnostic() {
        String formatted = NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                        + "clientProxy=HTTP:proxy,internal:8080, directHttpSocketFactoryJvmSocksBypass=true, "
                        + "manualProxyConfig=FIELDS_PRESENT");

        assertEquals(formatted, "代理配置：请求策略=使用代理，应用代理启用=是，应用代理模式=手动配置，"
                + "客户端代理=HTTP:proxy,internal:8080，直连/HTTP 代理底层 Socket 绕过 JVM 隐式 SOCKS=是，"
                + "手动代理配置=主机与端口已填写");
    }

    @Test
    public void shouldLocalizeManualProxyFallbackReasons() {
        String diagnostic = "requestPolicy=USE_PROXY, appProxyEnabled=true, appProxyMode=MANUAL, "
                + "clientProxy=DIRECT, directHttpSocketFactoryJvmSocksBypass=true, manualProxyConfig=";

        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "MISSING_HOST").contains("手动代理配置=缺少主机"));
        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "INVALID_HOST").contains("手动代理配置=主机格式无效"));
        assertTrue(NetworkLogMessageFormatter.format(NetworkLogEventStage.PROXY_SELECT,
                diagnostic + "INVALID_PORT").contains("手动代理配置=端口无效"));
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
}
