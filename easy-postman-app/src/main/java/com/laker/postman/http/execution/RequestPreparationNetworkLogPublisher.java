package com.laker.postman.http.execution;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestVersions;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@UtilityClass
public class RequestPreparationNetworkLogPublisher {

    public static void publish(PreparedRequest request) {
        if (!NetworkLogSupport.isEnabled(request)) {
            return;
        }
        NetworkLogSupport.append(request, NetworkLogEventStage.REQUEST_PREPARED, format(request));
    }

    private static String format(PreparedRequest request) {
        StringBuilder sb = new StringBuilder("\n");
        if (!isBlank(request.bodyType) && !RequestBodyTypes.BODY_TYPE_NONE.equals(request.bodyType)) {
            appendLine(sb, MessageKeys.NETWORK_LOG_PREPARED_BODY_TYPE, request.bodyType);
        }
        if (request.headersList != null && request.headersList.stream()
                .anyMatch(header -> header != null && !isBlank(header.getKey()) && !header.isEnabled())) {
            appendCountSummary(sb, MessageKeys.NETWORK_LOG_PREPARED_CONFIGURED_HEADERS,
                    request.headersList, HttpHeader::getKey, HttpHeader::isEnabled);
        }
        appendCountSummary(sb, MessageKeys.NETWORK_LOG_PREPARED_QUERY_PARAMS,
                request.paramsList, HttpParam::getKey, HttpParam::isEnabled);
        appendCountSummary(sb, MessageKeys.NETWORK_LOG_PREPARED_PATH_VARIABLES,
                request.pathVariablesList, HttpParam::getKey, HttpParam::isEnabled);
        appendCountSummary(sb, MessageKeys.NETWORK_LOG_PREPARED_FORM_DATA,
                request.formDataList, HttpFormData::getKey, HttpFormData::isEnabled);
        appendCountSummary(sb, MessageKeys.NETWORK_LOG_PREPARED_URLENCODED,
                request.urlencodedList, HttpFormUrlencoded::getKey, HttpFormUrlencoded::isEnabled);
        if (!isBlank(request.prescript)) {
            appendLine(sb, MessageKeys.NETWORK_LOG_PREPARED_PRE_SCRIPT);
        }
        if (!isBlank(request.postscript)) {
            appendLine(sb, MessageKeys.NETWORK_LOG_PREPARED_POST_SCRIPT);
        }
        appendLine(sb, MessageKeys.NETWORK_LOG_PREPARED_SESSION,
                enabledText(request.followRedirects), enabledText(request.cookieJarEnabled));
        appendLine(sb, MessageKeys.NETWORK_LOG_PREPARED_TRANSPORT,
                enabledText(request.sslVerificationEnabled), httpPreference(request.httpVersion),
                request.requestTimeoutMs > 0 ? request.requestTimeoutMs + " ms"
                        : I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_NO_TIMEOUT));
        return sb.toString();
    }

    private static <T> void appendCountSummary(StringBuilder sb, String messageKey, List<T> rows,
                                              Function<T, String> key, Predicate<T> enabled) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Predicate<T> configured = row -> row != null && !isBlank(key.apply(row));
        long totalCount = rows.stream().filter(configured).count();
        if (totalCount == 0) {
            return;
        }
        long enabledCount = rows.stream().filter(configured).filter(enabled).count();
        appendLine(sb, messageKey, Long.toString(enabledCount), Long.toString(totalCount));
    }

    private static void appendLine(StringBuilder sb, String messageKey, Object... args) {
        sb.append(I18nUtil.getMessage(messageKey, args)).append('\n');
    }

    private static String enabledText(boolean enabled) {
        return I18nUtil.getMessage(enabled ? MessageKeys.NETWORK_LOG_VALUE_ENABLED
                : MessageKeys.NETWORK_LOG_VALUE_DISABLED);
    }

    private static String httpPreference(String version) {
        if (isBlank(version) || HttpRequestVersions.AUTO.equalsIgnoreCase(version)) {
            return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_HTTP_AUTO);
        }
        return switch (version) {
            case HttpRequestVersions.HTTP_1_1 -> "HTTP/1.1";
            case HttpRequestVersions.HTTP_2 -> "HTTP/2";
            default -> version;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
