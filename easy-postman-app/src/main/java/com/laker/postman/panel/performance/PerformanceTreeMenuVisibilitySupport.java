package com.laker.postman.panel.performance;


import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

final class PerformanceTreeMenuVisibilitySupport {

    private final PerformanceTreeCommandPolicy commandPolicy;
    private final Supplier<List<DefaultMutableTreeNode>> copiedNodesSupplier;

    PerformanceTreeMenuVisibilitySupport(PerformanceTreeSupport treeSupport,
                                         Supplier<List<DefaultMutableTreeNode>> copiedNodesSupplier) {
        this.commandPolicy = new PerformanceTreeCommandPolicy(treeSupport);
        this.copiedNodesSupplier = copiedNodesSupplier;
    }

    void configureMultiSelectionMenu(TreePath[] selectedPaths, PerformanceTreeMenuItems items) {
        applyCommandVisibility(items, commandPolicy.commandsForMultiSelection(selectedPaths));
    }

    void configureSingleSelectionMenu(DefaultMutableTreeNode node, PerformanceTreeMenuItems items) {
        applyCommandVisibility(items, commandPolicy.commandsForSingleSelection(node, copiedNodesSupplier.get()));
    }

    private void applyCommandVisibility(PerformanceTreeMenuItems items, EnumSet<PerformanceTreeCommand> commands) {
        items.addThreadGroup().setVisible(commands.contains(PerformanceTreeCommand.ADD_THREAD_GROUP));
        items.addCsvDataSet().setVisible(commands.contains(PerformanceTreeCommand.ADD_CSV_DATA_SET));
        items.addRequest().setVisible(commands.contains(PerformanceTreeCommand.ADD_REQUEST));
        items.addLoop().setVisible(commands.contains(PerformanceTreeCommand.ADD_LOOP));
        items.addSimple().setVisible(commands.contains(PerformanceTreeCommand.ADD_SIMPLE));
        items.addCondition().setVisible(commands.contains(PerformanceTreeCommand.ADD_CONDITION));
        items.addWhile().setVisible(commands.contains(PerformanceTreeCommand.ADD_WHILE));
        items.addOnceOnly().setVisible(commands.contains(PerformanceTreeCommand.ADD_ONCE_ONLY));
        items.addSseConnect().setVisible(commands.contains(PerformanceTreeCommand.ADD_SSE_CONNECT));
        items.addSseRead().setVisible(commands.contains(PerformanceTreeCommand.ADD_SSE_READ));
        items.toggleSseMode().setVisible(commands.contains(PerformanceTreeCommand.TOGGLE_SSE_MODE));
        items.addWsConnect().setVisible(commands.contains(PerformanceTreeCommand.ADD_WS_CONNECT));
        items.addWsSend().setVisible(commands.contains(PerformanceTreeCommand.ADD_WS_SEND));
        items.addWsRead().setVisible(commands.contains(PerformanceTreeCommand.ADD_WS_READ));
        items.addWsClose().setVisible(commands.contains(PerformanceTreeCommand.ADD_WS_CLOSE));
        items.addAssertion().setVisible(commands.contains(PerformanceTreeCommand.ADD_ASSERTION));
        items.addExtractor().setVisible(commands.contains(PerformanceTreeCommand.ADD_EXTRACTOR));
        items.addTimer().setVisible(commands.contains(PerformanceTreeCommand.ADD_TIMER));
        items.enableNode().setVisible(commands.contains(PerformanceTreeCommand.ENABLE));
        items.disableNode().setVisible(commands.contains(PerformanceTreeCommand.DISABLE));
        items.copyNode().setVisible(commands.contains(PerformanceTreeCommand.COPY));
        items.pasteNode().setVisible(commands.contains(PerformanceTreeCommand.PASTE));
        items.renameNode().setVisible(commands.contains(PerformanceTreeCommand.RENAME));
        items.deleteNode().setVisible(commands.contains(PerformanceTreeCommand.DELETE));
    }
}
