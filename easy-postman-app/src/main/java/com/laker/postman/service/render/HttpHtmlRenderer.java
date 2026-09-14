package com.laker.postman.service.render;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.functional.model.RequestResult;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpFormUrlencoded;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.performance.model.PerformanceInternalHeaders;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一的 HTTP 请求/响应 HTML 渲染工具类
 */
@UtilityClass
public class HttpHtmlRenderer {

    private static final int MAX_DISPLAY_SIZE = 2 * 1024;
    private static final int MAX_REQUEST_DISPLAY_SIZE = 2 * 1024;

    // ==================== 字号 ====================

    /**
     * JTextPane HTMLEditorKit 基于 72dpi，Swing 字号基于屏幕 DPI，
     * 乘以 0.7 换算为视觉等效的 HTML px。
     */
    private static int htmlFontSize() {
        return Math.max(8, (int) Math.round(SettingManager.getUiFontSize() * 0.7));
    }

    private static String fs()      { return htmlFontSize() + "px"; }
    private static String fsSmall() { return Math.max(8, htmlFontSize() - 1) + "px"; }

    // ==================== 主题 ====================

    private static String colorPrimary() { return toHex(ModernColors.getPrimary()); }
    private static String colorSuccess() { return toHex(ModernColors.getSuccess()); }
    private static String colorError() { return toHex(ModernColors.getError()); }
    private static String colorWarning() { return toHex(ModernColors.getWarning()); }
    private static String colorInfo() { return toHex(ModernColors.getInfo()); }
    private static String colorGray() { return toHex(ModernColors.getTextHint()); }
    private static String surfaceColor() { return toHex(ModernColors.getCardBackgroundColor()); }
    private static String textColor() { return toHex(ModernColors.getTextPrimary()); }
    private static String borderColor() { return toHex(ModernColors.getBorderLightColor()); }
    private static String rowDividerColor() {
        return toHex(ModernColors.blendColors(
                ModernColors.getCardBackgroundColor(),
                ModernColors.getBorderLightColor(),
                ModernColors.isDarkTheme() ? 0.55f : 0.36f
        ));
    }
    private static String codeBgColor() { return toHex(ModernColors.getHoverBackgroundColor()); }

    private static String statusColor(int code) {
        if (code <= 0) return colorError();
        if (code >= 500) return colorError();
        if (code >= 400) return colorWarning();
        return colorSuccess();
    }

    // ==================== HTML 文档 ====================

    /**
     * 完整 HTML 文档：body 设好字体、字号、颜色，子元素直接继承，无需重复设置。
     */
    private static String htmlDoc(String bodyContent) {
        return "<html><body style='"
                + "font-family:monospace;"
                + "font-size:" + fs() + ";"
                + "color:" + textColor() + ";"
                + "background:" + surfaceColor() + ";"
                + "margin:8px 12px;"
                + "'>" + bodyContent + "</body></html>";
    }

    // ==================== 通用 HTML 片段 ====================

    /** key: value 行。详情页使用单一白色内容面，避免灰色条块切碎视线。 */
    private static String kvRow(String keyColor, String key, String value, boolean alt) {
        return "<div style='padding:3px 4px 4px 4px;"
                + "line-height:1.35;"
                + "word-break:break-all;'>"
                + "<span style='color:" + keyColor + ";font-weight:bold;'>" + key + "</span>"
                + "<span style='color:" + colorGray() + ";'> : </span>"
                + "<span>" + value + "</span>"
                + "</div>";
    }

    /** 节标题，带左边竖线装饰 */
    private static String sectionTitle(String color, String title) {
        return "<div style='margin:10px 0 4px 0;padding-left:6px;"
                + "border-left:3px solid " + color + ";"
                + "font-weight:bold;color:" + color + ";'>" + title + "</div>";
    }

    /** 无数据提示 */
    private static String noData(String message) {
        return "<div style='color:" + colorGray() + ";padding:12px;font-style:italic;'>" + message + "</div>";
    }

    /** 警告/错误提示框 */
    private static String alertBox(String color, String title, String message) {
        return "<div style='border-left:3px solid " + color + ";padding:8px 12px;"
                + "margin-bottom:10px;background:" + codeBgColor() + ";border-radius:0 4px 4px 0;'>"
                + "<div style='color:" + color + ";font-weight:bold;margin-bottom:4px;'>" + escapeHtml(title) + "</div>"
                + "<div style='white-space:pre-wrap;word-break:break-all;'>" + escapeHtml(message) + "</div>"
                + "</div>";
    }

    // ==================== 公开 API ====================

