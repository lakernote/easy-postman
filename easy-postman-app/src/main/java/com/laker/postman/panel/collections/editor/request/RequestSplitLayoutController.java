package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.panel.collections.editor.request.sub.ResponsePanel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.util.function.Supplier;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class RequestSplitLayoutController {
    private final JSplitPane splitPane;
    private final ResponsePanel responsePanel;
    private final Supplier<Boolean> isEffectiveWebSocketProtocolSupplier;
    private boolean initialDividerLocationSet;

    void updateLayoutOrientation(boolean isVertical) {
        int targetOrientation = isVertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
        if (splitPane.getOrientation() == targetOrientation) {
            return;
        }

        double ratio = isVertical ? getDefaultResizeWeight() : 0.5;
        splitPane.setOrientation(targetOrientation);
        splitPane.setResizeWeight(ratio);
        initialDividerLocationSet = false;

        splitPane.revalidate();
        int totalSize = isVertical ? splitPane.getHeight() : splitPane.getWidth();
        if (totalSize > 0) {
            splitPane.setDividerLocation(ratio);
        }

        if (responsePanel != null) {
            responsePanel.updateLayoutOrientation(isVertical);
        }
    }

    void handleInitialLayout() {
        if (initialDividerLocationSet || splitPane == null) {
            return;
        }

        if (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT && splitPane.getWidth() > 0) {
            initialDividerLocationSet = true;
            splitPane.setDividerLocation(0.5);
            return;
        }
        if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT && splitPane.getHeight() > 0) {
            initialDividerLocationSet = true;
            splitPane.setDividerLocation(getDefaultResizeWeight());
        }
    }

    double getDefaultResizeWeight() {
        if (Boolean.TRUE.equals(isEffectiveWebSocketProtocolSupplier.get())) {
            return 0.3;
        }
        return 0.5;
    }
}
