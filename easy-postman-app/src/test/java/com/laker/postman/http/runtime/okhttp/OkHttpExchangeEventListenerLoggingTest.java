package com.laker.postman.http.runtime.okhttp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class OkHttpExchangeEventListenerLoggingTest {

    @Test
    public void socksMismatchShouldWriteSafeProxySummaryToApplicationLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(OkHttpExchangeEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            HttpRuntimeSettingsProvider.set(new HttpRuntimeSettings() {
                @Override
                public String getProxyUsername() {
                    return "proxy-secret-user";
                }

                @Override
                public String getProxyPassword() {
                    return "proxy-secret-password";
                }
            });
            PreparedRequest request = new PreparedRequest();
            request.url = "http://user:url-secret@example.test/?token=body-secret";
            request.proxyPolicy = HttpRequestProxyPolicy.NO_PROXY;
            request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
            OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);

            listener.connectStart(null, new InetSocketAddress("127.0.0.1", 80), Proxy.NO_PROXY);
            listener.connectFailed(null, new InetSocketAddress("127.0.0.1", 80), Proxy.NO_PROXY,
                    null, new SocketException("Reply from SOCKS server has bad version"));
            listener.connectStart(null, new InetSocketAddress("127.0.0.2", 80), Proxy.NO_PROXY);
            listener.connectFailed(null, new InetSocketAddress("127.0.0.2", 80), Proxy.NO_PROXY,
                    null, new SocketException("Reply from SOCKS server has bad version"));
            PreparedRequest performanceRequest = new PreparedRequest();
            performanceRequest.captureProfile = HttpCaptureProfile.PERFORMANCE_METRICS;
            new OkHttpExchangeEventListener(performanceRequest).connectFailed(null,
                    new InetSocketAddress("127.0.0.3", 80), Proxy.NO_PROXY,
                    null, new SocketException("Reply from SOCKS server has bad version"));

            assertEquals(appender.list.size(), 1);
            assertEquals(appender.list.get(0).getLevel(), Level.WARN);
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("SOCKS_PROTOCOL_MISMATCH"));
            assertTrue(message.contains("requestPolicy=NO_PROXY"));
            assertTrue(message.contains("routeProxy=DIRECT"));
            assertTrue(message.contains("manualProxyType=NOT_USED"));
            assertFalse(message.contains("secret"));
            assertFalse(message.contains("example.test"));
        } finally {
            HttpRuntimeSettingsProvider.reset();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
