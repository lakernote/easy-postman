package com.laker.postman.service.setting;

import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import java.awt.event.KeyEvent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ShortcutManagerTest extends AbstractSwingUiTest {

    @Test
    public void clearedShortcutShouldBeUnassignedAndNotConflict() {
        ShortcutManager.ShortcutConfig config = ShortcutManager.getShortcut(ShortcutManager.CLOSE_ALL_TABS);
        int originalKeyCode = config.getKeyCode();
        int originalModifiers = config.getModifiers();
        try {
            ShortcutManager.setShortcut(ShortcutManager.CLOSE_ALL_TABS, KeyEvent.VK_F9, 0);
            ShortcutManager.clearShortcut(ShortcutManager.CLOSE_ALL_TABS);

            assertFalse(ShortcutManager.isAssigned(ShortcutManager.CLOSE_ALL_TABS));
            assertNull(ShortcutManager.getKeyStroke(ShortcutManager.CLOSE_ALL_TABS));
            assertEquals(ShortcutManager.getShortcutText(ShortcutManager.CLOSE_ALL_TABS), "");
            assertNull(ShortcutManager.checkConflict(ShortcutManager.SEND_REQUEST, KeyEvent.VK_UNDEFINED, 0));
            assertNull(ShortcutManager.checkConflict(ShortcutManager.SEND_REQUEST, KeyEvent.VK_F9, 0));

            ShortcutManager.setShortcut(ShortcutManager.CLOSE_ALL_TABS, KeyEvent.VK_F9, 0);

            assertTrue(ShortcutManager.isAssigned(ShortcutManager.CLOSE_ALL_TABS));
            assertNotNull(ShortcutManager.getKeyStroke(ShortcutManager.CLOSE_ALL_TABS));
        } finally {
            ShortcutManager.setShortcut(ShortcutManager.CLOSE_ALL_TABS, originalKeyCode, originalModifiers);
        }
    }
}
