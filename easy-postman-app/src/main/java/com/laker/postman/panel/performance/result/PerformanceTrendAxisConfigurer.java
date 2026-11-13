package com.laker.postman.panel.performance.result;

import lombok.experimental.UtilityClass;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.RangeType;

import java.text.SimpleDateFormat;
import java.text.NumberFormat;

@UtilityClass
class PerformanceTrendAxisConfigurer {

    private static final int MAX_TIME_TICK_LABELS = 9;
    private static final long SECOND_MS = 1_000L;
    private static final long MINUTE_MS = 60 * SECOND_MS;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final String WALL_CLOCK_TIME_WITH_SECONDS_PATTERN = "HH:mm:ss";
    private static final String WALL_CLOCK_TIME_WITH_MINUTES_PATTERN = "HH:mm";
    private static final String WALL_CLOCK_DATE_TIME_PATTERN = "MM-dd HH:mm";
    private static final TimeTickUnitSpec[] TIME_TICK_CANDIDATES = {
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 1, SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 2, 2 * SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 5, 5 * SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 10, 10 * SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 15, 15 * SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.SECOND, 30, 30 * SECOND_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 1, MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 2, 2 * MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 5, 5 * MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 10, 10 * MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 15, 15 * MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.MINUTE, 30, 30 * MINUTE_MS),
            new TimeTickUnitSpec(DateTickUnitType.HOUR, 1, HOUR_MS),
            new TimeTickUnitSpec(DateTickUnitType.HOUR, 2, 2 * HOUR_MS),
            new TimeTickUnitSpec(DateTickUnitType.HOUR, 4, 4 * HOUR_MS),
            new TimeTickUnitSpec(DateTickUnitType.HOUR, 6, 6 * HOUR_MS),
            new TimeTickUnitSpec(DateTickUnitType.HOUR, 12, 12 * HOUR_MS),
            new TimeTickUnitSpec(DateTickUnitType.DAY, 1, DAY_MS)
    };

    void configureMetricAxis(NumberAxis axis) {
        if (axis == null) {
            return;
        }
        axis.setAutoRangeMinimumSize(1.0);
        axis.setUpperMargin(0.2);
        axis.setAutoRangeIncludesZero(true);
        // 趋势指标都不为负；否则全 0 序列会以 0 为中心显示成 -0.4..0.4。
        axis.setRangeType(RangeType.POSITIVE);
    }

    void configureIntegerAxis(NumberAxis axis) {
        if (axis == null) {
            return;
        }
        // 整数指标必须使用整数刻度，不能只把小数刻度格式化成整数，否则 0.5/1.5 会显示成重复的 0/2。
        axis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        axis.setNumberFormatOverride(NumberFormat.getIntegerInstance());
    }

    void configureTimeAxis(DateAxis axis, long visibleDurationMs) {
        if (axis == null) {
            return;
        }
        DateTickUnit tickUnit = selectTimeTickUnit(visibleDurationMs);
        // TickUnit 自带 formatter，与 JFreeChart 标准 DateAxis 的做法一致；关闭 auto 保证多张分离图刻度一致。
        axis.setDateFormatOverride(null);
        axis.setTickUnit(tickUnit, false, true);
    }

    DateTickUnit selectTimeTickUnit(long visibleDurationMs) {
        long durationMs = normalizeDurationMs(visibleDurationMs);
        for (TimeTickUnitSpec candidate : TIME_TICK_CANDIDATES) {
            if (estimatedTickLabelCount(durationMs, candidate.durationMs()) <= MAX_TIME_TICK_LABELS) {
                return candidate.toDateTickUnit(selectTimeFormatPattern(durationMs));
            }
        }
        return new DateTickUnit(DateTickUnitType.DAY, 1, new SimpleDateFormat(WALL_CLOCK_DATE_TIME_PATTERN));
    }

    long domainRightPaddingMs(long visibleDurationMs) {
        // 最后一个 tick 如果刚好落在绘图区右边界，JFreeChart 容易裁掉标签；保留少量空间即可，不能多出整段空白。
        long durationMs = normalizeDurationMs(visibleDurationMs);
        long tickMs = tickUnitMillis(selectTimeTickUnit(durationMs));
        long labelPaddingMs = Math.max(500L, tickMs / 4);
        long maxPaddingMs = Math.max(500L, durationMs / 40);
        return Math.min(labelPaddingMs, maxPaddingMs);
    }

    String selectTimeFormatPattern(long visibleDurationMs) {
        long durationMs = normalizeDurationMs(visibleDurationMs);
        if (durationMs <= HOUR_MS) {
            return WALL_CLOCK_TIME_WITH_SECONDS_PATTERN;
        }
        if (durationMs <= DAY_MS) {
            return WALL_CLOCK_TIME_WITH_MINUTES_PATTERN;
        }
        return WALL_CLOCK_DATE_TIME_PATTERN;
    }

    private static long normalizeDurationMs(long visibleDurationMs) {
        return Math.max(SECOND_MS, visibleDurationMs);
    }

    private static long estimatedTickLabelCount(long durationMs, long tickMs) {
        return (long) Math.ceil(durationMs / (double) tickMs) + 1;
    }

    private static long tickUnitMillis(DateTickUnit unit) {
        int multiple = unit.getMultiple();
        DateTickUnitType type = unit.getUnitType();
        if (DateTickUnitType.SECOND.equals(type)) {
            return multiple * SECOND_MS;
        }
        if (DateTickUnitType.MINUTE.equals(type)) {
            return multiple * MINUTE_MS;
        }
        if (DateTickUnitType.HOUR.equals(type)) {
            return multiple * HOUR_MS;
        }
        if (DateTickUnitType.DAY.equals(type)) {
            return multiple * DAY_MS;
        }
        return SECOND_MS;
    }

    private record TimeTickUnitSpec(DateTickUnitType type, int multiple, long durationMs) {
        DateTickUnit toDateTickUnit(String formatPattern) {
            return new DateTickUnit(type, multiple, new SimpleDateFormat(formatPattern));
        }
    }
}
