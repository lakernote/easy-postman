package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TabBarBuilderTest {

    @Test(description = "WebSocket 响应区域应显示网络日志标签，用于查看握手请求")
    public void shouldIncludeNetworkLogTabForWebSocketResponses() {
        TabBarBuilder.TabConfig config = TabBarBuilder.createWebSocketTabs();

        assertEquals(config.tabNames.length, 3);
        assertFalse(java.util.Arrays.asList(config.tabNames)
                .contains(I18nUtil.getMessage(MessageKeys.TAB_TIMING)));
        assertTrue(java.util.Arrays.asList(config.tabNames)
                .contains(I18nUtil.getMessage(MessageKeys.TAB_NETWORK_LOG)));
    }
}