    public static String renderTimingInfo(HttpResponse response) {
        if (response == null || response.httpEventInfo == null) return htmlDoc(noData("No Timing Info"));
        return htmlDoc(buildTimingHtml(response));
    }

    public static String renderEventInfo(HttpResponse response) {
        if (response == null || response.httpEventInfo == null) return htmlDoc(noData("No Event Info"));
        return htmlDoc(buildEventInfoHtml(response.httpEventInfo));
    }

    /** 渲染请求信息 */
    public static String renderRequest(PreparedRequest req) {
        if (req == null) return htmlDoc(noData("无请求信息"));
        StringBuilder sb = new StringBuilder();

        if (hasWebSocketHandshakeSnapshot(req)) {
            sb.append(kvRow(colorPrimary(), "WebSocket URL", escapeHtml(safeStr(req.url)), false));
            sb.append(kvRow(colorPrimary(), "Handshake URL", escapeHtml(safeStr(req.sentUrl)), true));
            sb.append(kvRow(colorPrimary(), "Handshake Method",
                    escapeHtml(safeStr(resolveSentOrConfigured(req.sentMethod, req.method))), false));
        } else {
            sb.append(kvRow(colorPrimary(), "URL", escapeHtml(safeStr(resolveSentOrConfigured(req.sentUrl, req.url))), false));
            sb.append(kvRow(colorPrimary(), "Method", escapeHtml(safeStr(resolveSentOrConfigured(req.sentMethod, req.method))), true));
        }

        boolean hasSentHeaders = hasHeaders(req.sentHeadersList);
        List<HttpHeader> displayHeaders = hasSentHeaders
                ? req.sentHeadersList
                : enabledHeaders(req.headersList);
        if (!displayHeaders.isEmpty()) {
            sb.append(sectionTitle(colorPrimary(), hasSentHeaders ? "Sent Headers" : "Configured Headers"));
            for (int i = 0; i < displayHeaders.size(); i++) {
                HttpHeader header = displayHeaders.get(i);
                if (header == null) {
                    continue;
                }
                sb.append(kvRow(colorPrimary(),
                        escapeHtml(header.getKey()),
                        escapeHtml(header.getValue()), i % 2 != 0));
            }
        }

        if (req.formDataList != null && !req.formDataList.isEmpty()) {
            boolean hasText = req.formDataList.stream().anyMatch(d -> d.isEnabled() && d.isText());
            boolean hasFile = req.formDataList.stream().anyMatch(d -> d.isEnabled() && d.isFile());
            if (hasText) {
                sb.append(sectionTitle(colorPrimary(), "Form Data"));
                int[] idx = {0};
                req.formDataList.stream().filter(d -> d.isEnabled() && d.isText()).forEach(d ->
                        sb.append(kvRow(colorPrimary(), escapeHtml(d.getKey()), escapeHtml(d.getValue()), idx[0]++ % 2 != 0)));
            }
            if (hasFile) {
                sb.append(sectionTitle(colorPrimary(), "Form Files"));
                int[] idx = {0};
                req.formDataList.stream().filter(d -> d.isEnabled() && d.isFile()).forEach(d ->
                        sb.append(kvRow(colorPrimary(), escapeHtml(d.getKey()), escapeHtml(d.getValue()), idx[0]++ % 2 != 0)));
            }
        }

        if (req.urlencodedList != null && !req.urlencodedList.isEmpty()) {
            sb.append(sectionTitle(colorPrimary(), "x-www-form-urlencoded"));
            int[] idx = {0};
            req.urlencodedList.stream().filter(HttpFormUrlencoded::isEnabled).forEach(e ->
                    sb.append(kvRow(colorPrimary(), escapeHtml(e.getKey()), escapeHtml(e.getValue()), idx[0]++ % 2 != 0)));
        }

        boolean hasSentSnapshot = hasSentHeaders || req.sentRequestBody != null;
        String displayBody = hasSentSnapshot ? req.sentRequestBody : req.body;
        if (isNotEmpty(displayBody)) {
            sb.append(sectionTitle(colorPrimary(), isNotEmpty(req.sentRequestBody) ? "Sent Body" : "Configured Body"));
            sb.append(codeBlock(truncate(displayBody, MAX_REQUEST_DISPLAY_SIZE)));
        }

        return htmlDoc(sb.toString());
    }

    private static boolean hasWebSocketHandshakeSnapshot(PreparedRequest req) {
        return req != null
                && isWebSocketUrl(req.url)
                && req.sentUrl != null
                && !req.sentUrl.isBlank()
                && !req.sentUrl.equals(req.url);
    }

