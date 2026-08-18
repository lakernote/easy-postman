package com.laker.postman.panel.sidebar;

import com.laker.postman.util.IconUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class SidebarTabMetricsTest {

    @Test
    public void shouldClampExpandedTabWidth() {
        assertEquals(SidebarTabMetrics.expandedWidth(20), 60);
        assertEquals(SidebarTabMetrics.expandedWidth(80), 104);
        assertEquals(SidebarTabMetrics.expandedWidth(200), 140);
    }

    @Test
    public void shouldCalculateCollapsedTabWidthFromIconWidth() {
        assertEquals(SidebarTabMetrics.collapsedWidth(10), 36);
        assertEquals(SidebarTabMetrics.collapsedWidth(24), 36);
        assertEquals(SidebarTabMetrics.collapsedWidth(32), 44);
    }

    @Test
    public void expandedSelectedBackgroundShouldAccountForContentGutter() {
        int tabWidth = SidebarTabMetrics.expandedWidth(20);

        assertEquals(tabWidth, 60);
        assertEquals(SidebarTabMetrics.EXPANDED_SELECTED_BACKGROUND_INSET_LEFT, 10);
        assertEquals(SidebarTabMetrics.EXPANDED_SELECTED_BACKGROUND_INSET_RIGHT, 2);
        assertEquals(SidebarTabMetrics.expandedSelectedBackgroundX(0), 10);
        assertEquals(SidebarTabMetrics.expandedSelectedBackgroundWidth(tabWidth), 48);
        assertEquals(SidebarTabMetrics.expandedContentPaddingLeft(), 16);
        assertEquals(SidebarTabMetrics.expandedContentPaddingRight(), 8);
        assertEquals(
                SidebarTabMetrics.expandedContentPaddingLeft()
                        + (tabWidth
                        - SidebarTabMetrics.expandedContentPaddingLeft()
                        - SidebarTabMetrics.expandedContentPaddingRight()) / 2,
                SidebarTabMetrics.expandedSelectedBackgroundX(0)
                        + SidebarTabMetrics.expandedSelectedBackgroundWidth(tabWidth) / 2
        );
    }

    @Test
    public void shouldCalculateTabHeights() {
        assertEquals(SidebarTabMetrics.expandedHeight(20, 14), 60);
        assertEquals(SidebarTabMetrics.expandedHeight(40, 24), 86);
        assertEquals(SidebarTabMetrics.collapsedHeight(20), 40);
        assertEquals(SidebarTabMetrics.collapsedHeight(40), 56);
    }

    @Test
    public void collapsedSelectedBackgroundShouldStayCompactWithoutInflatingSelectedTab() {
        assertEquals(SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_WIDTH, 32);
        assertEquals(SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_HEIGHT, 32);
        assertEquals(SidebarTabMetrics.SELECTED_BACKGROUND_ARC, 10);
    }

    @Test
    public void collapsedSelectedBackgroundShouldUseVisualCenterCompensation() {
        assertEquals(SidebarTabMetrics.TAB_AREA_INSET_TOP, 4);
        assertEquals(SidebarTabMetrics.TAB_AREA_INSET_LEFT, 2);
        assertEquals(SidebarTabMetrics.TAB_AREA_INSET_RIGHT, 2);
        assertEquals(SidebarTabMetrics.COLLAPSED_VISUAL_CENTER_OFFSET_X, 2);
        assertEquals(SidebarTabMetrics.COLLAPSED_STATE_BACKGROUND_GUTTER_OFFSET_X, 1);
        assertEquals(SidebarTabMetrics.collapsedSelectedBackgroundX(0, 36, 32), 5);
        assertEquals(SidebarTabMetrics.collapsedSelectedBackgroundX(0, 32, 32), 1);
    }

    @Test
    public void collapsedIconPaddingShouldFollowVisualCenterOffset() {
        assertEquals(SidebarTabMetrics.collapsedIconPaddingLeft(), 8);
        assertEquals(SidebarTabMetrics.collapsedIconPaddingRight(), 4);
    }

    @Test
    public void collapsedSelectedBackgroundShouldAccountForCompensatedRailCenter() {
        int railWidth = SidebarTabMetrics.TAB_AREA_INSET_LEFT
                + SidebarTabMetrics.collapsedWidth(20)
                + SidebarTabMetrics.TAB_AREA_INSET_RIGHT;
        int backgroundX = SidebarTabMetrics.collapsedSelectedBackgroundX(
                SidebarTabMetrics.TAB_AREA_INSET_LEFT,
                SidebarTabMetrics.collapsedWidth(20),
                SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_WIDTH
        );

        assertEquals(railWidth, 40);
        assertEquals(backgroundX, 7);
        assertEquals(railWidth - backgroundX - SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_WIDTH, 1);
    }

    @Test
    public void sidebarTabIconShouldStayLightweightInCollapsedToolWindowStripe() {
        assertEquals(IconUtil.SIZE_TAB, 20);
    }
}
