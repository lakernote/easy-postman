package com.laker.postman.service.collections;

import com.laker.postman.request.model.HttpRequestItem;


import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.constants.ConfigPathConstants;
import lombok.experimental.UtilityClass;
import com.laker.postman.util.SafeFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@UtilityClass
public class OpenedRequestTabsStore {

    public static final String PATHNAME = ConfigPathConstants.OPENED_REQUESTS;

    public static List<HttpRequestItem> loadAll() {
        return tryLoadAll().orElse(List.of());
    }

    /**
     * @return 文件不存在或为空时返回空列表；读取或解析失败时返回 {@link Optional#empty()}，供调用方区分“没有会话”和“会话不可读”
     */
    public static Optional<List<HttpRequestItem>> tryLoadAll() {
        File file = new File(PATHNAME);
        if (!file.exists()) {
            return Optional.of(List.of());
        }
        try {
            String json = FileUtil.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return Optional.of(List.of());
            }
            JSONArray arr = JSONUtil.parseArray(json);
            List<HttpRequestItem> result = new ArrayList<>();

            for (Object obj : arr) {
                if (obj instanceof JSONObject jsonObj) {
                    RequestProtocolStorageMigration.normalizeLegacySseProtocol(jsonObj);
                    HttpRequestItem item = jsonObj.toBean(HttpRequestItem.class);
                    result.add(item);
                }
            }
            return Optional.of(result);
        } catch (Exception ex) {
            log.error("Failed to read opened requests", ex);
            // 退出时可能用当前标签覆盖该文件，先保留一份原内容
            SafeFileUtil.backupUnreadable(file.toPath());
            return Optional.empty();
        }
    }

    public static void saveAll(List<HttpRequestItem> openedRequests) {
        if (openedRequests == null || openedRequests.isEmpty()) {
            clear();
            return;
        }

        try {
            File file = new File(PATHNAME);
            JSONArray arr = new JSONArray();
            for (HttpRequestItem item : openedRequests) {
                if (item.isNewRequest()) {
                    arr.add(JSONUtil.parse(item));
                } else {
                    JSONObject existRequest = new JSONObject();
                    existRequest.set("id", item.getId());
                    existRequest.set("name", item.getName());
                    arr.add(existRequest);
                }
            }
            // 原子替换：退出时被强制结束也不会留下半个文件
            SafeFileUtil.writeStringAtomically(file.toPath(), arr.toStringPretty());
            log.info("Saved {} opened requests to {}", openedRequests.size(), file.getAbsolutePath());
        } catch (Exception ex) {
            log.error("Failed to save opened requests on exit", ex);
        }
    }

    public static void clear() {
        File file = new File(PATHNAME);
        if (file.exists()) {
            try {
                FileUtil.del(file);
                log.info("Cleared opened requests file at {}", file.getAbsolutePath());
            } catch (Exception ex) {
                log.error("Failed to delete opened requests file", ex);
            }
        }
    }
}
