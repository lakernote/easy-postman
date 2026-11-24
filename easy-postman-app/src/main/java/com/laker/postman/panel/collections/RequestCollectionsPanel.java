package com.laker.postman.panel.collections;

import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;

import javax.swing.*;
import java.awt.*;

/**
 * 请求集合工作区，左侧展示集合树，右侧展示请求编辑器。
 */
public class RequestCollectionsPanel extends UiSingletonPanel {
    private static final int DEFAULT_DIVIDER_LOCATION = AppToolWindowChrome.DEFAULT_SIDE_WIDTH;

    @Override
    protected void initUI() {
        ToolWindowSurfaceStyle.applyBackground(this);
        setLayout(new BorderLayout());
        add(createCollectionsSplitPane(), BorderLayout.CENTER);
    }

    private JSplitPane createCollectionsSplitPane() {
        CollectionTreePanel collectionTreePanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        RequestEditorPanel requestEditorPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);

        JSplitPane splitPane = createCollectionsSplitPane(collectionTreePanel, requestEditorPanel);
        return splitPane;
    }

    static JSplitPane createCollectionsSplitPane(Component collectionTreePanel, Component requestEditorPanel) {
        return AppToolWindowChrome.createHorizontalCardSplitPane(
                collectionTreePanel,
                requestEditorPanel,
                DEFAULT_DIVIDER_LOCATION
        );
    }

    static JComponent createCollectionToolWindow(Component collectionTreePanel) {
        return AppToolWindowChrome.wrapLeftInsetToolWindow(collectionTreePanel);
    }

    static JComponent createRequestEditorToolWindow(Component requestEditorPanel) {
        return AppToolWindowChrome.wrapRightToolWindow(requestEditorPanel);
    }

    @Override
    protected void registerListeners() {
        // 两侧面板都已注册且尚未首帧绘制：预读就绪时树和标签随主内容一起出现
        UiSingletonFactory.getInstance(CollectionTreePanel.class).startStartupLoad();
    }
}