    private static boolean isWebSocketUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("ws://") || lower.startsWith("wss://");
    }

    /** 渲染响应信息 */
    public static String renderResponse(HttpResponse resp) {
        if (resp == null) return htmlDoc(noData("无响应信息"));
        StringBuilder sb = new StringBuilder();

        appendNetworkErrorAlert(sb, resp.httpEventInfo);

        int code = resp.code;
        String statusBadge = "<span style='color:" + statusColor(code) + ";font-weight:bold;padding:1px 6px;"
                + "border:1px solid " + statusColor(code) + ";border-radius:3px;'>" + code + "</span>";
        sb.append(kvRow(colorSuccess(), "Status",   statusBadge, false));
        sb.append(kvRow(colorPrimary(), "Protocol", escapeHtml(resolveProtocol(resp)),   true));
        sb.append(kvRow(colorPrimary(), "Thread",   escapeHtml(resolveThread(resp)), false));
        if (resp.httpEventInfo != null) {
            sb.append(kvRow(colorPrimary(), "Connection",
                    escapeHtml(safeStr(resp.httpEventInfo.getLocalAddress()))
                            + " <span style='color:" + colorGray() + ";'>→</span> "
                            + escapeHtml(safeStr(resp.httpEventInfo.getRemoteAddress())), true));
        }

        appendResponseHeaders(sb, resp.headers);

        sb.append(sectionTitle(colorSuccess(), "Body"));
        sb.append(codeBlock(truncate(resp.body)));

        return htmlDoc(sb.toString());
    }

    private static void appendNetworkErrorAlert(StringBuilder sb, HttpEventInfo eventInfo) {
        if (eventInfo != null && isNotEmpty(eventInfo.getErrorMessage())) {
            sb.append(alertBox(colorWarning(), "⚠ Network Error", eventInfo.getErrorMessage()));
        }
    }

    public static String renderResponseWithError(ResultNodeInfo info) {
        if (info == null) return renderResponse(null);
        return buildResponseWithError(info.errorMsg,
                info.resp != null ? info.resp.httpEventInfo : null, info.resp);
    }

    public static String renderResponseWithError(RequestResult request) {
        if (request == null) return renderResponse(null);
        return buildResponseWithError(request.getErrorMessage(),
                request.getResponse() != null ? request.getResponse().httpEventInfo : null,
                request.getResponse());
    }

    /** 渲染测试结果 */
    public static String renderTestResults(List<TestResult> testResults) {
        if (testResults == null || testResults.isEmpty()) return htmlDoc(noData("No test results"));

        StringBuilder sb = new StringBuilder();
        sb.append("<table style='border-collapse:collapse;width:100%;'>")
                .append("<tr style='font-weight:bold;border-bottom:1px solid ").append(rowDividerColor()).append(";'>")
                .append("<th style='padding:5px 10px;text-align:left;'>Name</th>")
                .append("<th style='padding:5px 10px;text-align:center;width:60px;'>Result</th>")
                .append("<th style='padding:5px 10px;text-align:left;'>Message</th>")
                .append("</tr>");
        int[] idx = {0};
        for (TestResult r : testResults) {
            if (r != null) sb.append(testResultRow(r, idx[0]++ % 2 != 0));
        }
        sb.append("</table>");
        return htmlDoc(sb.toString());
    }

    // ==================== 私有实现 ====================

    /** 代码块（请求/响应 body） */
    private static String codeBlock(String content) {
        return "<div style='background:" + codeBgColor()
                + ";color:" + textColor()
                + ";padding:8px;border-radius:4px;margin:2px 0 6px 0;'>"
                + "<pre style='margin:0;font-family:monospace;white-space:pre-wrap;word-break:break-all;'>"
                + escapeHtml(content)
                + "</pre></div>";
    }

    /**
     * 构建包含错误信息的响应内容（不做字符串截取，直接复用 renderResponseBody）
     */
    private static String buildResponseWithError(String errorMsg, HttpEventInfo eventInfo, HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        if (isNotEmpty(errorMsg)) {
            sb.append(alertBox(colorError(), "⚠ Error", errorMsg));
        }
        if (eventInfo != null && isNotEmpty(eventInfo.getErrorMessage())) {
            sb.append(alertBox(colorWarning(), "⚠ Network Error", eventInfo.getErrorMessage()));
        }
        // 直接拼响应内容片段，不包装外层 htmlDoc（由最后统一包装）
        sb.append(renderResponseBody(response));
        return htmlDoc(sb.toString());
    }

    /**
     * 只输出响应内容片段（无 htmlDoc 包装），供 buildResponseWithError 复用
     */
    private static String renderResponseBody(HttpResponse resp) {
        if (resp == null) return noData("No Response");
        StringBuilder sb = new StringBuilder();

        int code = resp.code;
        String statusBadge = "<span style='color:" + statusColor(code) + ";font-weight:bold;padding:1px 6px;"
                + "border:1px solid " + statusColor(code) + ";border-radius:3px;'>" + code + "</span>";
        sb.append(kvRow(colorSuccess(), "Status",   statusBadge, false));
        sb.append(kvRow(colorPrimary(), "Protocol", escapeHtml(resolveProtocol(resp)),   true));
        sb.append(kvRow(colorPrimary(), "Thread",   escapeHtml(resolveThread(resp)), false));
        if (resp.httpEventInfo != null) {
            sb.append(kvRow(colorPrimary(), "Connection",
                    escapeHtml(safeStr(resp.httpEventInfo.getLocalAddress()))
                            + " <span style='color:" + colorGray() + ";'>→</span> "
                            + escapeHtml(safeStr(resp.httpEventInfo.getRemoteAddress())), true));
        }
        appendResponseHeaders(sb, resp.headers);
        sb.append(sectionTitle(colorSuccess(), "Body"));
        sb.append(codeBlock(truncate(resp.body)));
        return sb.toString();
    }

    private static void appendResponseHeaders(StringBuilder sb, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        boolean hasVisibleHeader = headers.keySet().stream()
                .anyMatch(name -> !PerformanceInternalHeaders.isInternalHeader(name));
        if (!hasVisibleHeader) {
            return;
        }
        sb.append(sectionTitle(colorSuccess(), "Headers"));
        int[] idx = {0};
        headers.forEach((key, values) -> {
            if (PerformanceInternalHeaders.isInternalHeader(key)) {
                return;
            }
            sb.append(kvRow(colorPrimary(), escapeHtml(key),
                    escapeHtml(values != null ? String.join(", ", values) : ""),
                    idx[0]++ % 2 != 0));
        });
    }

    private static String testResultRow(TestResult r, boolean alt) {
        String icon = r.passed
                ? "<span style='color:" + colorSuccess() + ";font-size:" + (htmlFontSize() + 1) + "px;'>&#10003;</span>"
                : "<span style='color:" + colorError() + ";font-size:" + (htmlFontSize() + 1) + "px;'>&#10007;</span>";
        String msg = isNotEmpty(r.message)
                ? "<span style='color:" + colorError() + ";white-space:pre-wrap;word-break:break-all;'>" + escapeHtml(r.message) + "</span>"
                : "";
        return "<tr style='border-bottom:1px solid " + rowDividerColor() + ";'>"
                + "<td style='padding:5px 10px;'>" + escapeHtml(r.name) + "</td>"
                + "<td style='padding:5px 10px;text-align:center;'>" + icon + "</td>"
                + "<td style='padding:5px 10px;'>" + msg + "</td>"
                + "</tr>";
    }

    private static String resolveProtocol(HttpResponse resp) {
        if (resp == null) {
            return "";
        }
        if (isNotEmpty(resp.protocol)) {
            return resp.protocol;
        }
        return resp.httpEventInfo != null ? safeStr(resp.httpEventInfo.getProtocol()) : "";
    }

    private static String resolveThread(HttpResponse resp) {
        if (resp == null) {
            return "";
        }
        if (isNotEmpty(resp.threadName)) {
            return resp.threadName;
        }
        return resp.httpEventInfo != null ? safeStr(resp.httpEventInfo.getThreadName()) : "";
    }

    // Timeline 各阶段语义色，统一从主题 token 取色，避免暗色主题下固定色过暗。
    private static String timelineQueueColor() { return colorGray(); }
    private static String timelineDnsColor() { return toHex(ModernColors.getAccent()); }
    private static String timelineTcpColor() { return colorWarning(); }
    private static String timelineSslColor() { return toHex(ModernColors.getSecondaryDark()); }
    private static String timelineRequestColor() { return colorPrimary(); }
    private static String timelineTtfbColor() { return colorSuccess(); }
    private static String timelineDownloadColor() { return colorInfo(); }
    private static String timelineTrackColor() { return codeBgColor(); }

    private static String buildTimingHtml(HttpResponse response) {
        HttpEventInfo info = response.httpEventInfo;
        TimingCalculator calc = new TimingCalculator(info);
        long total = calc.getTotal();

        StringBuilder sb = new StringBuilder();
        sb.append(sectionTitle(colorPrimary(), "Timeline"));
        sb.append("<table style='border-collapse:collapse;width:100%;table-layout:fixed;'>");
        sb.append("<tr style='font-weight:bold;border-bottom:1px solid ").append(rowDividerColor()).append(";color:").append(colorGray()).append(";'>")
                .append("<th style='padding:4px 6px;text-align:left;width:30%;'>Phase</th>")
                .append("<th style='padding:4px 6px;text-align:right;width:16%;'>Time</th>")
                .append("<th style='padding:4px 6px;width:54%;'>Bar</th>")
                .append("</tr>");

        // Total 行不显示 bar（它是基准，显示 100% bar 没意义）
        // These are peer phases. TimingCalculator already removes nested TCP/TLS overlap,
        // so avoid tree-like labels that imply parent/child durations.
        timingRow(sb, "Total", calc.getTotal(), colorError(), true, total, true, -1);
        for (TimingPhase phase : buildTimingPhases(calc, total)) {
            timingRow(sb, phase.name, phase.value, phase.color, phase.bold, total, false, phase.percent);
        }

        sb.append("<tr><td colspan='3' style='padding:5px 0 3px 0;border-top:1px solid ")
                .append(rowDividerColor()).append("'></td></tr>");
        appendTimingRow(sb, "Connection Reused", calc.getConnectionReused() ? "Yes" : "No", null, false, -1, total);
        appendTimingRow(sb, "Idle Connections",  String.valueOf(response.idleConnectionCount), null, false, -1, total);
        appendTimingRow(sb, "Total Connections", String.valueOf(response.connectionCount),     null, false, -1, total);

        sb.append("</table>");
        return sb.toString();
    }

    private static List<TimingPhase> buildTimingPhases(TimingCalculator calc, long total) {
        List<TimingPhase> phases = new ArrayList<>();
        phases.add(new TimingPhase("Queueing", calc.getQueueing(), timelineQueueColor(), false));
        phases.add(new TimingPhase("Stalled", calc.getStalled(), timelineQueueColor(), false));
        phases.add(new TimingPhase("DNS Lookup", calc.getDns(), timelineDnsColor(), false));
        phases.add(new TimingPhase("TCP Connect", calc.getConnect(), timelineTcpColor(), false));
        phases.add(new TimingPhase("TLS Handshake", calc.getTls(), timelineSslColor(), false));
        phases.add(new TimingPhase("Request Sent", calc.getRequestSent(), timelineRequestColor(), false));
        phases.add(new TimingPhase("Waiting (TTFB)", calc.getServerCost(), timelineTtfbColor(), true));
        phases.add(new TimingPhase("Content Download", calc.getResponseBody(), timelineDownloadColor(), false));

        long known = sumPositivePhaseValues(phases);
        if (total > known) {
            phases.add(new TimingPhase("Other", total - known, timelineQueueColor(), false));
        }
        assignTimelinePercentages(phases, total);
        return phases;
    }

    private static long sumPositivePhaseValues(List<TimingPhase> phases) {
        long sum = 0;
        for (TimingPhase phase : phases) {
            if (phase.value > 0) {
                sum += phase.value;
            }
        }
        return sum;
    }

    private static void assignTimelinePercentages(List<TimingPhase> phases, long total) {
        if (total <= 0) {
            return;
        }

        int floorSum = 0;
        double[] remainders = new double[phases.size()];
        for (int i = 0; i < phases.size(); i++) {
            TimingPhase phase = phases.get(i);
            if (phase.value <= 0) {
                phase.percent = -1;
                continue;
            }
            double rawPercent = phase.value * 100.0 / total;
            phase.percent = (int) Math.floor(rawPercent);
            remainders[i] = rawPercent - phase.percent;
            floorSum += phase.percent;
        }

        int remaining = Math.max(0, 100 - floorSum);
        while (remaining-- > 0) {
            int index = indexOfLargestRemainder(phases, remainders);
            if (index < 0) {
                return;
            }
            phases.get(index).percent++;
            remainders[index] = -1;
        }
    }

    private static int indexOfLargestRemainder(List<TimingPhase> phases, double[] remainders) {
        int index = -1;
        double max = -1;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).value <= 0) {
                continue;
            }
            if (remainders[i] > max) {
                max = remainders[i];
                index = i;
            }
        }
        return index;
    }

    private static void timingRow(StringBuilder sb, String name, long val, String color, boolean bold,
                                  long total, boolean hideBar, int percent) {
        appendTimingRow(sb, name, val >= 0 ? val + " ms" : "-", color, bold,
                hideBar ? -1 : (val > 0 ? val : -1), total, percent);
    }

    /**
     * Timeline 行：名称列 + 时间列 + 进度条列
     * 用 table 实现进度条——JTextPane HTML 渲染器对嵌套 div 的 width:% 支持很差，
     * 而 table 的 width 属性支持可靠。
     */
    private static void appendTimingRow(StringBuilder sb, String name, String val,
                                        String color, boolean bold, long barVal, long total) {
        appendTimingRow(sb, name, val, color, bold, barVal, total, -1);
    }

    private static void appendTimingRow(StringBuilder sb, String name, String val,
                                        String color, boolean bold, long barVal, long total, int displayPercent) {
        String nameStyle = (bold ? "font-weight:bold;" : "")
                + (color != null ? "color:" + color + ";" : "color:" + textColor() + ";");
        String valStyle = (color != null ? "color:" + color + ";" : "color:" + textColor() + ";")
                + (bold ? "font-weight:bold;" : "");

        // 进度条：用 table 实现，两列：filled + empty，宽度用整数 px 近似
        String bar = "";
        if (barVal > 0 && total > 0) {
            int pct = displayPercent >= 0
                    ? displayPercent
                    : (int) Math.min(100, Math.round(barVal * 100.0 / total));
            int emptyPct = 100 - pct;
            String barColor = color != null ? color : colorPrimary();
            // 用 table 宽度百分比：JTextPane 对 table width=% 支持良好。
            // 空轨道使用 hover background，亮色下是轻灰蓝，暗色下是比 surface 稍亮的灰。
            String barTrack = "<table style='border-collapse:collapse;width:100%;' cellpadding='0' cellspacing='0'><tr>"
                    + "<td width='" + pct + "%' style='background:" + barColor
                    + ";height:8px;border-radius:2px 0 0 2px;'></td>"
                    + (emptyPct > 0 ? "<td width='" + emptyPct + "%' style='background:" + timelineTrackColor() + ";height:8px;'></td>" : "")
                    + "</tr></table>";
            bar = "<table style='border-collapse:collapse;width:100%;' cellpadding='0' cellspacing='0'><tr>"
                    + "<td width='88%' style='padding:0;'>" + barTrack + "</td>"
                    + "<td width='12%' style='padding:0 0 0 6px;text-align:right;white-space:nowrap;color:"
                    + colorGray() + ";font-size:" + fsSmall() + ";'>" + pct + "%</td>"
                    + "</tr></table>";
        }

        sb.append("<tr>")
                .append("<td style='padding:3px 6px;").append(nameStyle).append("'>").append(name).append("</td>")
                .append("<td style='padding:3px 6px;text-align:right;").append(valStyle).append("'>").append(val).append("</td>")
                .append("<td style='padding:3px 6px;'>").append(bar).append("</td>")
                .append("</tr>");
    }

    private static final class TimingPhase {
        private final String name;
        private final long value;
        private final String color;
        private final boolean bold;
        private int percent = -1;

        private TimingPhase(String name, long value, String color, boolean bold) {
            this.name = name;
            this.value = value;
            this.color = color;
            this.bold = bold;
        }
    }

    private static String buildEventInfoHtml(HttpEventInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(sectionTitle(colorPrimary(), "Summary"));
        sb.append("<table style='border-collapse:collapse;width:100%;margin-bottom:8px;'>");
        eventRow(sb, "Local Address",  escapeHtml(info.getLocalAddress()),  false);
        eventRow(sb, "Remote Address", escapeHtml(info.getRemoteAddress()),  true);
        eventRow(sb, "Protocol",       info.getProtocol() != null ? info.getProtocol() : "-", false);
        eventRow(sb, "TLS Version",    safeStr(info.getTlsVersion()),        true);
        eventRow(sb, "Thread",         safeStr(info.getThreadName()),        false);
        if (isNotEmpty(info.getDnsHost())) {
            eventRow(sb, I18nUtil.getMessage(MessageKeys.HTTP_EVENT_DNS_HOST),
                    escapeHtml(info.getDnsHost()), true);
        }
        if (info.getDnsAddresses() != null && !info.getDnsAddresses().isEmpty()) {
            eventRow(sb, I18nUtil.getMessage(MessageKeys.HTTP_EVENT_DNS_ADDRESSES),
                    escapeHtml(String.join(", ", info.getDnsAddresses())), false);
        }
        if (isNotEmpty(info.getDnsError())) {
            eventRow(sb, I18nUtil.getMessage(MessageKeys.HTTP_EVENT_DNS_ERROR),
                    "<span style='color:" + colorError() + ";'>"
                    + escapeHtml(info.getDnsError()) + "</span>", true);
        }
        if (info.getRetryDecisionCount() > 0) {
            eventRow(sb, I18nUtil.getMessage(MessageKeys.HTTP_EVENT_RETRIES),
                    I18nUtil.getMessage(MessageKeys.HTTP_EVENT_DECISIONS,
                            info.getRetryCount(), info.getRetryDecisionCount()), false);
        }
        if (info.getFollowUpDecisionCount() > 0) {
            eventRow(sb, I18nUtil.getMessage(MessageKeys.HTTP_EVENT_FOLLOW_UPS),
                    I18nUtil.getMessage(MessageKeys.HTTP_EVENT_DECISIONS,
                            info.getFollowUpCount(), info.getFollowUpDecisionCount()), true);
        }
        if (isNotEmpty(info.getErrorMessage())) {
            eventRow(sb, "Error", "<span style='color:" + colorError() + ";'>" + escapeHtml(info.getErrorMessage()) + "</span>", true);
        }
        sb.append("</table>");

        if (info.getRouteAttempts() != null && !info.getRouteAttempts().isEmpty()) {
            sb.append(sectionTitle(colorPrimary(), I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_ATTEMPTS)));
            sb.append("<table style='border-collapse:collapse;width:100%;margin-bottom:8px;'>");
            sb.append("<tr style='font-weight:bold;border-bottom:1px solid ").append(rowDividerColor())
                    .append(";color:").append(colorGray()).append(";'>")
                    .append("<th style='padding:3px 8px;text-align:left;'>")
                    .append(escapeHtml(I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_FAMILY)))
                    .append("</th>")
                    .append("<th style='padding:3px 8px;text-align:left;'>")
                    .append(escapeHtml(I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_ADDRESS)))
                    .append("</th>")
                    .append("<th style='padding:3px 8px;text-align:left;'>")
                    .append(escapeHtml(I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_RESULT)))
                    .append("</th>")
                    .append("<th style='padding:3px 8px;text-align:left;'>")
                    .append(escapeHtml(I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_DURATION)))
                    .append("</th>")
                    .append("</tr>");
            boolean alt = false;
            List<HttpRouteAttempt> routeAttempts = new ArrayList<>(info.getRouteAttempts());
            routeAttempts.sort(Comparator.comparingLong(HttpRouteAttempt::startTime));
            for (HttpRouteAttempt attempt : routeAttempts) {
                String result;
                if (attempt.connected()) {
                    result = I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_CONNECTED);
                } else if (attempt.canceled()) {
                    result = I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_CANCELED);
                } else {
                    result = I18nUtil.getMessage(MessageKeys.HTTP_EVENT_ROUTE_FAILED);
                }
                if (!attempt.connected() && isNotEmpty(attempt.error())) {
                    result += ": " + attempt.error();
                }
                appendRouteAttemptRow(sb, attempt, result, alt);
                alt = !alt;
            }
            sb.append("</table>");
        }

        sb.append(sectionTitle(colorPrimary(), "Event Timestamps"));
        sb.append("<table style='border-collapse:collapse;width:100%;'>");
        sb.append("<tr style='font-weight:bold;border-bottom:1px solid ").append(rowDividerColor()).append(";color:").append(colorGray()).append(";'>")
                .append("<th style='padding:3px 8px;text-align:left;width:40%;'>Event</th>")
                .append("<th style='padding:3px 8px;text-align:left;'>Time</th>")
                .append("</tr>");

        // 只显示非空（>0）的时间戳，减少噪音
        appendEventTimingRowIfSet(sb, "QueueStart",          info.getQueueStart(),          colorGray(),    false);
        appendEventTimingRowIfSet(sb, "CallStart",            info.getCallStart(),            colorPrimary(), true);
        appendEventTimingRowIfSet(sb, "DispatcherQueueStart", info.getDispatcherQueueStart(), null, false);
        appendEventTimingRowIfSet(sb, "DispatcherQueueEnd",   info.getDispatcherQueueEnd(),   null, true);
        appendEventTimingRowIfSet(sb, "DnsStart",             info.getDnsStart(),             null,          false);
        appendEventTimingRowIfSet(sb, "DnsEnd",               info.getDnsEnd(),               null,          true);
        appendEventTimingRowIfSet(sb, "ConnectStart",         info.getConnectStart(),         null,          false);
        appendEventTimingRowIfSet(sb, "SecureConnectStart",   info.getSecureConnectStart(),   null,          true);
        appendEventTimingRowIfSet(sb, "SecureConnectEnd",     info.getSecureConnectEnd(),     null,          false);
        appendEventTimingRowIfSet(sb, "ConnectEnd",           info.getConnectEnd(),           null,          true);
        appendEventTimingRowIfSet(sb, "ConnectionAcquired",   info.getConnectionAcquired(),   colorPrimary(), false);
        appendEventTimingRowIfSet(sb, "RequestHeadersStart",  info.getRequestHeadersStart(),  null,          true);
        appendEventTimingRowIfSet(sb, "RequestHeadersEnd",    info.getRequestHeadersEnd(),    null,          false);
        appendEventTimingRowIfSet(sb, "RequestBodyStart",     info.getRequestBodyStart(),     null,          true);
        appendEventTimingRowIfSet(sb, "RequestBodyEnd",       info.getRequestBodyEnd(),       null,          false);
        appendEventTimingRowIfSet(sb, "ResponseHeadersStart", info.getResponseHeadersStart(), colorSuccess(), true);
        appendEventTimingRowIfSet(sb, "ResponseHeadersEnd",   info.getResponseHeadersEnd(),   null,          false);
        appendEventTimingRowIfSet(sb, "ResponseBodyStart",    info.getResponseBodyStart(),    null,          true);
        appendEventTimingRowIfSet(sb, "ResponseBodyEnd",      info.getResponseBodyEnd(),      null,          false);
        appendEventTimingRowIfSet(sb, "ConnectionReleased",   info.getConnectionReleased(),   null,          true);
        appendEventTimingRowIfSet(sb, "CallEnd",              info.getCallEnd(),              colorPrimary(), false);
        appendEventTimingRowIfSet(sb, "CallFailed",           info.getCallFailed(),           colorError(),   true);
        appendEventTimingRowIfSet(sb, "Canceled",             info.getCanceled(),             colorError(),   false);

        sb.append("</table>");
        return sb.toString();
    }

    private static void eventRow(StringBuilder sb, String label, String value, boolean alt) {
        sb.append("<tr>")
                .append("<td style='width:35%;color:").append(colorGray()).append(";padding:3px 8px;'>").append(label).append("</td>")
                .append("<td style='width:65%;padding:3px 8px;word-break:break-all;'>").append(value).append("</td>")
                .append("</tr>");
    }

    private static void appendRouteAttemptRow(StringBuilder sb,
                                              HttpRouteAttempt attempt,
                                              String result,
                                              boolean alt) {
        String background = alt ? "background:" + codeBgColor() + ";" : "";
        String resultHtml = attempt.connected()
                ? escapeHtml(result)
                : "<span style='color:" + colorError() + ";'>" + escapeHtml(result) + "</span>";
        sb.append("<tr style='").append(background).append("'>")
                .append("<td style='padding:3px 8px;'>").append(escapeHtml(attempt.addressFamily())).append("</td>")
                .append("<td style='padding:3px 8px;word-break:break-all;'>").append(escapeHtml(attempt.address())).append("</td>")
                .append("<td style='padding:3px 8px;'>").append(resultHtml).append("</td>")
                .append("<td style='padding:3px 8px;'>").append(attempt.durationMs()).append(" ms</td>")
                .append("</tr>");
    }

    /** 只在时间戳 > 0 时才输出行，避免大量 "-" 噪音 */
    private static void appendEventTimingRowIfSet(StringBuilder sb, String label, long millis, String color, boolean alt) {
        if (millis <= 0) return;
        String style = color != null ? "color:" + color + ";" : "";
        sb.append("<tr>")
                .append("<td style='padding:3px 8px;").append(style).append("width:40%;'>").append(label).append("</td>")
                .append("<td style='padding:3px 8px;width:60%;'>").append(formatMillis(millis)).append("</td>")
                .append("</tr>");
    }

    // ==================== 工具方法 ====================

    private static String safeStr(String s) { return s != null ? s : "-"; }
    private static boolean isNotEmpty(String s) { return s != null && !s.isEmpty(); }
    private static String resolveSentOrConfigured(String sentValue, String configuredValue) {
        return sentValue != null && !sentValue.isBlank() ? sentValue : configuredValue;
    }

    private static boolean hasHeaders(List<HttpHeader> headers) {
        return headers != null && !headers.isEmpty();
    }

    private static List<HttpHeader> enabledHeaders(List<HttpHeader> headers) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        return headers.stream()
                .filter(header -> header != null && header.isEnabled())
                .toList();
    }

    private static String formatMillis(long millis) {
        return millis <= 0 ? "-" : new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(millis));
    }

    private static String truncate(String content) {
        return truncate(content, MAX_DISPLAY_SIZE);
    }

    private static String truncate(String content, int maxDisplaySize) {
        if (content == null) return "";
        if (content.length() <= maxDisplaySize) return content;
        return content.substring(0, maxDisplaySize)
                + "\n\n[Truncated: " + content.length() + " chars, showing first " + (maxDisplaySize / 1024) + "KB]";
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String toHex(java.awt.Color color) {
        return ModernColors.toHtmlColor(color);
    }
}
