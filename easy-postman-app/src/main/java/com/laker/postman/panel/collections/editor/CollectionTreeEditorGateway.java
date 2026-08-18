package com.laker.postman.panel.collections.editor;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * 请求编辑器访问集合树的 Swing 网关。
 * <p>
 * 核心点：编辑器控制器不直接寻找 CollectionTreePanel，后续如果集合树实现替换，
 * 只需要替换这个网关，而不是在保存、打开、分组编辑控制器里到处改。
 */
public final class CollectionTreeEditorGateway {

    public TreeModel groupTreeModel() {
        return collectionTreePanel().getGroupTreeModel();
    }

    public void saveRequestToGroup(RequestGroup group, HttpRequestItem item) {
        collectionTreePanel().saveRequestToGroup(group, item);
    }

    public boolean updateExistingRequest(HttpRequestItem item) {
        return collectionTreePanel().updateExistingRequest(item);
    }

    public void saveGroupNode(DefaultMutableTreeNode groupNode) {
        CollectionTreePanel collectionTreePanel = collectionTreePanel();
        collectionTreePanel.getTreeModel().nodeChanged(groupNode);
        collectionTreePanel.getCollectionTreePersistence().saveCurrentTree();
    }

    public boolean saveResponseForRequest(HttpRequestItem requestItem, SavedResponse savedResponse) {
        return collectionTreePanel().saveResponseForRequest(requestItem, savedResponse);
    }

    public boolean upsertResponseForRequest(String requestId, SavedResponse savedResponse) {
        return collectionTreePanel().upsertResponseForRequest(requestId, savedResponse);
    }

    public boolean deleteResponseForRequest(String requestId, String responseId) {
        return collectionTreePanel().deleteResponseForRequest(requestId, responseId);
    }

    private CollectionTreePanel collectionTreePanel() {
        return UiSingletonFactory.getInstance(CollectionTreePanel.class);
    }
}
