package com.laker.postman.service.curl;

import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.http.request.HttpRequestProtocol;
import com.laker.postman.request.util.HttpUrlUtil;
import lombok.experimental.UtilityClass;

/**
 * cURL 导入工具类
 * 提供 cURL 命令解析和转换为 HttpRequestItem 的功能
 */
@UtilityClass
public class CurlImportUtil {

    /**
     * 解析 cURL 命令并转换为 HttpRequestItem
     *
     * @param curlText cURL 命令文本（可以是 cURL 命令字符串或已解析的 CurlRequest 对象）
     * @return 解析后的 HttpRequestItem，如果解析失败返回 null
     */
    public static HttpRequestItem fromCurl(String curlText) {
        if (CharSequenceUtil.isBlank(curlText)) {
            return null;
        }

        try {
            CurlRequest curlRequest = CurlParser.parse(curlText);
            return fromCurlRequest(curlRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cURL command", e);
        }
    }

    /**
     * 将 CurlRequest 转换为 HttpRequestItem
     *
     * @param curlRequest 已解析的 cURL 请求对象
     * @return HttpRequestItem 对象，如果 curlRequest 无效返回 null
     */
    public static HttpRequestItem fromCurlRequest(CurlRequest curlRequest) {
        if (curlRequest == null || curlRequest.url == null) {
            return null;
        }

        HttpRequestItem item = new HttpRequestItem();
        item.setName(null);
        item.setUrl(HttpUrlUtil.decodeQueryForDisplay(curlRequest.url));
        item.setMethod(curlRequest.method);

        if (CollUtil.isNotEmpty(curlRequest.headersList)) {
            item.setHeadersList(curlRequest.headersList);
        }

        // Preserve cURL body bytes semantically. Signed or challenge-protected requests can fail
        // if imported JSON is reformatted before sending.
        item.setBody(curlRequest.body == null ? "" : curlRequest.body);

        if (CollUtil.isNotEmpty(curlRequest.paramsList)) {
            item.setParamsList(curlRequest.paramsList);
        }

        if (CollUtil.isNotEmpty(curlRequest.formDataList)) {
            item.setFormDataList(curlRequest.formDataList);
            item.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        }

        if (CollUtil.isNotEmpty(curlRequest.urlencodedList)) {
            item.setUrlencodedList(curlRequest.urlencodedList);
            item.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        }

        if (curlRequest.binaryBody) {
            item.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        } else if (curlRequest.body != null && !curlRequest.body.isEmpty()
                && CollUtil.isEmpty(curlRequest.formDataList)
                && CollUtil.isEmpty(curlRequest.urlencodedList)) {
            item.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        }

        if (curlRequest.followRedirects) {
            item.setFollowRedirects(Boolean.TRUE);
        }

        if (AuthType.DIGEST.getConstant().equals(curlRequest.authType)
                && CharSequenceUtil.isNotBlank(curlRequest.authUsername)) {
            item.setAuthType(AuthType.DIGEST.getConstant());
            item.setAuthUsername(curlRequest.authUsername);
            item.setAuthPassword(curlRequest.authPassword != null ? curlRequest.authPassword : "");
        }

        // 智能判断协议类型
        if (HttpRequestProtocol.isWebSocketUrl(item.getUrl())) {
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
        } else {
            item.setProtocol(RequestItemProtocolEnum.HTTP);
        }

        return item;
    }

}
