package com.laker.postman.panel.performance;


import javax.swing.JMenuItem;

record PerformanceTreeMenuItems(
        JMenuItem addThreadGroup,
        JMenuItem addCsvDataSet,
        JMenuItem addRequest,
        JMenuItem addLoop,
        JMenuItem addSimple,
        JMenuItem addCondition,
        JMenuItem addWhile,
        JMenuItem addOnceOnly,
        JMenuItem addSseConnect,
        JMenuItem addSseRead,
        JMenuItem toggleSseMode,
        JMenuItem addWsConnect,
        JMenuItem addWsSend,
        JMenuItem addWsRead,
        JMenuItem addWsClose,
        JMenuItem addAssertion,
        JMenuItem addExtractor,
        JMenuItem addTimer,
        JMenuItem enableNode,
        JMenuItem disableNode,
        JMenuItem copyNode,
        JMenuItem pasteNode,
        JMenuItem renameNode,
        JMenuItem deleteNode
) {

    boolean hasVisibleAddItem() {
        return addThreadGroup.isVisible() || addCsvDataSet.isVisible() || addRequest.isVisible()
                || addLoop.isVisible()
                || addSimple.isVisible()
                || addCondition.isVisible()
                || addWhile.isVisible()
                || addOnceOnly.isVisible()
                || addSseConnect.isVisible() || addSseRead.isVisible() || toggleSseMode.isVisible()
                || addWsConnect.isVisible()
                || addWsSend.isVisible() || addWsRead.isVisible() || addWsClose.isVisible()
                || addAssertion.isVisible() || addExtractor.isVisible() || addTimer.isVisible();
    }

    boolean hasVisibleToggleItem() {
        return enableNode.isVisible() || disableNode.isVisible();
    }

    boolean hasVisibleClipboardItem() {
        return copyNode.isVisible() || pasteNode.isVisible();
    }

    boolean hasVisibleEditItem() {
        return renameNode.isVisible() || deleteNode.isVisible();
    }
}
