package com.laker.postman.service.collections;

import com.laker.postman.request.model.HttpRequestItem;


import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.constants.ConfigPathConstants;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@UtilityClass
public class OpenedRequestTabsStore {

    public static final String PATHNAME = ConfigPathConstants.OPENED_REQUESTS;

    public static List<HttpRequestItem> loadAll() {
        File file = new File(PATHNAME);
        if (!file.exists()) {
            return List.of();
        }
        try {
            String json = FileUtil.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return List.of();
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
            return result;
        } catch (Exception ex) {
            log.error("Failed to read opened requests", ex);
            return List.of();
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
            FileUtil.writeUtf8String(arr.toStringPretty(), file);
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
