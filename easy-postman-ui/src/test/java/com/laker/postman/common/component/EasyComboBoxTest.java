package com.laker.postman.common.component;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

import static org.testng.Assert.*;

public class EasyComboBoxTest {

    private static final String LONG_ITEM = "a-very-very-very-long-workspace-name-that-overflows";

    private LookAndFeel originalLaf;

    @BeforeMethod
    public void rememberLaf() {
        originalLaf = UIManager.getLookAndFeel();
    }

    @AfterMethod(alwaysRun = true)
    public void restoreLaf() throws Exception {
        UIManager.setLookAndFeel(originalLaf);
    }

    @Test
    public void shouldUseCustomPreferredWidth() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"GET", "POST"}, 180);

        assertEquals(comboBox.getPreferredSize().width, 180);
    }

    @Test
    public void fixedMaxShouldMatchPlainComboBox() {
        String[] items = {"A", "MGS-local", LONG_ITEM};
        EasyComboBox<String> comboBox = new EasyComboBox<>(items, EasyComboBox.WidthMode.FIXED_MAX);

        assertEquals(comboBox.getPreferredSize().width, plainWidth(comboBox, items));
    }

    @Test
    public void dynamicWidthShouldEqualPlainComboBoxWithOnlySelectedItem() throws Exception {
        assertDynamicWidthMatchesPlainComboBox();
        UIManager.setLookAndFeel(new FlatLightLaf());
        assertDynamicWidthMatchesPlainComboBox();
    }

    @Test
    public void dynamicWidthShouldFollowSelectionWithinRange() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A", "MGS-local", LONG_ITEM});
        comboBox.setDynamicWidthRange(64, 160);
        comboBox.setFixedHeight(28);

        comboBox.setSelectedItem("A");
        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(64));
        assertEquals(comboBox.getPreferredSize().height, UIScale.scale(28));
        assertNull(comboBox.getToolTipText());

        comboBox.setSelectedItem("MGS-local");
        int mediumWidth = comboBox.getPreferredSize().width;
        assertTrue(mediumWidth >= UIScale.scale(64) && mediumWidth <= UIScale.scale(160));

        comboBox.setSelectedItem(LONG_ITEM);
        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(160));
        assertEquals(comboBox.getMaximumSize().width, UIScale.scale(160));
        assertEquals(comboBox.getMinimumSize().width, UIScale.scale(64));
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);
    }

    @Test
    public void emptyComboBoxShouldStillRespectRange() {
        EasyComboBox<String> comboBox = new EasyComboBox<>();
        comboBox.setDynamicWidthRange(64, 160);

        int width = comboBox.getPreferredSize().width;
        assertTrue(width >= UIScale.scale(64) && width <= UIScale.scale(160));
        assertNull(comboBox.getToolTipText());
    }

    @Test
    public void prototypeWidthShouldStillRespectRange() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A", LONG_ITEM});
        comboBox.setPrototypeDisplayValue(LONG_ITEM);
        comboBox.setDynamicWidthRange(64, 160);

        assertEquals(comboBox.getPreferredSize().width, UIScale.scale(160));
    }

    @Test
    public void prototypeToolTipShouldDependOnSelectedItem() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A", LONG_ITEM});
        comboBox.setPrototypeDisplayValue(LONG_ITEM);
        comboBox.setDynamicWidthRange(64, 160);
        comboBox.setSelectedItem("A");
        assertNull(comboBox.getToolTipText());

        comboBox.setPrototypeDisplayValue("AB");
        comboBox.setSelectedItem(LONG_ITEM);
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);
    }

    @Test
    public void truncatedToolTipShouldDisappearWhenNotDynamicSizing() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{LONG_ITEM});
        comboBox.setDynamicWidthRange(64, 160);
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);

        comboBox.setPreferredSize(new Dimension(600, 28));
        assertNull(comboBox.getToolTipText());
        comboBox.setPreferredSize(null);
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);

        comboBox.setWidthMode(EasyComboBox.WidthMode.FIXED_MAX);
        assertNull(comboBox.getToolTipText());
    }

    @Test
    public void externalToolTipShouldBeKeptAndRestored() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A", LONG_ITEM});
        comboBox.setToolTipText("external");
        comboBox.setDynamicWidthRange(64, 160);
        assertEquals(comboBox.getToolTipText(), "external");

        comboBox.setSelectedItem(LONG_ITEM);
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);

        comboBox.setSelectedItem("A");
        assertEquals(comboBox.getToolTipText(), "external");
    }

    @Test
    public void toolTipManagerShouldStayRegisteredAfterExternalToolTipCleared() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{LONG_ITEM});
        comboBox.setDynamicWidthRange(64, 160);
        comboBox.setToolTipText("external");
        comboBox.setToolTipText(null);

        assertTrue(Arrays.asList(comboBox.getMouseListeners()).contains(ToolTipManager.sharedInstance()));
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);
    }

    @Test
    public void sizeShouldFollowChangesWithoutParent() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A"});
        int shortWidth = comboBox.getPreferredSize().width;

        comboBox.removeAllItems();
        comboBox.addItem("MGS-local-environment");
        assertTrue(comboBox.getPreferredSize().width > shortWidth);

        int beforeFont = comboBox.getPreferredSize().width;
        comboBox.setFont(comboBox.getFont().deriveFont(comboBox.getFont().getSize2D() * 2));
        assertTrue(comboBox.getPreferredSize().width > beforeFont);
    }

    @Test
    public void inPlaceModelChangeShouldRefreshWidth() {
        MutableItem item = new MutableItem("A");
        RenamableModel model = new RenamableModel(item);
        EasyComboBox<MutableItem> comboBox = new EasyComboBox<>();
        comboBox.setModel(model);
        int before = comboBox.getPreferredSize().width;

        item.text = LONG_ITEM;
        model.fireItemChanged(0);
        assertTrue(comboBox.getPreferredSize().width > before);
    }

    @Test
    public void setModelShouldRefreshWidth() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A"});
        int before = comboBox.getPreferredSize().width;

        comboBox.setModel(new DefaultComboBoxModel<>(new String[]{LONG_ITEM}));
        assertTrue(comboBox.getPreferredSize().width > before);
    }

    @Test
    public void sizeRangeShouldScaleConsistently() {
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{LONG_ITEM});
        comboBox.setDynamicWidthRange(64, 0);
        int naturalWidth = comboBox.getPreferredSize().width;
        assertNull(comboBox.getToolTipText());

        // 阈值按实测宽度推算，避免 HiDPI 缩放下固定数值失效
        comboBox.setDynamicWidthRange(64, (int) Math.ceil(2.0 * naturalWidth / UIScale.getUserScaleFactor()));
        assertEquals(comboBox.getPreferredSize().width, naturalWidth);
        assertNull(comboBox.getToolTipText());

        comboBox.setFont(comboBox.getFont().deriveFont(comboBox.getFont().getSize2D() * 4));
        assertEquals(comboBox.getToolTipText(), LONG_ITEM);
    }

    @Test
    public void themeSwitchShouldNotFailWhileUiIsInstalling() throws Exception {
        UIManager.setLookAndFeel(new FlatLightLaf());
        EasyComboBox<String> comboBox = new EasyComboBox<>(new String[]{"A", "MGS-local"});
        comboBox.setDynamicWidthRange(64, 160);
        int lightWidth = comboBox.getPreferredSize().width;

        UIManager.setLookAndFeel(new FlatDarkLaf());
        SwingUtilities.updateComponentTreeUI(comboBox);

        assertEquals(comboBox.getPreferredSize().width, lightWidth);
    }

    @Test
    public void flatLafMinimumWidthShouldApplyWhenAllItemsAreShort() throws Exception {
        UIManager.setLookAndFeel(new FlatLightLaf());
        assertDynamicWidthMatchesPlainComboBox("A", "B");
    }

    @Test
    public void flatLafStylePropertiesShouldRefreshWidth() throws Exception {
        UIManager.setLookAndFeel(new FlatLightLaf());
        String[] items = {"A", "MGS-local"};
        EasyComboBox<String> comboBox = new EasyComboBox<>(items);
        JComboBox<String> plain = new JComboBox<>(new String[]{"A"});
        plain.setFont(comboBox.getFont());

        comboBox.putClientProperty("JComponent.minimumWidth", 150);
        plain.putClientProperty("JComponent.minimumWidth", 150);
        assertEquals(comboBox.getPreferredSize().width, plain.getPreferredSize().width);

        comboBox.putClientProperty("JComponent.minimumWidth", null);
        plain.putClientProperty("JComponent.minimumWidth", null);
        comboBox.putClientProperty("FlatLaf.style", "minimumWidth: 10; padding: 2,30,2,30");
        plain.putClientProperty("FlatLaf.style", "minimumWidth: 10; padding: 2,30,2,30");
        assertEquals(comboBox.getPreferredSize().width, plain.getPreferredSize().width);
    }

    private static void assertDynamicWidthMatchesPlainComboBox() {
        assertDynamicWidthMatchesPlainComboBox("A", "MGS-local", LONG_ITEM);
    }

    private static void assertDynamicWidthMatchesPlainComboBox(String... items) {
        EasyComboBox<String> comboBox = new EasyComboBox<>(items);
        for (String item : items) {
            comboBox.setSelectedItem(item);
            assertEquals(comboBox.getPreferredSize().width, plainWidth(comboBox, item),
                    UIManager.getLookAndFeel().getName() + " / " + item);
        }
    }

    private static int plainWidth(JComboBox<String> reference, String... items) {
        JComboBox<String> plain = new JComboBox<>(items);
        plain.setFont(reference.getFont());
        return plain.getPreferredSize().width;
    }

    private static final class MutableItem {
        private String text;

        private MutableItem(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final class RenamableModel extends DefaultComboBoxModel<MutableItem> {
        private RenamableModel(MutableItem item) {
            super(new MutableItem[]{item});
        }

        private void fireItemChanged(int index) {
            fireContentsChanged(this, index, index);
        }
    }
}
