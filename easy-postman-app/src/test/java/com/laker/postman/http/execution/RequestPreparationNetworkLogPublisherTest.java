package com.laker.postman.http.execution;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestVersions;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.util.I18nUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RequestPreparationNetworkLogPublisherTest {

    @DataProvider
    public Object[][] summaryLocales() {
        return new Object[][]{
                {Locale.ENGLISH, "Configured headers: 1 enabled / 2 total",
                        "Query parameters: 1 enabled / 2 total", "Path variables: 1 enabled / 1 total",
                        "Form-data rows: 1 enabled / 2 total", "Urlencoded rows: 0 enabled / 1 total",
                        "Pre-request script: present", "TLS verification: Disabled", "HTTP preference: Automatic negotiation"},
                {Locale.CHINESE, "配置请求头：启用 1 / 共 2",
                        "查询参数：启用 1 / 共 2", "路径变量：启用 1 / 共 1",
                        "Form-data 行：启用 1 / 共 2", "Urlencoded 行：启用 0 / 共 1",
                        "前置脚本：已配置", "TLS 证书校验：禁用", "HTTP 协议偏好：自动协商"}
        };
    }

    @Test(dataProvider = "summaryLocales")
    public void shouldPublishLocalizedConfiguredCountsWithoutRepeatingRequestDetails(
            Locale locale, String headers, String params, String pathVariables, String formData,
            String urlencoded, String preScript, String tls, String httpPreference) {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            I18nUtil.setLocale(locale);
            String message = publish(configuredRequest());

            for (String expected : List.of(headers, params, pathVariables, formData, urlencoded,
                    preScript, tls, httpPreference, "5000 ms", "raw")) {
                assertTrue(message.contains(expected), message);
            }
            for (String repeatedOrPrivate : List.of("Method:", "URL:", "Header Names:",
                    "example.test", "Authorization", "Bearer token", "X-Disabled", "Proxy Policy:")) {
                assertFalse(message.contains(repeatedOrPrivate), message);
            }
            assertFalse(message.contains("Post-response script"), message);
            assertFalse(message.contains("后置脚本"), message);
            assertFalse(message.contains("+0ms"), message);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void shouldOmitRoutineHeadersEmptyRowsAndAbsentScriptsAndExplainUnlimitedTimeout() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            I18nUtil.setLocale(Locale.ENGLISH);
            PreparedRequest request = new PreparedRequest();
            request.bodyType = RequestBodyTypes.BODY_TYPE_NONE;
            request.headersList = List.of(new HttpHeader(true, "Accept", "*/*"),
                    new HttpHeader(true, "User-Agent", "EasyPostman Client"),
                    new HttpHeader(false, " ", ""));
            request.paramsList = List.of(new HttpParam(true, "", "", ""));
            request.pathVariablesList = List.of();
            request.formDataList = List.of(new HttpFormData());
            request.urlencodedList = List.of(new HttpFormUrlencoded());
            request.prescript = " ";
            request.postscript = null;
            request.followRedirects = true;
            request.cookieJarEnabled = true;
            request.httpVersion = HttpRequestVersions.HTTP_2;

            String message = publish(request);

            assertEquals(message.strip().lines().count(), 2L, message);
            assertTrue(message.contains("Redirects: Enabled · Cookie jar: Enabled"), message);
            assertTrue(message.contains("TLS verification: Disabled · HTTP preference: HTTP/2 · Timeout: No timeout"), message);
            assertFalse(message.contains("0 enabled / 0 total"), message);
            assertFalse(message.contains("Body type:"), message);
            assertFalse(message.contains("Configured headers:"), message);
            assertFalse(message.contains("+0ms"), message);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void shouldNotPublishWhenNetworkLogIsDisabled() {
        PreparedRequest request = new PreparedRequest();
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;

        RequestPreparationNetworkLogPublisher.publish(request);

        assertTrue(events.isEmpty());
    }

    private static PreparedRequest configuredRequest() {
        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://example.test/api?debug=true";
        request.bodyType = RequestBodyTypes.BODY_TYPE_RAW;
        request.headersList = new ArrayList<>();
        request.headersList.add(new HttpHeader(true, "Authorization", "Bearer token"));
        request.headersList.add(new HttpHeader(false, "X-Disabled", "no"));
        request.headersList.add(new HttpHeader(true, "", ""));
        request.paramsList = new ArrayList<>();
        request.paramsList.add(new HttpParam(true, "debug", "true", ""));
        request.paramsList.add(new HttpParam(false, "page", "2", ""));
        request.paramsList.add(null);
        request.pathVariablesList = List.of(new HttpParam(true, "id", "1", ""));
        request.formDataList = List.of(new HttpFormData(true, "file", HttpFormData.TYPE_FILE, "a.txt"),
                new HttpFormData(false, "label", HttpFormData.TYPE_TEXT, "sample"));
        request.urlencodedList = List.of(new HttpFormUrlencoded(false, "unused", "value"));
        request.prescript = "pm.environment.set('token', 'abc')";
        request.postscript = "";
        request.followRedirects = true;
        request.cookieJarEnabled = true;
        request.sslVerificationEnabled = false;
        request.httpVersion = HttpRequestVersions.AUTO;
        request.requestTimeoutMs = 5_000;
        return request;
    }

    private static String publish(PreparedRequest request) {
        request.enableNetworkLog = true;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;

        RequestPreparationNetworkLogPublisher.publish(request);

        assertEquals(events.size(), 1);
        NetworkLogEvent event = events.get(0);
        assertEquals(event.stage(), NetworkLogEventStage.REQUEST_PREPARED);
        return event.message();
    }
}
