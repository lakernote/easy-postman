package com.laker.postman.panel.performance;

import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.model.NodeType;


import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.panel.performance.assertion.AssertionPropertyPanel;
import com.laker.postman.panel.performance.config.CsvDataSetPropertyPanel;
import com.laker.postman.panel.performance.controller.ConditionPropertyPanel;
import com.laker.postman.panel.performance.controller.LoopPropertyPanel;
import com.laker.postman.panel.performance.controller.WhilePropertyPanel;
import com.laker.postman.panel.performance.extractor.ExtractorPropertyPanel;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.panel.performance.threadgroup.ThreadGroupPropertyPanel;
import com.laker.postman.panel.performance.timer.TimerPropertyPanel;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@RequiredArgsConstructor
final class PerformanceTreeInteractionSupport {

    private final Component parentComponent;
    private final JTree performanceTree;
    private final DefaultTreeModel treeModel;
    private final CardLayout propertyCardLayout;
    private final JPanel propertyPanel;
    private final ThreadGroupPropertyPanel threadGroupPanel;
    private final CsvDataSetPropertyPanel csvDataSetPanel;
    private final LoopPropertyPanel loopPanel;
    private final ConditionPropertyPanel conditionPanel;
    private final WhilePropertyPanel whilePanel;
    private final AssertionPropertyPanel assertionPanel;
    private final ExtractorPropertyPanel extractorPanel;
    private final TimerPropertyPanel timerPanel;
    private final SseStagePropertyPanel sseConnectPanel;
    private final SseStagePropertyPanel sseReadPanel;
    private final WebSocketStagePropertyPanel wsConnectPanel;
    private final WebSocketStagePropertyPanel wsSendPanel;
    private final WebSocketStagePropertyPanel wsReadPanel;
    private final WebSocketStagePropertyPanel wsClosePanel;
    private final PerformanceTreeSupport treeSupport;
    private final Consumer<DefaultMutableTreeNode> saveRequestNodeAction;
    private final Consumer<DefaultMutableTreeNode> saveSseStageAction;
    private final Consumer<DefaultMutableTreeNode> saveWebSocketStageAction;
    private final Consumer<HttpRequestItem> switchRequestEditorAction;
    private final BiConsumer<DefaultMutableTreeNode, PerformanceTreeNode> syncRequestStructureAction;
    private final Runnable saveConfigAction;
    private final Supplier<DefaultMutableTreeNode> currentRequestNodeSupplier;
    private final Consumer<DefaultMutableTreeNode> currentRequestNodeSetter;
    private final String emptyCard;
    private final String threadGroupCard;
    private final String csvDataSetCard;
    private final String loopCard;
    private final String simpleCard;
    private final String conditionCard;
    private final String whileCard;
    private final String onceOnlyCard;
    private final String requestCard;
    private final String assertionCard;
    private final String extractorCard;
    private final String timerCard;
    private final String sseConnectCard;
    private final String sseReadCard;
    private final String wsConnectCard;
    private final String wsSendCard;
    private final String wsReadCard;
    private final String wsCloseCard;

    private PerformanceTreeSelectionSupport selectionSupport;
    private PerformanceTreeNodeCommandSupport nodeCommandSupport;

    void install() {
        selectionSupport = createSelectionSupport();
        nodeCommandSupport = createNodeCommandSupport();
        selectionSupport.install();
        installPopupMenu();
    }

    private void persistLastSelection() {
        selectionSupport.persistLastSelection();
    }

    private PerformanceTreeSelectionSupport createSelectionSupport() {
        return new PerformanceTreeSelectionSupport(
                performanceTree,
                treeModel,
                propertyCardLayout,
                propertyPanel,
                threadGroupPanel,
                csvDataSetPanel,
                loopPanel,
                conditionPanel,
                whilePanel,
                assertionPanel,
                extractorPanel,
                timerPanel,
                sseConnectPanel,
                sseReadPanel,
                wsConnectPanel,
                wsSendPanel,
                wsReadPanel,
                wsClosePanel,
                treeSupport,
                saveRequestNodeAction,
                saveSseStageAction,
                saveWebSocketStageAction,
                switchRequestEditorAction,
                syncRequestStructureAction,
                currentRequestNodeSetter,
                emptyCard,
                threadGroupCard,
                csvDataSetCard,
                loopCard,
                simpleCard,
                conditionCard,
                whileCard,
                onceOnlyCard,
                requestCard,
                assertionCard,
                extractorCard,
                timerCard,
                sseConnectCard,
                sseReadCard,
                wsConnectCard,
                wsSendCard,
                wsReadCard,
                wsCloseCard
        );
    }

