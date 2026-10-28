package com.laker.postman.plugin.capture;

import com.laker.postman.model.RequestImportBodyTypes;
import com.laker.postman.model.RequestImportDraft;
import com.laker.postman.model.RequestImportHeader;
import com.laker.postman.model.RequestImportProtocol;
import com.laker.postman.model.RequestImportResult;
import com.laker.postman.plugin.api.service.RequestCollectionImportService;
import com.laker.postman.common.component.notification.NotificationCenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.laker.postman.plugin.capture.CaptureI18n.t;

final class CaptureRequestCollectionImporter {

    private final RequestCollectionImportService importService;

    CaptureRequestCollectionImporter(RequestCollectionImportService importService) {
        this.importService = importService;
    }

    void importFlows(List<CaptureFlow> flows) {
        if (flows == null || flows.isEmpty()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_CAPTURE_IMPORT_EMPTY));
            return;
        }
        if (importService == null) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_CAPTURE_IMPORT_UNAVAILABLE));
            return;
        }

        RequestImportResult result = importService.importRequests(toDrafts(flows));
        if (result == null || result.status() == RequestImportResult.Status.UNAVAILABLE) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_CAPTURE_IMPORT_UNAVAILABLE));
            return;
        }
        if (result.isImported()) {
            NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_CAPTURE_IMPORT_SUCCESS, result.importedCount()));
        }
    }

    List<RequestImportDraft> toDrafts(List<CaptureFlow> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        List<RequestImportDraft> drafts = new ArrayList<>(flows.size());
        for (CaptureFlow flow : flows) {
            drafts.add(toDraft(flow, nextRequestName(flow, nameCounts)));
        }
        return drafts;
    }

    private RequestImportDraft toDraft(CaptureFlow flow, String requestName) {
        return new RequestImportDraft(
                "",
                requestName,
                flow.collectionRequestUrl(),
                flow.method(),
                resolveProtocol(flow),
                toHeaders(flow),
                buildDescription(flow),
                resolveBodyType(flow),
                resolveBody(flow)
        );
    }

    private List<RequestImportHeader> toHeaders(CaptureFlow flow) {
        List<RequestImportHeader> headers = new ArrayList<>();
        for (Map.Entry<String, String> entry : flow.requestHeadersSnapshot().entrySet()) {
            String key = entry.getKey();
            if (shouldSkipHeader(key, flow)) {
                continue;
            }
            headers.add(new RequestImportHeader(true, key, entry.getValue()));
        }
        return headers;
    }

    private boolean shouldSkipHeader(String key, CaptureFlow flow) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if ("host".equals(normalized)
                || "content-length".equals(normalized)
                || "proxy-connection".equals(normalized)
                || "transfer-encoding".equals(normalized)) {
            return true;
        }
        if (flow.isWebSocketProtocol()) {
            return "connection".equals(normalized)
                    || "upgrade".equals(normalized)
                    || normalized.startsWith("sec-websocket-");
        }
        return false;
    }

    private RequestImportProtocol resolveProtocol(CaptureFlow flow) {
        if (flow.isWebSocketProtocol()) {
            return RequestImportProtocol.WEBSOCKET;
        }
        return RequestImportProtocol.HTTP;
    }

    private String resolveBodyType(CaptureFlow flow) {
        if (flow.isWebSocketProtocol()) {
            return RequestImportBodyTypes.RAW;
        }
        return flow.requestBodyImportText().isBlank()
                ? RequestImportBodyTypes.NONE
                : RequestImportBodyTypes.RAW;
    }

    private String resolveBody(CaptureFlow flow) {
        return flow.isWebSocketProtocol() ? "" : flow.requestBodyImportText();
    }

    private String buildDescription(CaptureFlow flow) {
        StringBuilder builder = new StringBuilder();
        builder.append("Imported from Capture\n");
        builder.append("Captured At: ").append(flow.startedAtText()).append('\n');
        builder.append("Source URL: ").append(flow.collectionRequestUrl()).append('\n');
        if (flow.requestBodyPartial()) {
            builder.append("Note: request body preview was truncated during capture.\n");
        }
        return builder.toString().trim();
    }

    private String nextRequestName(CaptureFlow flow, Map<String, Integer> nameCounts) {
        String baseName = suggestedName(flow);
        int nextIndex = nameCounts.getOrDefault(baseName, 0) + 1;
        nameCounts.put(baseName, nextIndex);
        return nextIndex == 1 ? baseName : baseName + " " + nextIndex;
    }

    private String suggestedName(CaptureFlow flow) {
        String path = flow.path();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return flow.host();
        }
        return path.length() > 64 ? path.substring(0, 64) + "..." : path;
    }
}
