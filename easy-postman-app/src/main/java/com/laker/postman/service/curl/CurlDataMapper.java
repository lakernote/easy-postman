package com.laker.postman.service.curl;

import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.util.HttpUrlUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
class CurlDataMapper {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    static void apply(CurlRequest req, CurlCommandOptions options) {
        if (!options.hasDataParams()) {
            return;
        }

        if (options.forceGet) {
            req.method = "GET";
            if (req.paramsList == null) {
                req.paramsList = new ArrayList<>();
            }
            addDataParamsAsQueryParams(req.paramsList, options.dataParams);
            addDataUrlencodeParamsAsQueryParams(req.paramsList, options.dataUrlencodeParams);
            return;
        }

        if (options.binaryDataFilePath != null && !options.binaryDataFilePath.isBlank()) {
            req.binaryBody = true;
            req.body = options.binaryDataFilePath;
            return;
        }

        String contentType = CurlHeaderSupport.findEnabledHeaderValue(req.headersList, CONTENT_TYPE);
        if (contentType == null || contentType.isEmpty()) {
            if (allDataParamsAreKeyValue(options)) {
                contentType = FORM_URLENCODED;
                addHeader(req, CONTENT_TYPE, contentType);
            } else {
                contentType = "";
            }
        }

        if (contentType.startsWith(FORM_URLENCODED)) {
            applyUrlencodedBody(req, options);
        } else if (contentType.startsWith("multipart/form-data")) {
            req.body = combineDataParams(options.dataParams, options.dataUrlencodeParams);
            CurlMultipartBodyParser.parseInto(req, req.body);
        } else {
            req.body = combineDataParams(options.dataParams, options.dataUrlencodeParams);
        }
    }

    private static boolean allDataParamsAreKeyValue(CurlCommandOptions options) {
        for (String dataParam : options.dataParams) {
            if (!dataParam.contains("=")) {
                return false;
            }
        }
        for (String dataParam : options.dataUrlencodeParams) {
            if (!dataParam.contains("=")) {
                return false;
            }
        }
        return true;
    }

    private static void applyUrlencodedBody(CurlRequest req, CurlCommandOptions options) {
        String combinedData = combineDataParams(options.dataParams, options.dataUrlencodeParams);
        if (!combinedData.contains("=")) {
            req.body = combinedData;
            return;
        }

        if (req.urlencodedList == null) {
            req.urlencodedList = new ArrayList<>();
        }
        boolean hasValidPairs = addDataParamsAsUrlencoded(req.urlencodedList, options.dataParams);
        hasValidPairs = addDataUrlencodeParamsAsUrlencoded(req.urlencodedList, options.dataUrlencodeParams) || hasValidPairs;
        if (!hasValidPairs) {
            req.urlencodedList = null;
            req.body = combinedData;
        }
    }

    private static void addHeader(CurlRequest req, String key, String value) {
        if (req.headersList == null) {
            req.headersList = new ArrayList<>();
        }
        req.headersList.add(new HttpHeader(true, key, value));
    }

    private static void addDataParamsAsQueryParams(List<HttpParam> paramsList, List<String> dataParams) {
        for (String dataParam : dataParams) {
            for (String pair : splitAmpersandPairs(dataParam)) {
                addQueryParamPair(paramsList, pair, true);
            }
        }
    }

    private static void addDataUrlencodeParamsAsQueryParams(List<HttpParam> paramsList, List<String> dataUrlencodeParams) {
        for (String dataParam : dataUrlencodeParams) {
            addQueryParamPair(paramsList, dataParam, false);
        }
    }

    private static void addQueryParamPair(List<HttpParam> paramsList, String pair, boolean decodeValue) {
        if (pair == null || pair.trim().isEmpty()) {
            return;
        }
        String[] kv = pair.split("=", 2);
        if (kv.length == 2) {
            paramsList.add(new HttpParam(true,
                    HttpUrlUtil.decodeComponent(kv[0].trim()),
                    decodeValue ? HttpUrlUtil.decodeComponent(kv[1].trim()) : kv[1]));
        } else if (kv.length == 1) {
            paramsList.add(new HttpParam(true, HttpUrlUtil.decodeComponent(kv[0].trim()), ""));
        }
    }

    private static boolean addDataParamsAsUrlencoded(List<HttpFormUrlencoded> urlencodedList, List<String> dataParams) {
        boolean hasValidPairs = false;
        for (String dataParam : dataParams) {
            for (String pair : splitAmpersandPairs(dataParam)) {
                hasValidPairs = addUrlencodedPair(urlencodedList, pair, true) || hasValidPairs;
            }
        }
        return hasValidPairs;
    }

    private static boolean addDataUrlencodeParamsAsUrlencoded(
            List<HttpFormUrlencoded> urlencodedList,
            List<String> dataUrlencodeParams
    ) {
        boolean hasValidPairs = false;
        for (String dataParam : dataUrlencodeParams) {
            hasValidPairs = addUrlencodedPair(urlencodedList, dataParam, false) || hasValidPairs;
        }
        return hasValidPairs;
    }

    private static boolean addUrlencodedPair(
            List<HttpFormUrlencoded> urlencodedList,
            String pair,
            boolean decodeValue
    ) {
        if (pair == null || pair.trim().isEmpty()) {
            return false;
        }
        String[] kv = pair.split("=", 2);
        if (kv.length == 2) {
            urlencodedList.add(new HttpFormUrlencoded(true,
                    decodeFormComponent(kv[0].trim()),
                    decodeValue ? decodeFormComponent(kv[1].trim()) : kv[1]));
            return true;
        }
        if (kv.length == 1 && !kv[0].trim().isEmpty()) {
            urlencodedList.add(new HttpFormUrlencoded(true, decodeFormComponent(kv[0].trim()), ""));
            return true;
        }
        return false;
    }

    private static String[] splitAmpersandPairs(String dataParam) {
        if (dataParam == null) {
            return new String[0];
        }
        return dataParam.split("&");
    }

    private static String combineDataParams(List<String> dataParams, List<String> dataUrlencodeParams) {
        List<String> combined = new ArrayList<>();
        combined.addAll(dataParams);
        combined.addAll(dataUrlencodeParams);
        return String.join("&", combined);
    }

    /**
     * Decodes one key or value of form-urlencoded data, where {@code +} stands for a space.
     */
    private static String decodeFormComponent(String value) {
        return HttpUrlUtil.decodeComponent(value.replace('+', ' '));
    }
}
