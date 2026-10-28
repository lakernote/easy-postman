package com.laker.postman.service.collections;

import cn.hutool.json.JSONObject;
import lombok.experimental.UtilityClass;

/** Converts legacy stored request values to the current request model while reading persisted data. */
@UtilityClass
public class RequestProtocolStorageMigration {

    public void normalizeLegacySseProtocol(JSONObject requestJson) {
        if (requestJson == null) {
            return;
        }
        String protocol = requestJson.getStr("protocol");
        if ("SSE".equalsIgnoreCase(protocol)) {
            requestJson.set("protocol", "HTTP");
        }
    }
}
