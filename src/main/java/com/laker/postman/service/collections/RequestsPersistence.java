package com.laker.postman.service.collections;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.HttpRequestItem;
import lombok.extern.slf4j.Slf4j;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class RequestsPersistence {
    private Path filePath;
    private final DefaultMutableTreeNode rootTreeNode;
    private final DefaultTreeModel treeModel;

    public RequestsPersistence(Path filePath, DefaultMutableTreeNode rootTreeNode, DefaultTreeModel treeModel) {
        this.filePath = filePath;
        this.rootTreeNode = rootTreeNode;
        this.treeModel = treeModel;
    }

    public void exportRequestCollection(File fileToSave) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(fileToSave), StandardCharsets.UTF_8)) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) rootTreeNode.getChildAt(i);
                array.add(buildGroupJson(groupNode));
            }
            writer.write(array.toStringPretty());
        }
    }

    public void initRequestGroupsFromFile() {
        File file = filePath.toFile();
        if (!file.exists()) { // 如果文件不存在，则创建默认请求组
            RequestsFactory.createDefaultRequestGroups(rootTreeNode, treeModel); // 创建默认请求组
            saveRequestGroups(); // 保存默认请求组到文件
            log.info("File not found, created default request groups.");
            return;
        }
        try {

            JSONArray array = JSONUtil.readJSONArray(file, StandardCharsets.UTF_8);
            List<DefaultMutableTreeNode> groupNodeList = new ArrayList<>();
            for (Object o : array) {
                JSONObject groupJson = (JSONObject) o;
                DefaultMutableTreeNode groupNode = parseGroupNode(groupJson);
                groupNodeList.add(groupNode);
            }
            groupNodeList.forEach(rootTreeNode::add);
            treeModel.reload(rootTreeNode);
            log.info("Loaded request groups from file: {}", filePath);
        } catch (Exception e) {
            log.error("Error loading request groups from file: {}", filePath, e);
        }
    }

    public void saveRequestGroups() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(filePath.toFile()), StandardCharsets.UTF_8)) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < rootTreeNode.getChildCount(); i++) {
                DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) rootTreeNode.getChildAt(i);
                array.add(buildGroupJson(groupNode));
            }
            writer.write(array.toStringPretty());
            log.debug("Saved request groups to: {}", filePath);
        } catch (Exception ex) {
            log.error("Error saving request groups to file: {}", filePath, ex);
        }
    }

    public DefaultMutableTreeNode parseGroupNode(JSONObject groupJson) {
        String name = groupJson.getStr("name");
        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new Object[]{"group", name});
        JSONArray children = groupJson.getJSONArray("children");
        if (children != null) {
            for (Object child : children) {
                JSONObject childJson = (JSONObject) child;
                String type = childJson.getStr("type");
                if ("group".equals(type)) {
                    groupNode.add(parseGroupNode(childJson));
                } else if ("request".equals(type)) {
                    JSONObject dataJson = childJson.getJSONObject("data");
                    HttpRequestItem item = JSONUtil.toBean(dataJson, HttpRequestItem.class);
                    // 确保请求体不为 null
                    item.setBody(item.getBody() != null ? item.getBody() : "");
                    if (item.getId() == null || item.getId().isEmpty()) {
                        String id = dataJson.getStr("id");
                        if (id == null || id.isEmpty()) {
                            item.setId(UUID.randomUUID().toString());
                        } else {
                            item.setId(id);
                        }
                    }
                    groupNode.add(new DefaultMutableTreeNode(new Object[]{"request", item}));
                }
            }
        }
        return groupNode;
    }

    public JSONObject buildGroupJson(DefaultMutableTreeNode node) {
        JSONObject groupJson = new JSONObject();
        Object[] obj = (Object[]) node.getUserObject();
        groupJson.set("type", "group");
        groupJson.set("name", obj[1]);
        JSONArray children = new JSONArray();
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object[] childObj = (Object[]) child.getUserObject();
            if ("group".equals(childObj[0])) {
                children.add(buildGroupJson(child));
            } else if ("request".equals(childObj[0])) {
                JSONObject reqJson = new JSONObject();
                reqJson.set("type", "request");
                HttpRequestItem requestItem = (HttpRequestItem) childObj[1];
                JSONObject itemJson = JSONUtil.parseObj(requestItem);
                reqJson.set("data", itemJson);
                children.add(reqJson);
            }
        }
        groupJson.set("children", children);
        return groupJson;
    }

    /**
     * 切换数据文件路径并重新加载集合
     */
    public void setDataFilePath(Path path) {
        if (path == null) return;
        this.filePath = path;
        // 清空现有树
        rootTreeNode.removeAllChildren();
        treeModel.reload(rootTreeNode);
        // 重新加载
        initRequestGroupsFromFile();
    }
}