package com.laker.postman.common.component.combobox;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;

import static org.testng.Assert.*;

public class MenuBarComboBoxTest {

    private static final String LONG_ITEM = "a-very-very-very-long-workspace-name-that-overflows";

    private LookAndFeel originalLaf;

    @BeforeMethod
    public void setUpLaf() throws Exception {
        originalLaf = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new FlatLightLaf());
    }

    @AfterMethod(alwaysRun = true)
    public void restoreLaf() throws Exception {
        UIManager.setLookAndFeel(originalLaf);
    }

    @Test
    public void widthShouldStayWithinMenuBarRange() {
        TestComboBox comboBox = new TestComboBox("A", LONG_ITEM);

        comboBox.setSelectedIndex(0);
        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(64));
        assertNull(comboBox.getToolTipText());

        comboBox.setSelectedIndex(1);
        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(200));
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);
        assertEquals(comboBox.getPreferredSize().height, UIScale.scale(28));
    }

    @Test
    public void themeSwitchShouldKeepMenuBarStyle() throws Exception {
        TestComboBox comboBox = new TestComboBox("A");

        UIManager.setLookAndFeel(new FlatDarkLaf());
        SwingUtilities.updateComponentTreeUI(comboBox);

        assertEquals(comboBox.getBackground(), UIManager.getColor("MenuBar.background"));
        assertTrue(String.valueOf(comboBox.getClientProperty("FlatLaf.style")).contains("buttonBackground"));
        assertEquals(comboBox.getInsets(), new Insets(0, 0, 0, 0));
        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(64));
    }

    private static final class TestComboBox extends MenuBarComboBox<String> {
        private TestComboBox(String... items) {
            for (String item : items) {
                addItem(item);
            }
        }
    }
}