    private PerformanceTreeNodeCommandSupport createNodeCommandSupport() {
        return new PerformanceTreeNodeCommandSupport(
                parentComponent,
                performanceTree,
                treeModel,
                propertyCardLayout,
                propertyPanel,
                treeSupport,
                this::persistLastSelection,
                switchRequestEditorAction,
                currentRequestNodeSupplier,
                currentRequestNodeSetter,
                saveConfigAction,
                requestCard
        );
    }

    private void installPopupMenu() {
        JPopupMenu treeMenu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(treeMenu);
        JMenuItem addThreadGroup = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_THREAD_GROUP));
        JMenuItem addCsvDataSet = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_CSV_DATA_SET));
        JMenuItem addRequest = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_REQUEST));
        JMenuItem addSseConnect = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_SSE_CONNECT));
        JMenuItem addSseRead = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_SSE_READ));
        JCheckBoxMenuItem sseMode = new JCheckBoxMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_TOGGLE_SSE_MODE));
        JMenuItem addWsConnect = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_WS_CONNECT));
        JMenuItem addWsSend = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_WS_SEND));
        JMenuItem addWsRead = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_WS_READ));
        JMenuItem addWsClose = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_WS_CLOSE));
        JMenuItem addLoop = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_LOOP));
        JMenuItem addSimple = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_SIMPLE));
        JMenuItem addCondition = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_CONDITION));
        JMenuItem addWhile = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_WHILE));
        JMenuItem addOnceOnly = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_ONCE_ONLY));
        JMenuItem addAssertion = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_ASSERTION));
        JMenuItem addExtractor = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_EXTRACTOR));
        JMenuItem addTimer = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ADD_TIMER));
        JMenuItem renameNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_RENAME));
        JMenuItem deleteNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_DELETE));
        JMenuItem enableNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_ENABLE));
        JMenuItem disableNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_DISABLE));
        JMenuItem copyNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_COPY));
        JMenuItem pasteNode = new JMenuItem(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_PASTE));
        JMenu addDataMenu = new JMenu(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_GROUP_REQUEST_DATA));
        JMenu addControllerMenu = new JMenu(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_GROUP_CONTROLLERS));
        JMenu addProtocolMenu = new JMenu(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_GROUP_PROTOCOL_STEPS));
        JMenu addCheckTimingMenu = new JMenu(I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_GROUP_CHECK_TIMING));
        PerformanceTreeMenuItems menuItems = new PerformanceTreeMenuItems(
                addThreadGroup,
                addCsvDataSet,
                addRequest,
                addLoop,
                addSimple,
                addCondition,
                addWhile,
                addOnceOnly,
                addSseConnect,
                addSseRead,
                sseMode,
                addWsConnect,
                addWsSend,
                addWsRead,
                addWsClose,
                addAssertion,
                addExtractor,
                addTimer,
                enableNode,
                disableNode,
                copyNode,
                pasteNode,
                renameNode,
                deleteNode
        );
        PerformanceTreeMenuVisibilitySupport menuVisibilitySupport = new PerformanceTreeMenuVisibilitySupport(
                treeSupport,
                nodeCommandSupport::copiedNodes
        );
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        renameNode.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
        deleteNode.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0));
        copyNode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask));
        pasteNode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask));

        JSeparator separator1 = new JSeparator();
        JSeparator separator2 = new JSeparator();
        JSeparator separator3 = new JSeparator();
        JSeparator protocolMenuSeparator = new JSeparator();

        addDataMenu.add(addThreadGroup);
        addDataMenu.add(addCsvDataSet);
        addDataMenu.add(addRequest);
        addControllerMenu.add(addSimple);
        addControllerMenu.add(addLoop);
        addControllerMenu.add(addCondition);
        addControllerMenu.add(addWhile);
        addControllerMenu.add(addOnceOnly);
        addProtocolMenu.add(addSseConnect);
        addProtocolMenu.add(addSseRead);
        addProtocolMenu.add(sseMode);
        addProtocolMenu.add(protocolMenuSeparator);
        addProtocolMenu.add(addWsConnect);
        addProtocolMenu.add(addWsSend);
        addProtocolMenu.add(addWsRead);
        addProtocolMenu.add(addWsClose);
        addCheckTimingMenu.add(addAssertion);
        addCheckTimingMenu.add(addExtractor);
        addCheckTimingMenu.add(addTimer);

        treeMenu.add(addDataMenu);
        treeMenu.add(addControllerMenu);
        treeMenu.add(addProtocolMenu);
        treeMenu.add(addCheckTimingMenu);
        treeMenu.add(separator1);
        treeMenu.add(enableNode);
        treeMenu.add(disableNode);
        treeMenu.add(separator2);
        treeMenu.add(copyNode);
        treeMenu.add(pasteNode);
        treeMenu.add(separator3);
        treeMenu.add(renameNode);
        treeMenu.add(deleteNode);

        Runnable updateMenuSeparators = () -> {
            syncAddMenuVisibility(addDataMenu, addThreadGroup, addCsvDataSet, addRequest);
            syncAddMenuVisibility(addControllerMenu, addSimple, addLoop, addCondition, addWhile, addOnceOnly);
            boolean hasSseItems = addSseConnect.isVisible() || addSseRead.isVisible() || sseMode.isVisible();
            boolean hasWebSocketItems = addWsConnect.isVisible()
                    || addWsSend.isVisible()
                    || addWsRead.isVisible()
                    || addWsClose.isVisible();
            protocolMenuSeparator.setVisible(hasSseItems && hasWebSocketItems);
            syncAddMenuVisibility(
                    addProtocolMenu,
                    addSseConnect,
                    addSseRead,
                sseMode,
                    addWsConnect,
                    addWsSend,
                    addWsRead,
                    addWsClose
            );
            syncAddMenuVisibility(addCheckTimingMenu, addAssertion, addExtractor, addTimer);
            boolean hasAddGroup = addDataMenu.isVisible()
                    || addControllerMenu.isVisible()
                    || addProtocolMenu.isVisible()
                    || addCheckTimingMenu.isVisible();
            boolean hasToggleGroup = menuItems.hasVisibleToggleItem();
            boolean hasClipboardGroup = menuItems.hasVisibleClipboardItem();
            boolean hasEditGroup = menuItems.hasVisibleEditItem();
            separator1.setVisible(hasAddGroup && (hasToggleGroup || hasClipboardGroup || hasEditGroup));
            separator2.setVisible(hasToggleGroup && (hasClipboardGroup || hasEditGroup));
            separator3.setVisible(hasClipboardGroup && hasEditGroup);
        };

        addThreadGroup.addActionListener(e -> nodeCommandSupport.addThreadGroupNode());
        addCsvDataSet.addActionListener(e -> treeSupport.addCsvDataSetNode(performanceTree, saveConfigAction));
        addRequest.addActionListener(e -> nodeCommandSupport.addRequestNodes());
        addLoop.addActionListener(e -> treeSupport.addLoopNode(performanceTree, saveConfigAction));
        addSimple.addActionListener(e -> treeSupport.addSimpleNode(performanceTree, saveConfigAction));
        addCondition.addActionListener(e -> treeSupport.addConditionNode(performanceTree, saveConfigAction));
        addWhile.addActionListener(e -> treeSupport.addWhileNode(performanceTree, saveConfigAction));
        addOnceOnly.addActionListener(e -> treeSupport.addOnceOnlyNode(performanceTree, saveConfigAction));
        addSseConnect.addActionListener(e -> treeSupport.addSseStageNode(performanceTree, NodeType.SSE_CONNECT, saveConfigAction));
        addSseRead.addActionListener(e -> treeSupport.addSseStageNode(performanceTree, NodeType.SSE_READ, saveConfigAction));
        sseMode.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
            treeSupport.setSsePerformanceMode(selectedNode, sseMode.isSelected());
            if (selectedNode != null) {
                treeModel.nodeChanged(selectedNode);
            }
            saveConfigAction.run();
        });
        addWsConnect.addActionListener(e -> treeSupport.addWebSocketStepNode(performanceTree, NodeType.WS_CONNECT, saveConfigAction));
        addWsSend.addActionListener(e -> treeSupport.addWebSocketStepNode(performanceTree, NodeType.WS_SEND, saveConfigAction));
        addWsRead.addActionListener(e -> treeSupport.addWebSocketStepNode(performanceTree, NodeType.WS_READ, saveConfigAction));
        addWsClose.addActionListener(e -> treeSupport.addWebSocketStepNode(performanceTree, NodeType.WS_CLOSE, saveConfigAction));
        addAssertion.addActionListener(e -> nodeCommandSupport.addAssertionNode());
        addExtractor.addActionListener(e -> nodeCommandSupport.addExtractorNode());
        addTimer.addActionListener(e -> treeSupport.addTimerNode(performanceTree, saveConfigAction));

        Action renameAction = nodeCommandSupport.createRenameAction();
        Action deleteAction = nodeCommandSupport.createDeleteAction();
        Action copyAction = nodeCommandSupport.createCopyAction();
        Action pasteAction = nodeCommandSupport.createPasteAction();
        renameNode.addActionListener(renameAction);
        deleteNode.addActionListener(deleteAction);
        copyNode.addActionListener(copyAction);
        pasteNode.addActionListener(pasteAction);

        InputMap treeInputMap = performanceTree.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap treeActionMap = performanceTree.getActionMap();
        treeInputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0), "renamePerformanceNode");
        treeActionMap.put("renamePerformanceNode", renameAction);
        treeInputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "deletePerformanceNode");
        treeInputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), "deletePerformanceNode");
        treeActionMap.put("deletePerformanceNode", deleteAction);
        treeInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask), "copyPerformanceNode");
        treeActionMap.put("copyPerformanceNode", copyAction);
        treeInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask), "pastePerformanceNode");
        treeActionMap.put("pastePerformanceNode", pasteAction);

        enableNode.addActionListener(e -> nodeCommandSupport.setSelectedNodesEnabled(true));
        disableNode.addActionListener(e -> nodeCommandSupport.setSelectedNodesEnabled(false));

        performanceTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                int row = performanceTree.getClosestRowForLocation(e.getX(), e.getY());
                if (row < 0) {
                    return;
                }

                TreePath clickedPath = performanceTree.getPathForLocation(e.getX(), e.getY());
                alignSelectionForPopup(clickedPath);

                DefaultMutableTreeNode currentRequestNode = currentRequestNodeSupplier.get();
                if (currentRequestNode != null) {
                    saveRequestNodeAction.accept(currentRequestNode);
                }

                TreePath[] selectedPaths = performanceTree.getSelectionPaths();
                if (selectedPaths == null || selectedPaths.length == 0) {
                    return;
                }

                if (selectedPaths.length > 1) {
                    menuVisibilitySupport.configureMultiSelectionMenu(selectedPaths, menuItems);
                } else {
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPaths[0].getLastPathComponent();
                    menuVisibilitySupport.configureSingleSelectionMenu(selectedNode, menuItems);
                    sseMode.setSelected(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData
                            && nodeData.ssePerformanceEnabled);
                }
                updateMenuSeparators.run();
                treeMenu.show(performanceTree, e.getX(), e.getY());
            }
        });
    }

    private void syncAddMenuVisibility(JMenu menu, JMenuItem... items) {
        boolean visible = false;
        for (JMenuItem item : items) {
            visible = visible || item.isVisible();
        }
        menu.setVisible(visible);
    }

    private void alignSelectionForPopup(TreePath clickedPath) {
        if (clickedPath == null) {
            return;
        }
        TreePath[] selectedPaths = performanceTree.getSelectionPaths();
        boolean isInSelection = false;
        if (selectedPaths != null) {
            for (TreePath path : selectedPaths) {
                if (path.equals(clickedPath)) {
                    isInSelection = true;
                    break;
                }
            }
        }
        if (!isInSelection) {
            performanceTree.setSelectionPath(clickedPath);
        }
    }

}
