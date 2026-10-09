package com.laker.postman.common.component;

import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.setting.SettingsHintLabel;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.AsyncClipboardUtil;
import com.laker.postman.util.FileSizeDisplayUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Progress for finite downloads and unknown-length media responses.
 * Network workers only update counters; the Swing timer renders their latest values.
 */
@Slf4j
public class DownloadProgressDialog extends JDialog {
    private static final long WAITING_FOR_DATA_NANOS = 2_000_000_000L;
    private static final int UPDATE_INTERVAL_MS = 500;
    private static final int MAX_SPEED_SAMPLES = 600;
    private static final int STREAM_CHART_WINDOW_MS = 30_000;

    private final String downloadTitle;
    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JLabel detailsLabel;
    private final JLabel speedLabel;
    private final JLabel elapsedLabel;
    private final JLabel remainingLabel;
    private final JTextArea streamHint;
    private final JButton copyAddressButton;
    private final JButton cancelButton;
    private final JButton closeButton;
    private final TimeSeries speedSeries;
    private final DateAxis timeAxis;
    private final Timer updateTimer;
    private final AtomicLong totalBytes = new AtomicLong();
    private final AtomicLong lastDataNanos = new AtomicLong();

    @Getter
    private volatile boolean cancelled;
    private volatile boolean transferActive;
    private int currentContentLength;
    private boolean streamingMedia;
    private String sourceUrl;
    private Runnable cancelAction;
    private long startedNanos;
    private long endedNanos;
    private long lastBytesForSpeed;
    private long lastTimeForSpeed;

    public DownloadProgressDialog(String title) {
        this(null, title);
    }

