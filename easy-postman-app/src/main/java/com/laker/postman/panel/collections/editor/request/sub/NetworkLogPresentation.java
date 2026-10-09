package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ModernColors;
import lombok.experimental.UtilityClass;

import java.awt.Color;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves visual emphasis from runtime diagnostics before their labels are localized.
 * Status codes are read only from the first line of response metadata, never from user data.
 */
@UtilityClass
class NetworkLogPresentation {
    private static final Pattern RESPONSE_STATUS = Pattern.compile(
            "^(?:HTTP/\\d+(?:\\.\\d+)?|Status:)[ \\t]+([1-5]\\d{2})(?:[ \\t].*)?$");
    private static final Pattern CERTIFICATE_WARNING = Pattern.compile(
            "^(?:⚠️?\\s*)?Certificate Warning:\\s*\\S.*$");

    static NetworkLogStage resolveStage(NetworkLogStage stage, String rawMessage) {
        NetworkLogStage resolved = stage == null ? NetworkLogStage.DEFAULT : stage;
        // OkHttp can report an explicit cancellation again through its failure callback.
        // Keep actual timeout and socket errors in the failure stages.
        if ((resolved == NetworkLogStage.CALL_FAILED || resolved == NetworkLogStage.REQUEST_FAILED
                || resolved == NetworkLogStage.RESPONSE_FAILED) && rawMessage != null) {
            String reason = rawMessage.strip();
            if (reason.equalsIgnoreCase("Canceled") || reason.equalsIgnoreCase("Cancelled")
                    || reason.equalsIgnoreCase("IOException: Canceled")
                    || reason.equalsIgnoreCase("IOException: Cancelled")) {
                return NetworkLogStage.CANCELED;
            }
        }
        return resolved;
    }

    static Style resolveStyle(NetworkLogStage stage, String rawMessage) {
        Integer status = responseStatus(stage, rawMessage);
        if (status != null && status >= 400) {
            return new Style("❌", NetworkLogPresentation::errorColor, true);
        }
        if (stage == NetworkLogStage.REQUEST_COMPLETE && status != null) {
            if (status >= 200 && status < 300) {
                return new Style("✅", NetworkLogPresentation::successColor, true);
            }
            if (status >= 300 && status < 400) {
                return new Style("↪", ModernColors::getPrimary, true);
            }
        }
        if (stage == NetworkLogStage.RETRY_DECISION && rawMessage != null) {
            if (rawMessage.startsWith("Retry: true, reason: ")) {
                return new Style("🔁", NetworkLogPresentation::warningColor, true);
            }
            if (rawMessage.startsWith("Retry: false, reason: ")) {
                return new Style("■", ModernColors::getTextSecondary, false);
            }
        }
        if (stage == NetworkLogStage.REDIRECT && rawMessage != null) {
            if (rawMessage.startsWith("Redirect stopped: disabled, status: ")) {
                return new Style("■", ModernColors::getTextSecondary, false);
            }
            if (rawMessage.startsWith("Redirect stopped: max redirects reached, status: ")
                    || rawMessage.startsWith("Redirect stopped: missing Location, status: ")) {
                return new Style("⚠️", NetworkLogPresentation::warningColor, true);
            }
        }
        if (stage == NetworkLogStage.SECURE_CONNECT_END && rawMessage != null
                && rawMessage.lines().anyMatch(line -> isCertificateWarning(stage, line))) {
            return new Style(stage.getEmoji(), NetworkLogPresentation::warningColor, true);
        }
        if (stage.isError()) {
            return new Style(stage.getEmoji(), NetworkLogPresentation::errorColor, stage.isBold());
        }
        if (stage == NetworkLogStage.CONNECT_FAILED || stage == NetworkLogStage.SATISFACTION_FAILURE) {
            return new Style("⚠️", NetworkLogPresentation::warningColor, true);
        }
        return new Style(stage.getEmoji(), stage::getColor, stage.isBold());
    }

    static Integer responseStatus(NetworkLogStage stage, String rawMessage) {
        if (rawMessage == null || (stage != NetworkLogStage.RESPONSE_HEADERS_END
                && stage != NetworkLogStage.RESPONSE_HEADERS_END_REDIRECT
                && stage != NetworkLogStage.REQUEST_COMPLETE)) {
            return null;
        }
        String firstLine = rawMessage.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        Matcher matcher = RESPONSE_STATUS.matcher(firstLine);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }

    static boolean isCertificateWarning(NetworkLogStage stage, String rawLine) {
        return stage == NetworkLogStage.SECURE_CONNECT_END && rawLine != null
                && CERTIFICATE_WARNING.matcher(rawLine).matches();
    }

    static Color statusColor(int status) {
        if (status >= 400) {
            return errorColor();
        }
        if (status >= 200 && status < 300) {
            return successColor();
        }
        return ModernColors.getPrimary();
    }

    static Color warningColor() {
        return ModernColors.isDarkTheme() ? ModernColors.getWarning() : ModernColors.getWarningDarker();
    }

    private static Color successColor() {
        return ModernColors.isDarkTheme() ? ModernColors.getSuccess() : ModernColors.getSuccessDark();
    }

    private static Color errorColor() {
        return ModernColors.isDarkTheme() ? ModernColors.getError() : ModernColors.getErrorDark();
    }

    record Style(String symbol, Supplier<Color> colorProvider, boolean bold) {
        Color color() {
            return colorProvider.get();
        }
    }
}
