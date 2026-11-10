package com.laker.postman.collection;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.util.JsonUtil;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 保证集合内分组、请求、Example（SavedResponse）的 ID 唯一。
 * <p>
 * 编辑器 Tab 按 ID 查找已打开的请求/Example，ID 重复会导致点击后停留在另一个 Tab（issue #194）。
 * 历史版本的 Postman 导入曾把 {@code _postman_previewlanguage}（如 json）当作 Example ID，
 * 重复导入同一份 EasyPostman JSON 也会带来重复的请求/Example ID。
 */
@UtilityClass
public class CollectionIdNormalizer {

    /**
     * 集合中已占用的 ID，按节点类型分开记录。
     */
    public record UsedIds(Set<String> groups, Set<String> requests, Set<String> examples) {
        public static UsedIds empty() {
            return new UsedIds(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }

        private UsedIds copy() {
            return new UsedIds(new HashSet<>(groups), new HashSet<>(requests), new HashSet<>(examples));
        }
    }

    /**
     * 收集文档中所有非空白 ID。
     */
    public static UsedIds collectIds(CollectionDocument document) {
        UsedIds ids = UsedIds.empty();
        forEachNode(document, node -> {
            if (node.isGroup()) {
                addIfPresent(ids.groups(), node.asGroup().getId());
            } else if (node.isRequest()) {
                HttpRequestItem request = node.asRequest();
                addIfPresent(ids.requests(), request.getId());
                for (SavedResponse example : examples(request)) {
                    addIfPresent(ids.examples(), example.getId());
                }
            }
        });
        return ids;
    }

    /**
     * 为空白或重复的 ID 重新分配 UUID，首次出现的 ID 保持不变。
     *
     * @return 被重新分配 ID 的节点数量
     */
    public static int ensureUniqueIds(CollectionDocument document) {
        return ensureUniqueIds(document, UsedIds.empty());
    }

    /**
     * 为空白、重复或与 {@code reserved} 冲突的 ID 重新分配 UUID，用于导入到已有集合时避免跨文档冲突。
     *
     * @return 被重新分配 ID 的节点数量
     */
    public static int ensureUniqueIds(CollectionDocument document, UsedIds reserved) {
        UsedIds seen = reserved == null ? UsedIds.empty() : reserved.copy();
        int[] reassigned = {0};
        forEachNode(document, node -> {
            if (node.isGroup()) {
                RequestGroup group = node.asGroup();
                String newId = uniqueId(group.getId(), seen.groups());
                if (newId != null) {
                    group.setId(newId);
                    reassigned[0]++;
                }
            } else if (node.isRequest()) {
                HttpRequestItem request = node.asRequest();
                String newId = uniqueId(request.getId(), seen.requests());
                if (newId != null) {
                    request.setId(newId);
                    reassigned[0]++;
                }
                for (SavedResponse example : examples(request)) {
                    String newExampleId = uniqueId(example.getId(), seen.examples());
                    if (newExampleId != null) {
                        example.setId(newExampleId);
                        reassigned[0]++;
                    }
                }
            }
        });
        return reassigned[0];
    }

    /**
     * 深拷贝请求，并为请求及其 Example 分配新 ID，避免与源请求冲突。
     */
    public static HttpRequestItem copyRequestWithNewIds(HttpRequestItem source) {
        HttpRequestItem copy = JsonUtil.deepCopy(source, HttpRequestItem.class);
        copy.setId(newId());
        for (SavedResponse example : examples(copy)) {
            example.setId(newId());
        }
        return copy;
    }

    /**
     * 当前 ID 可用时登记并返回 null，否则登记并返回一个新的 UUID。
     */
    private static String uniqueId(String id, Set<String> seen) {
        if (id != null && !id.isBlank() && seen.add(id)) {
            return null;
        }
        String newId = newId();
        seen.add(newId);
        return newId;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (id != null && !id.isBlank()) {
            ids.add(id);
        }
    }

    private static List<SavedResponse> examples(HttpRequestItem request) {
        if (request.getResponse() == null) {
            return List.of();
        }
        return request.getResponse().stream().filter(Objects::nonNull).toList();
    }

    private static void forEachNode(CollectionDocument document, Consumer<CollectionNode> visitor) {
        if (document == null) {
            return;
        }
        for (CollectionNode root : document.getRoots()) {
            visit(root, visitor);
        }
    }

    private static void visit(CollectionNode node, Consumer<CollectionNode> visitor) {
        if (node == null) {
            return;
        }
        if ((node.isGroup() && node.asGroup() == null) || (node.isRequest() && node.asRequest() == null)) {
            return;
        }
        visitor.accept(node);
        for (CollectionNode child : node.getChildren()) {
            visit(child, visitor);
        }
    }
}
