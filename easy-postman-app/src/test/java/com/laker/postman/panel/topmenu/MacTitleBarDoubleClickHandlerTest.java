package com.laker.postman.panel.topmenu;

import static org.testng.Assert.assertEquals;

import com.laker.postman.panel.topmenu.MacTitleBarDoubleClickHandler.Action;
import org.testng.annotations.Test;

public class MacTitleBarDoubleClickHandlerTest {

    @Test
    public void shouldFollowMacDoubleClickPreference() {
        assertEquals(MacTitleBarDoubleClickHandler.parseAction(null, null), Action.ZOOM);
        assertEquals(MacTitleBarDoubleClickHandler.parseAction("Maximize", null), Action.ZOOM);
        assertEquals(MacTitleBarDoubleClickHandler.parseAction("Fill", null), Action.ZOOM);
        assertEquals(MacTitleBarDoubleClickHandler.parseAction("Minimize", null), Action.MINIMIZE);
        assertEquals(MacTitleBarDoubleClickHandler.parseAction("None", "1"), Action.NONE);
        assertEquals(MacTitleBarDoubleClickHandler.parseAction(null, "1"), Action.MINIMIZE);
    }
}
