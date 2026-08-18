package com.laker.postman.i18n;

import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;

public class MenuLabelI18nTest {

    @Test
    public void chineseNavigationAndCollectionActionLabelsShouldUseTestDomainNames() {
        ResourceBundle zh = ResourceBundle.getBundle("messages", Locale.SIMPLIFIED_CHINESE);

        assertEquals(zh.getString(MessageKeys.COLLECTIONS_MENU_ADD_TO_FUNCTIONAL), "功能测试");
        assertEquals(zh.getString(MessageKeys.MENU_PERFORMANCE), "性能测试");
        assertEquals(zh.getString(MessageKeys.MENU_MOCK_SERVER), "Mock Server");
    }

    @Test
    public void englishFunctionalNavigationLabelShouldBeShortDomainName() {
        ResourceBundle en = ResourceBundle.getBundle("messages", Locale.ENGLISH);

        assertEquals(en.getString(MessageKeys.COLLECTIONS_MENU_ADD_TO_FUNCTIONAL), "Functional");
        assertEquals(en.getString(MessageKeys.MENU_FUNCTIONAL), "Functional");
        assertEquals(en.getString(MessageKeys.MENU_MOCK_SERVER), "Mock Server");
    }
}