    public DownloadProgressDialog(Window owner, String title) {
        super(owner, title, ModalityType.MODELESS);
        downloadTitle = title;
        setModal(false);
        setResizable(true);
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        ToolWindowSurfaceStyle.applyDialogSurface(mainPanel);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx, novisualpadding", "[][grow,fill][]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(headerPanel);
        headerPanel.add(new JLabel(IconUtil.createThemed("icons/download.svg", 18, 18)));
        titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        headerPanel.add(titleLabel, "wmin 0");
        statusLabel = new JLabel();
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getTextSecondary());
        headerPanel.add(statusLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        speedSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_SPEED_AXIS));
        speedSeries.setMaximumItemCount(MAX_SPEED_SAMPLES);
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null,
                I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_TIME_AXIS),
                I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_SPEED_AXIS),
                new TimeSeriesCollection(speedSeries), false, true, false);
        configureChart(chart);
        timeAxis = (DateAxis) chart.getXYPlot().getDomainAxis();
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(510, 160));
        chartPanel.setMinimumSize(new Dimension(250, 120));
        chartPanel.setMouseWheelEnabled(true);
        mainPanel.add(chartPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1, novisualpadding", "[grow,fill]", "[]8[]10[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(southPanel);
        JPanel infoPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 2, novisualpadding", "[grow,fill]12[grow,fill]", "[]4[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(infoPanel);
        detailsLabel = createInfoLabel();
        speedLabel = createInfoLabel();
        elapsedLabel = createInfoLabel();
        remainingLabel = createInfoLabel();
        infoPanel.add(detailsLabel, "wmin 0");
        infoPanel.add(elapsedLabel, "wmin 0");
        infoPanel.add(speedLabel, "wmin 0");
        infoPanel.add(remainingLabel, "wmin 0, hidemode 3");
        southPanel.add(infoPanel);

        streamHint = new SettingsHintLabel(I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_HINT), 510);
        streamHint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        ToolWindowSurfaceStyle.applyTextComponentDialogSurface(streamHint);
        streamHint.setForeground(ModernColors.getTextSecondary());
        streamHint.setBorder(BorderFactory.createEmptyBorder());
        streamHint.setVisible(false);
        southPanel.add(streamHint, "hidemode 3, growx, wmin 0");

        copyAddressButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_COPY_ADDRESS), false, "icons/copy.svg");
        copyAddressButton.setToolTipText(I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_COPY_ADDRESS_TOOLTIP));
        copyAddressButton.setVisible(false);
        copyAddressButton.addActionListener(e -> {
            if (sourceUrl != null && !sourceUrl.isBlank()) {
                AsyncClipboardUtil.setStringAsync(sourceUrl);
            }
        });
        cancelButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_STOP), false, "icons/cancel.svg");
        cancelButton.addActionListener(e -> {
            if (streamingMedia) {
                stopTransfer();
            } else {
                dispose();
            }
        });
        closeButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.BUTTON_CLOSE), true, null);
        closeButton.setVisible(false);
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new MigLayout("insets 10 0 0 0, fillx, novisualpadding", "[grow][][][]", "[]"));
        ToolWindowSurfaceStyle.applyDialogFooter(buttonPanel);
        buttonPanel.add(new JLabel());
        buttonPanel.add(copyAddressButton, "hidemode 3");
        buttonPanel.add(cancelButton, "hidemode 3");
        buttonPanel.add(closeButton, "hidemode 3");
        southPanel.add(buttonPanel);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
        updateTimer = new Timer(UPDATE_INTERVAL_MS, e -> updateUIWithLatestData());
        pack();
        setMinimumSize(new Dimension(480, 330));
        setSize(Math.max(560, getWidth()), Math.max(350, getHeight()));
    }

    private static JLabel createInfoLabel() {
        JLabel label = new JLabel();
        label.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        return label;
    }

    private static void configureChart(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setAutoRange(true);
        dateAxis.setAutoRangeMinimumSize(10_000);
        dateAxis.setTickLabelFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        dateAxis.setTickLabelPaint(ModernColors.getTextSecondary());
        dateAxis.setLabelFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        dateAxis.setLabelPaint(ModernColors.getTextPrimary());
        dateAxis.setAxisLinePaint(ModernColors.getBorderMediumColor());
        dateAxis.setTickMarkPaint(ModernColors.getBorderMediumColor());
        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.setAutoRangeIncludesZero(true);
        valueAxis.setTickLabelFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        valueAxis.setTickLabelPaint(ModernColors.getTextSecondary());
        valueAxis.setLabelFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        valueAxis.setLabelPaint(ModernColors.getTextPrimary());
        valueAxis.setAxisLinePaint(ModernColors.getBorderMediumColor());
        valueAxis.setTickMarkPaint(ModernColors.getBorderMediumColor());
        chart.setBackgroundPaint(ModernColors.getDialogChromeBackgroundColor());
        plot.setBackgroundPaint(ModernColors.getDialogChromeBackgroundColor());
        plot.setDomainGridlinePaint(ModernColors.getBorderLightColor());
        plot.setRangeGridlinePaint(ModernColors.getBorderLightColor());
        BasicStroke gridStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);
        plot.setDomainGridlineStroke(gridStroke);
        plot.setRangeGridlineStroke(gridStroke);
        plot.setOutlinePaint(ModernColors.getBorderLightColor());
        plot.getRenderer().setSeriesPaint(0, ModernColors.getPrimary());
        plot.getRenderer().setSeriesStroke(0, new BasicStroke(2f));
    }

    public void startDownload(int contentLength) {
        startDownload(contentLength, false, null, null);
    }

    public void startDownload(int contentLength, boolean streamingMedia, String sourceUrl, Runnable cancelAction) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> startDownload(contentLength, streamingMedia, sourceUrl, cancelAction));
            return;
        }
        this.currentContentLength = contentLength;
        this.streamingMedia = streamingMedia;
        this.sourceUrl = sourceUrl;
        this.cancelAction = cancelAction;
        cancelled = false;
        totalBytes.set(0);
        startedNanos = System.nanoTime();
        endedNanos = 0;
        lastDataNanos.set(startedNanos);
        lastTimeForSpeed = startedNanos;
        lastBytesForSpeed = 0;
        speedSeries.clear();
        // Millisecond periods use milliseconds as their serial index.
        speedSeries.setMaximumItemAge(streamingMedia ? STREAM_CHART_WINDOW_MS : Long.MAX_VALUE);
        timeAxis.setFixedAutoRange(streamingMedia ? STREAM_CHART_WINDOW_MS : 0);
        timeAxis.setLabel(streamingMedia
                ? I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_TIME_AXIS, STREAM_CHART_WINDOW_MS / 1000)
                : I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_TIME_AXIS));
        transferActive = true;
        String title = streamingMedia ? I18nUtil.getMessage(MessageKeys.STREAM_RESPONSE_TITLE) : downloadTitle;
        setTitle(title);
        titleLabel.setText(title);
        streamHint.setVisible(streamingMedia);
        copyAddressButton.setVisible(streamingMedia);
        copyAddressButton.setEnabled(sourceUrl != null && !sourceUrl.isBlank());
        cancelButton.setText(I18nUtil.getMessage(streamingMedia ? MessageKeys.STREAM_RESPONSE_STOP : MessageKeys.BUTTON_CANCEL));
        cancelButton.setVisible(true);
        closeButton.setVisible(false);
        remainingLabel.setVisible(!streamingMedia);
        updateUIWithLatestData();
        if (shouldShow(contentLength)) {
            pack();
            setSize(Math.max(560, getWidth()), Math.max(350, getHeight()));
            // Center after the final layout, on the owning window's display.
            setLocationRelativeTo(getOwner());
            setVisible(true);
            updateTimer.start();
        }
    }

    public void updateProgress(int bytesRead) {
        if (bytesRead > 0 && transferActive) {
            totalBytes.addAndGet(bytesRead);
            lastDataNanos.set(System.nanoTime());
        }
    }

    public void finishDownload() {
        finishDownload(true);
    }

    public void finishDownload(boolean completed) {
        finishDownload(completed, false);
    }

    public void finishDownload(boolean completed, boolean transferCancelled) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> finishDownload(completed, transferCancelled));
            return;
        }
        cancelled |= transferCancelled;
        transferActive = false;
        cancelAction = null;
        if (endedNanos == 0) {
            endedNanos = System.nanoTime();
        }
        updateTimer.stop();
        updateUIWithLatestData();
        showFinishedControls();
        setStatus(cancelled ? MessageKeys.DOWNLOAD_PROGRESS_STOPPED
                : completed ? MessageKeys.DOWNLOAD_PROGRESS_FINISHED : MessageKeys.DOWNLOAD_PROGRESS_FAILED,
                completed || cancelled ? ModernColors.getTextSecondary() : ModernColors.getError());
        if (!isVisible()) {
            dispose();
        }
    }

    private void stopTransfer() {
        if (!transferActive) {
            return;
        }
        cancelled = true;
        transferActive = false;
        endedNanos = System.nanoTime();
        updateTimer.stop();
        Runnable action = cancelAction;
        cancelAction = null;
        if (action != null) {
            try {
                action.run();
            } catch (RuntimeException ex) {
                log.warn("Failed to cancel response transfer", ex);
            }
        }
        updateUIWithLatestData();
        showFinishedControls();
        setStatus(MessageKeys.DOWNLOAD_PROGRESS_STOPPED, ModernColors.getTextSecondary());
    }

    private void showFinishedControls() {
        cancelButton.setVisible(false);
        closeButton.setVisible(true);
        remainingLabel.setVisible(false);
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    @Override
    public void dispose() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::dispose);
            return;
        }
        stopTransfer();
        updateTimer.stop();
        super.dispose();
    }

    private void updateUIWithLatestData() {
        long now = System.nanoTime();
        long bytes = totalBytes.get();
        long bytesDelta = Math.max(0, bytes - lastBytesForSpeed);
        long timeDelta = now - lastTimeForSpeed;
        double speed = timeDelta > 0 ? bytesDelta * 1_000_000_000.0 / timeDelta : 0;
        lastBytesForSpeed = bytes;
        lastTimeForSpeed = now;
        speedSeries.addOrUpdate(new Millisecond(), speed / 1024.0);
        detailsLabel.setText(I18nUtil.getMessage(streamingMedia ? MessageKeys.DOWNLOAD_PROGRESS_RECEIVED
                : currentContentLength > 0 ? MessageKeys.DOWNLOAD_PROGRESS_DOWNLOADED_TOTAL
                : MessageKeys.DOWNLOAD_PROGRESS_DOWNLOADED, formatSize(bytes), formatSize(currentContentLength)));
        speedLabel.setText(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_SPEED, formatSize((long) speed)));
        long elapsedSeconds = Math.max(0, ((endedNanos > 0 ? endedNanos : now) - startedNanos) / 1_000_000_000L);
        elapsedLabel.setText(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_ELAPSED, formatDuration(elapsedSeconds)));
        if (currentContentLength <= 0) {
            remainingLabel.setText(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_TOTAL_UNKNOWN));
        } else if (speed > 0) {
            long remainingSeconds = Math.max(0, (long) Math.ceil((currentContentLength - bytes) / speed));
            remainingLabel.setText(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_REMAINING, formatDuration(remainingSeconds)));
        } else {
            remainingLabel.setText(I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_REMAINING_WAITING));
        }
        if (transferActive) {
            boolean waiting = now - lastDataNanos.get() >= WAITING_FOR_DATA_NANOS;
            setStatus(waiting ? MessageKeys.DOWNLOAD_PROGRESS_WAITING
                            : bytes == 0 ? MessageKeys.DOWNLOAD_PROGRESS_CONNECTED : MessageKeys.DOWNLOAD_PROGRESS_RECEIVING,
                    waiting ? ModernColors.getTextSecondary() : ModernColors.getPrimary());
        }
    }

    private void setStatus(String messageKey, Color color) {
        statusLabel.setText(I18nUtil.getMessage(messageKey));
        statusLabel.setForeground(color);
    }

    private static String formatSize(long size) {
        return FileSizeDisplayUtil.formatSize(Math.max(0, size));
    }

    private static String formatDuration(long seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private boolean shouldShow(int contentLength) {
        return SettingManager.isShowDownloadProgressDialog()
                && (contentLength > SettingManager.getDownloadProgressDialogThreshold() || contentLength <= 0);
    }
}
