package com.laker.postman.panel.performance.threadgroup;

import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.EasyJSpinner;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.SegmentedButtonBar;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThreadGroupPropertyPanel extends JPanel {
    private static final int CONFIG_PANEL_WIDTH = 460;
    private static final int PREVIEW_PANEL_HEIGHT = 200;
    private static final int FORM_CONTROL_HEIGHT = 28;
    private static final int SPINNER_WIDTH = 80;
    private static final int LABEL_FIELD_GAP = 8;
    private static final int FIELD_PAIR_GAP = 30;
    private static final int FORM_ROW_GAP = 8;
    private static final int CONFIG_PREVIEW_GAP = 36;

    private final EasyComboBox<ThreadGroupData.ThreadMode> modeComboBox;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private PerformanceTreeNode currentNode;
    private boolean updatingPreview;

    // 固定模式面板组件
    private final JPanel fixedPanel;
    private final EasyJSpinner fixedNumThreadsSpinner;
    private final EasyJSpinner fixedLoopsSpinner;
    private final SegmentedButtonBar<Boolean> executionModeBar;
    private final JToggleButton useLoopCountButton;
    private final JToggleButton useTimeCheckBox;
    private final EasyJSpinner durationSpinner;
    private final JLabel maxInFlightWaitLabel;
    private final EasyJSpinner maxInFlightWaitSpinner;

    // 递增模式面板组件
    private final JPanel rampUpPanel;
    private final EasyJSpinner rampUpStartThreadsSpinner;
    private final EasyJSpinner rampUpEndThreadsSpinner;
    private final EasyJSpinner rampUpTimeSpinner;
    private final EasyJSpinner rampUpDurationSpinner;

    // 尖刺模式面板组件
    private final JPanel spikePanel;
    private final EasyJSpinner spikeMinThreadsSpinner;
    private final EasyJSpinner spikeMaxThreadsSpinner;
    private final EasyJSpinner spikeRampUpTimeSpinner;
    private final EasyJSpinner spikeHoldTimeSpinner;
    private final EasyJSpinner spikeRampDownTimeSpinner;
    private final EasyJSpinner spikeDurationSpinner;

    // 阶梯模式面板组件
    private final JPanel stairsPanel;
    private final EasyJSpinner stairsStartThreadsSpinner;
    private final EasyJSpinner stairsEndThreadsSpinner;
    private final EasyJSpinner stairsStepSpinner;
    private final EasyJSpinner stairsHoldTimeSpinner;
    private final EasyJSpinner stairsDurationSpinner;

    // 负载模式预览相关
    private final ThreadLoadPreviewPanel previewPanel;

    public ThreadGroupPropertyPanel() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        modeComboBox = new EasyComboBox<>(
                ThreadGroupData.ThreadMode.values(),
                EasyComboBox.WidthMode.FIXED_MAX
        );
        modeComboBox.setRenderer(new ThreadModeRenderer());

        // 中间部分：左侧配置面板，右侧预览图
        JPanel mainPanel = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, gap 0",
                "[" + CONFIG_PANEL_WIDTH + "!,left]" + CONFIG_PREVIEW_GAP + "[420::,grow,fill]",
                "[]"
        ));
        mainPanel.setOpaque(false);

        // 中间卡片布局，用于切换不同模式的配置面板
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        // 初始化所有控件和面板
        // 1. 固定模式面板
        fixedPanel = new JPanel(createFixedModeLayout());
        fixedPanel.setOpaque(false);
        fixedNumThreadsSpinner = standardThreadCountSpinner(1);
        fixedLoopsSpinner = standardIntSpinner(1, 1, null, 1);
        executionModeBar = new SegmentedButtonBar<>(FlowLayout.CENTER, SegmentedButtonBar.Size.COMPACT);
        useLoopCountButton = executionModeBar.addOption(
                Boolean.FALSE,
                I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_EXECUTION_COUNT),
                true
        );
        useTimeCheckBox = executionModeBar.addOption(
                Boolean.TRUE,
                I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_EXECUTION_TIME),
                false
        );
        durationSpinner = standardIntSpinner(60, 1, null, 10);
        maxInFlightWaitLabel = formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_MAX_IN_FLIGHT_WAIT));
        maxInFlightWaitSpinner = standardIntSpinner(
                ThreadGroupData.DEFAULT_MAX_IN_FLIGHT_WAIT_SECONDS,
                1,
                null,
                1
        );
        String maxInFlightWaitTooltip = I18nUtil.getMessage(MessageKeys.THREADGROUP_MAX_IN_FLIGHT_WAIT_TOOLTIP);
        maxInFlightWaitLabel.setToolTipText(maxInFlightWaitTooltip);
        maxInFlightWaitSpinner.setToolTipText(maxInFlightWaitTooltip);

        // 2. 递增模式面板
        rampUpPanel = new JPanel(createValuePairLayout());
        rampUpPanel.setOpaque(false);
        rampUpStartThreadsSpinner = standardThreadCountSpinner(1);
        rampUpEndThreadsSpinner = standardThreadCountSpinner(10);
        rampUpTimeSpinner = standardIntSpinner(30, 1, null, 5);
        rampUpDurationSpinner = standardIntSpinner(120, 1, null, 10);

        // 3. 尖刺模式面板
        spikePanel = new JPanel(createValuePairLayout());
        spikePanel.setOpaque(false);
        spikeMinThreadsSpinner = standardThreadCountSpinner(1);
        spikeMaxThreadsSpinner = standardThreadCountSpinner(20);
        spikeRampUpTimeSpinner = standardIntSpinner(10, 1, null, 1);
        spikeHoldTimeSpinner = standardIntSpinner(5, 0, null, 1);
        spikeRampDownTimeSpinner = standardIntSpinner(10, 1, null, 1);
        spikeDurationSpinner = standardIntSpinner(120, 1, null, 10);

        // 4. 阶梯模式面板
        stairsPanel = new JPanel(createValuePairLayout());
        stairsPanel.setOpaque(false);
        stairsStartThreadsSpinner = standardThreadCountSpinner(1);
        stairsEndThreadsSpinner = standardThreadCountSpinner(20);
        stairsStepSpinner = standardIntSpinner(5, 1, null, 1);
        stairsHoldTimeSpinner = standardIntSpinner(10, 1, null, 1);
        stairsDurationSpinner = standardIntSpinner(240, 1, null, 10);

        // 设置各个面板的布局
        setupFixedPanel();
        setupRampUpPanel();
        setupSpikePanel();
        setupStairsPanel();

        // 添加所有面板到卡片布局
        cardPanel.add(fixedPanel, ThreadGroupData.ThreadMode.FIXED.name());
        cardPanel.add(rampUpPanel, ThreadGroupData.ThreadMode.RAMP_UP.name());
        cardPanel.add(spikePanel, ThreadGroupData.ThreadMode.SPIKE.name());
        cardPanel.add(stairsPanel, ThreadGroupData.ThreadMode.STAIRS.name());

        // 默认显示固定模式面板
        showThreadMode(ThreadGroupData.ThreadMode.FIXED);

        // 模式切换监听器
        modeComboBox.addActionListener(e -> {
            ThreadGroupData.ThreadMode selectedMode = (ThreadGroupData.ThreadMode) modeComboBox.getSelectedItem();
            if (selectedMode != null) {
                showThreadMode(selectedMode);
                updateMaxInFlightWaitState();
                updatePreview();
            }
        });

        executionModeBar.setSelectionListener(useTime -> {
            updateFixedExecutionModeState();
            updateMaxInFlightWaitState();
        });
        updateFixedExecutionModeState();
        updateMaxInFlightWaitState();

        // 预览图表区域
        previewPanel = new ThreadLoadPreviewPanel();
        previewPanel.setPreferredSize(new Dimension(560, PREVIEW_PANEL_HEIGHT));
        previewPanel.setMinimumSize(new Dimension(380, PREVIEW_PANEL_HEIGHT));  // 设置最小尺寸防止过度压缩
        previewPanel.setOpaque(false);
        previewPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // 左侧配置区包装在一个面板中，以便控制布局
        JPanel configPanel = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, gap 0",
                "[left]",
                "[]10[]8[]"
        ));
        configPanel.setOpaque(false);
        configPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        JPanel modeRow = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gap 0",
                "[right,pref!]8[pref!,left]",
                "[]"
        ));
        modeRow.setOpaque(false);
        modeRow.add(formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_MODE_LABEL)), "aligny center");
        modeRow.add(modeComboBox, "w pref!, h " + FORM_CONTROL_HEIGHT + "!");
        configPanel.add(modeRow, "left, wrap");
        configPanel.add(cardPanel, "w " + CONFIG_PANEL_WIDTH + "!, h pref!, growx, wrap");
        int fixedRightFieldWidth = Math.max(SPINNER_WIDTH, executionModeBar.getPreferredSize().width);
        JPanel maxInFlightWaitRow = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, gap 0",
                "[grow,fill][right,pref!]" + LABEL_FIELD_GAP + "[" + fixedRightFieldWidth + "!,left]",
                "[]"
        ));
        maxInFlightWaitRow.setOpaque(false);
        maxInFlightWaitRow.add(maxInFlightWaitLabel, "skip 1, aligny center");
        maxInFlightWaitRow.add(maxInFlightWaitSpinner, spinnerConstraints());
        maxInFlightWaitRow.setPreferredSize(new Dimension(
                fixedPanel.getPreferredSize().width,
                FORM_CONTROL_HEIGHT
        ));
        configPanel.add(maxInFlightWaitRow, "left");
        JPanel previewSection = new JPanel(new MigLayout(
                "insets 0, fill, novisualpadding, gap 0",
                "[grow,fill]",
                "[" + PREVIEW_PANEL_HEIGHT + "!,fill]"
        ));
        previewSection.setOpaque(false);
        previewSection.add(previewPanel, "grow");

        // 添加到主面板
        mainPanel.add(configPanel, "top, growx");
        mainPanel.add(previewSection, "top, growx, pushx");

        // 整体布局
        add(mainPanel, BorderLayout.NORTH);

        // 为所有输入组件添加变化监听，刷新预览图
        addPreviewUpdateListeners();
    }

    private static EasyJSpinner threadCountSpinner(int value) {
        return EasyJSpinner.intSpinner(value, ThreadGroupData.MIN_THREADS, null, 1);
    }

    private static EasyJSpinner standardThreadCountSpinner(int value) {
        return standardSizedSpinner(threadCountSpinner(value));
    }

    private static EasyJSpinner standardIntSpinner(int value, int minimum, Integer maximum, int stepSize) {
        return standardSizedSpinner(EasyJSpinner.intSpinner(value, minimum, maximum, stepSize));
    }

    private static EasyJSpinner standardSizedSpinner(EasyJSpinner spinner) {
        spinner.setPreferredSize(new Dimension(SPINNER_WIDTH, FORM_CONTROL_HEIGHT));
        return spinner;
    }

    private static MigLayout createValuePairLayout() {
        return createThreadGroupFormLayout(3, "[" + SPINNER_WIDTH + "!]");
    }

    private static MigLayout createFixedModeLayout() {
        return createThreadGroupFormLayout(2, "[pref!]");
    }

    private static MigLayout createThreadGroupFormLayout(int rowCount, String rightFieldColumn) {
        return new MigLayout(
                formLayoutConstraints(),
                formColumns(rightFieldColumn),
                formRows(rowCount)
        );
    }

    private static String formLayoutConstraints() {
        return "insets 0, novisualpadding, gap 0";
    }

    private static String formColumns(String rightFieldColumn) {
        return "[right,pref!]" + LABEL_FIELD_GAP + "["
                + SPINNER_WIDTH + "!]"
                + FIELD_PAIR_GAP
                + "[right,pref!]" + LABEL_FIELD_GAP + rightFieldColumn;
    }

    private static String formRows(int rowCount) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) {
                rows.append(FORM_ROW_GAP);
            }
            rows.append("[]");
        }
        return rows.toString();
    }

    private static JLabel formLabel(String text) {
        return new JLabel(text, SwingConstants.RIGHT);
    }

    private static String spinnerConstraints() {
        return "w " + SPINNER_WIDTH + "!, h " + FORM_CONTROL_HEIGHT + "!";
    }

    // 设置固定模式面板
    private void setupFixedPanel() {
        fixedPanel.add(formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_USERS)));
        fixedPanel.add(fixedNumThreadsSpinner, spinnerConstraints());
        fixedPanel.add(formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_EXECUTION_MODE)));
        fixedPanel.add(createExecutionModePanel(), "w pref!, h pref!, wrap");

        fixedPanel.add(formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_LOOPS)));
        fixedPanel.add(fixedLoopsSpinner, spinnerConstraints());
        fixedPanel.add(formLabel(I18nUtil.getMessage(MessageKeys.THREADGROUP_FIXED_DURATION)));
        fixedPanel.add(durationSpinner, spinnerConstraints());
    }

    // 设置递增模式面板
    private void setupRampUpPanel() {
        addValuePairRow(
                rampUpPanel,
                MessageKeys.THREADGROUP_RAMPUP_START_USERS,
                rampUpStartThreadsSpinner,
                MessageKeys.THREADGROUP_RAMPUP_END_USERS,
                rampUpEndThreadsSpinner
        );
        addValuePairRow(
                rampUpPanel,
                MessageKeys.THREADGROUP_RAMPUP_RAMP_TIME,
                rampUpTimeSpinner,
                MessageKeys.THREADGROUP_RAMPUP_TEST_DURATION,
                rampUpDurationSpinner
        );
    }

    // 设置尖刺模式面板
    private void setupSpikePanel() {
        addValuePairRow(
                spikePanel,
                MessageKeys.THREADGROUP_SPIKE_MIN_USERS,
                spikeMinThreadsSpinner,
                MessageKeys.THREADGROUP_SPIKE_MAX_USERS,
                spikeMaxThreadsSpinner
        );
        addValuePairRow(
                spikePanel,
                MessageKeys.THREADGROUP_SPIKE_RAMP_UP_TIME,
                spikeRampUpTimeSpinner,
                MessageKeys.THREADGROUP_SPIKE_HOLD_TIME,
                spikeHoldTimeSpinner
        );
        addValuePairRow(
                spikePanel,
                MessageKeys.THREADGROUP_SPIKE_RAMP_DOWN_TIME,
                spikeRampDownTimeSpinner,
                MessageKeys.THREADGROUP_SPIKE_TEST_DURATION,
                spikeDurationSpinner
        );
    }

    // 设置阶梯模式面板
    private void setupStairsPanel() {
        addValuePairRow(
                stairsPanel,
                MessageKeys.THREADGROUP_STAIRS_START_USERS,
                stairsStartThreadsSpinner,
                MessageKeys.THREADGROUP_STAIRS_END_USERS,
                stairsEndThreadsSpinner
        );
        addValuePairRow(
                stairsPanel,
                MessageKeys.THREADGROUP_STAIRS_STEP_SIZE,
                stairsStepSpinner,
                MessageKeys.THREADGROUP_STAIRS_HOLD_TIME,
                stairsHoldTimeSpinner
        );
        addValuePairRow(
                stairsPanel,
                MessageKeys.THREADGROUP_STAIRS_TEST_DURATION,
                stairsDurationSpinner,
                null,
                null
        );
    }

    private void addValuePairRow(JPanel panel,
                                 String leftLabelKey,
                                 JComponent leftField,
                                 String rightLabelKey,
                                 JComponent rightField) {
        panel.add(formLabel(I18nUtil.getMessage(leftLabelKey)));
        panel.add(leftField, spinnerConstraints());
        if (rightLabelKey != null && rightField != null) {
            panel.add(formLabel(I18nUtil.getMessage(rightLabelKey)));
            panel.add(rightField, spinnerConstraints() + ", wrap");
        } else {
            panel.add(Box.createHorizontalStrut(0), "span 2, wrap");
        }
    }

    private void addPreviewUpdateListeners() {
        // 固定模式参数变化监听
        fixedNumThreadsSpinner.addChangeListener(e -> updatePreview());
        fixedLoopsSpinner.addChangeListener(e -> updatePreview());
        useLoopCountButton.addActionListener(e -> updatePreview());
        useTimeCheckBox.addActionListener(e -> updatePreview());
        durationSpinner.addChangeListener(e -> updatePreview());

        // 递增模式参数变化监听
        rampUpStartThreadsSpinner.addChangeListener(e -> updatePreview());
        rampUpEndThreadsSpinner.addChangeListener(e -> updatePreview());
        rampUpTimeSpinner.addChangeListener(e -> updatePreview());
        rampUpDurationSpinner.addChangeListener(e -> updatePreview());

        // 尖刺模式参数变化监听
        spikeMinThreadsSpinner.addChangeListener(e -> updatePreview());
        spikeMaxThreadsSpinner.addChangeListener(e -> updatePreview());
        spikeRampUpTimeSpinner.addChangeListener(e -> updatePreview());
        spikeHoldTimeSpinner.addChangeListener(e -> updatePreview());
        spikeRampDownTimeSpinner.addChangeListener(e -> updatePreview());
        spikeDurationSpinner.addChangeListener(e -> updatePreview());

        // 阶梯模式参数变化监听
        stairsStartThreadsSpinner.addChangeListener(e -> updatePreview());
        stairsEndThreadsSpinner.addChangeListener(e -> updatePreview());
        stairsStepSpinner.addChangeListener(e -> updatePreview());
        stairsHoldTimeSpinner.addChangeListener(e -> updatePreview());
        stairsDurationSpinner.addChangeListener(e -> updatePreview());
    }

    private void updatePreview() {
        if (updatingPreview) {
            return;
        }
        updatingPreview = true;
        try {
            updatePreviewSnapshot();
        } finally {
            updatingPreview = false;
        }
    }

    private void updatePreviewSnapshot() {
        ThreadGroupData.ThreadMode mode = (ThreadGroupData.ThreadMode) modeComboBox.getSelectedItem();
        if (mode == null) return;

        ThreadLoadPreviewData previewData = new ThreadLoadPreviewData();
        previewData.mode = mode;

        switch (mode) {
            case FIXED:
                previewData.fixedThreads = fixedNumThreadsSpinner.getCommittedIntValue();
                previewData.useTime = useTimeCheckBox.isSelected();
                previewData.duration = durationSpinner.getCommittedIntValue();
                previewData.loops = fixedLoopsSpinner.getCommittedIntValue();
                break;

            case RAMP_UP:
                previewData.rampUpStartThreads = rampUpStartThreadsSpinner.getCommittedIntValue();
                previewData.rampUpEndThreads = rampUpEndThreadsSpinner.getCommittedIntValue();
                previewData.rampUpTime = rampUpTimeSpinner.getCommittedIntValue();
                previewData.rampUpDuration = rampUpDurationSpinner.getCommittedIntValue();
                break;

            case SPIKE:
                previewData.spikeMinThreads = spikeMinThreadsSpinner.getCommittedIntValue();
                previewData.spikeMaxThreads = spikeMaxThreadsSpinner.getCommittedIntValue();
                previewData.spikeRampUpTime = spikeRampUpTimeSpinner.getCommittedIntValue();
                previewData.spikeHoldTime = spikeHoldTimeSpinner.getCommittedIntValue();
                previewData.spikeRampDownTime = spikeRampDownTimeSpinner.getCommittedIntValue();
                previewData.spikeDuration = spikeDurationSpinner.getCommittedIntValue();
                break;

            case STAIRS:
                previewData.stairsStartThreads = stairsStartThreadsSpinner.getCommittedIntValue();
                previewData.stairsEndThreads = stairsEndThreadsSpinner.getCommittedIntValue();
                previewData.stairsStep = stairsStepSpinner.getCommittedIntValue();
                previewData.stairsHoldTime = stairsHoldTimeSpinner.getCommittedIntValue();
                previewData.stairsDuration = stairsDurationSpinner.getCommittedIntValue();
                break;
        }

        previewPanel.setPreviewData(previewData);
        previewPanel.repaint();
    }

    // 回填数据
    public void setThreadGroupData(PerformanceTreeNode node) {
        this.currentNode = node;
        ThreadGroupData data = node.threadGroupData;
        if (data == null) {
            data = new ThreadGroupData();
            node.threadGroupData = data;
        }
        data.normalize();

        // 设置模式
        modeComboBox.setSelectedItem(data.threadMode);
        cardLayout.show(cardPanel, data.threadMode.name());

        // 设置固定模式参数
        fixedNumThreadsSpinner.setValue(data.numThreads);
        fixedLoopsSpinner.setValue(data.loops);
        useTimeCheckBox.setSelected(data.useTime);
        useLoopCountButton.setSelected(!data.useTime);
        durationSpinner.setValue(data.duration);
        maxInFlightWaitSpinner.setValue(data.maxInFlightWaitSeconds);

        // 更新UI状态
        updateFixedExecutionModeState();
        updateMaxInFlightWaitState();

        // 设置递增模式参数
        rampUpStartThreadsSpinner.setValue(data.rampUpStartThreads);
        rampUpEndThreadsSpinner.setValue(data.rampUpEndThreads);
        rampUpTimeSpinner.setValue(data.rampUpTime);
        rampUpDurationSpinner.setValue(data.rampUpDuration);

        // 设置尖刺模式参数
        spikeMinThreadsSpinner.setValue(data.spikeMinThreads);
        spikeMaxThreadsSpinner.setValue(data.spikeMaxThreads);
        spikeRampUpTimeSpinner.setValue(data.spikeRampUpTime);
        spikeHoldTimeSpinner.setValue(data.spikeHoldTime);
        spikeRampDownTimeSpinner.setValue(data.spikeRampDownTime);
        spikeDurationSpinner.setValue(data.spikeDuration);

        // 设置阶梯模式参数
        stairsStartThreadsSpinner.setValue(data.stairsStartThreads);
        stairsEndThreadsSpinner.setValue(data.stairsEndThreads);
        stairsStepSpinner.setValue(data.stairsStep);
        stairsHoldTimeSpinner.setValue(data.stairsHoldTime);
        stairsDurationSpinner.setValue(data.stairsDuration);

        // 更新预览图
        updatePreview();
    }

    /**
     * 强制提交所有 EasyJSpinner 的值
     * 用于 Ctrl/Cmd+S 保存时，确保所有输入都已提交
     */
    public void forceCommitAllSpinners() {
        List<EasyJSpinner> allSpinners = Arrays.asList(
                fixedNumThreadsSpinner, fixedLoopsSpinner, durationSpinner, maxInFlightWaitSpinner,
                rampUpStartThreadsSpinner, rampUpEndThreadsSpinner,
                rampUpTimeSpinner, rampUpDurationSpinner,
                spikeMinThreadsSpinner, spikeMaxThreadsSpinner,
                spikeRampUpTimeSpinner, spikeHoldTimeSpinner,
                spikeRampDownTimeSpinner, spikeDurationSpinner,
                stairsStartThreadsSpinner, stairsEndThreadsSpinner,
                stairsStepSpinner, stairsHoldTimeSpinner, stairsDurationSpinner
        );

        allSpinners.forEach(EasyJSpinner::forceCommit);
    }

    public void saveThreadGroupData() {
        if (currentNode == null) return;
        ThreadGroupData data = currentNode.threadGroupData;
        if (data == null) {
            data = new ThreadGroupData();
            currentNode.threadGroupData = data;
        }

        // 保存模式
        data.threadMode = (ThreadGroupData.ThreadMode) modeComboBox.getSelectedItem();

        // 保存固定模式参数
        data.numThreads = fixedNumThreadsSpinner.getCommittedIntValue();
        data.loops = fixedLoopsSpinner.getCommittedIntValue();
        data.useTime = useTimeCheckBox.isSelected();
        data.duration = durationSpinner.getCommittedIntValue();
        data.maxInFlightWaitSeconds = maxInFlightWaitSpinner.getCommittedIntValue();

        // 保存递增模式参数
        data.rampUpStartThreads = rampUpStartThreadsSpinner.getCommittedIntValue();
        data.rampUpEndThreads = rampUpEndThreadsSpinner.getCommittedIntValue();
        data.rampUpTime = rampUpTimeSpinner.getCommittedIntValue();
        data.rampUpDuration = rampUpDurationSpinner.getCommittedIntValue();

        // 保存尖刺模式参数
        data.spikeMinThreads = spikeMinThreadsSpinner.getCommittedIntValue();
        data.spikeMaxThreads = spikeMaxThreadsSpinner.getCommittedIntValue();
        data.spikeRampUpTime = spikeRampUpTimeSpinner.getCommittedIntValue();
        data.spikeHoldTime = spikeHoldTimeSpinner.getCommittedIntValue();
        data.spikeRampDownTime = spikeRampDownTimeSpinner.getCommittedIntValue();
        data.spikeDuration = spikeDurationSpinner.getCommittedIntValue();

        // 保存阶梯模式参数
        data.stairsStartThreads = stairsStartThreadsSpinner.getCommittedIntValue();
        data.stairsEndThreads = stairsEndThreadsSpinner.getCommittedIntValue();
        data.stairsStep = stairsStepSpinner.getCommittedIntValue();
        data.stairsHoldTime = stairsHoldTimeSpinner.getCommittedIntValue();
        data.stairsDuration = stairsDurationSpinner.getCommittedIntValue();
        data.normalize();
    }

    private static final class ThreadModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ThreadGroupData.ThreadMode threadMode) {
                setText(I18nUtil.getMessage(threadMode.getMessageKey()));
            }
            return component;
        }
    }

    private JPanel createExecutionModePanel() {
        return executionModeBar;
    }

    private void updateFixedExecutionModeState() {
        boolean useTime = useTimeCheckBox.isSelected();
        fixedLoopsSpinner.setEnabled(!useTime);
        durationSpinner.setEnabled(useTime);
    }

    private void showThreadMode(ThreadGroupData.ThreadMode mode) {
        JPanel visiblePanel = switch (mode) {
            case FIXED -> fixedPanel;
            case RAMP_UP -> rampUpPanel;
            case SPIKE -> spikePanel;
            case STAIRS -> stairsPanel;
        };
        cardLayout.show(cardPanel, mode.name());
        cardPanel.setPreferredSize(new Dimension(
                CONFIG_PANEL_WIDTH,
                visiblePanel.getPreferredSize().height
        ));
        cardPanel.revalidate();
    }

    private void updateMaxInFlightWaitState() {
        ThreadGroupData.ThreadMode mode = (ThreadGroupData.ThreadMode) modeComboBox.getSelectedItem();
        boolean enabled = mode != null
                && (mode != ThreadGroupData.ThreadMode.FIXED || useTimeCheckBox.isSelected());
        maxInFlightWaitLabel.setEnabled(enabled);
        maxInFlightWaitSpinner.setEnabled(enabled);
    }

    private static String trimFieldLabel(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[:：]\\s*$", "");
    }

    // 预览数据模型
    private static class ThreadLoadPreviewData {
        ThreadGroupData.ThreadMode mode;
        // 固定模式
        int fixedThreads;
        boolean useTime;
        int duration;
        int loops;
        // 递增模式
        int rampUpStartThreads;
        int rampUpEndThreads;
        int rampUpTime;
        int rampUpDuration;
        // 尖刺模式
        int spikeMinThreads;
        int spikeMaxThreads;
        int spikeRampUpTime;
        int spikeHoldTime;
        int spikeRampDownTime;
        int spikeDuration;
        // 阶梯模式
        int stairsStartThreads;
        int stairsEndThreads;
        int stairsStep;
        int stairsHoldTime;
        int stairsDuration;
    }

    // 预览面板实现
    private static class ThreadLoadPreviewPanel extends JPanel {
        private ThreadLoadPreviewData previewData;
        private static final int PADDING = 40;

        public void setPreviewData(ThreadLoadPreviewData data) {
            this.previewData = data;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (previewData == null) return;

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 2 * PADDING;
            int height = getHeight() - 2 * PADDING;

            // 绘制网格和坐标轴
            drawGrid(g2d, width, height);

            // 根据不同模式绘制曲线
            drawCurve(g2d, width, height);

            g2d.dispose();
        }

        /**
         * 生成优美刻度（nice ticks），如1-2-5-10-20等
         *
         * @param min      最小值
         * @param max      最大值
         * @param maxTicks 最多刻度数
         * @return 刻度数组
         */
        private static java.util.List<Integer> getNiceTicks(int min, int max, int maxTicks) {
            java.util.List<Integer> ticks = new java.util.ArrayList<>();
            if (max <= min || maxTicks <= 0) return ticks;
            int range = max - min;
            if (range == 0) {
                ticks.add(min);
                return ticks;
            }
            // 计算优美间隔
            double rawStep = (double) range / maxTicks;
            double mag = Math.pow(10, Math.floor(Math.log10(rawStep)));
            double[] niceSteps = {1, 2, 5, 10};
            double niceStep = niceSteps[0] * mag;
            for (double s : niceSteps) {
                if (rawStep <= s * mag) {
                    niceStep = s * mag;
                    break;
                }
            }
            // 防止niceStep为0或过小，避免死循环
            if (niceStep < 1) niceStep = 1;
            int niceStepInt = (int) niceStep; // Convert to int to avoid implicit narrowing in compound assignment
            int niceMin = (int) (Math.floor(min / niceStep) * niceStep);
            int niceMax = (int) (Math.ceil(max / niceStep) * niceStep);
            int maxLoop = 1000; // 最大循环次数保护
            for (int v = niceMin, count = 0; v <= niceMax && count < maxLoop; v += niceStepInt, count++) {
                if (v >= min && v <= max) {
                    ticks.add(v);
                }
            }
            if (!ticks.contains(max)) ticks.add(max);
            return ticks;
        }

        private void drawGrid(Graphics2D g2d, int width, int height) {
            Color gridColor = UIManager.getColor("Performance.chart.gridColor");
            Color textColor = UIManager.getColor("Performance.chart.textColor");
            Color axisColor = UIManager.getColor("Performance.chart.axisColor");

            g2d.setColor(gridColor);
            g2d.setStroke(new BasicStroke(0.5f));

            // Y轴优美刻度
            int maxThreads = getMaxThreads();
            java.util.List<Integer> yTicks = getNiceTicks(0, maxThreads, 5);
            for (int i = 0; i < yTicks.size(); i++) {
                int threadValue = yTicks.get(i);
                int y = PADDING + height - (int) ((double) threadValue / maxThreads * height);
                g2d.draw(new java.awt.geom.Line2D.Double(PADDING, y, PADDING + width, y));
                if (threadValue > 0) {
                    g2d.setColor(textColor);
                    g2d.drawString(String.valueOf(threadValue), PADDING - 30, y + 5);
                    g2d.setColor(gridColor);
                }
            }

            // X轴优美刻度
            int duration = getDuration();
            java.util.List<Integer> xTicks = getNiceTicks(0, duration, 10);
            for (int i = 0; i < xTicks.size(); i++) {
                int timeValue = xTicks.get(i);
                int x = PADDING + (int) ((double) timeValue / duration * width);
                g2d.draw(new java.awt.geom.Line2D.Double(x, PADDING, x, PADDING + height));
                if (timeValue > 0) {
                    g2d.setColor(textColor);
                    g2d.drawString(timeValue + "s", x - 10, PADDING + height + 15);
                    g2d.setColor(gridColor);
                }
            }

            // 坐标轴
            g2d.setColor(axisColor);
            g2d.setStroke(new BasicStroke(1.5f));

            // X轴
            g2d.draw(new Line2D.Double(PADDING, PADDING + height, PADDING + width, PADDING + height));
            // Y轴
            g2d.draw(new Line2D.Double(PADDING, PADDING, PADDING, PADDING + height));

            // 坐标轴标签
            g2d.setColor(textColor);
            g2d.drawString(I18nUtil.getMessage(MessageKeys.THREADGROUP_PREVIEW_TIME_SECONDS), PADDING + width / 2 - 20, PADDING + height + 30);

            // 在左上角添加模式信息
            g2d.drawString(I18nUtil.getMessage(MessageKeys.THREADGROUP_PREVIEW_MODE_PREFIX)
                    + " "
                    + I18nUtil.getMessage(previewData.mode.getMessageKey()), PADDING, PADDING - 10);
        }

        private int getMaxThreads() {

            // 根据当前模式计算实际使用的最大线程数
            int actualMax;
            switch (previewData.mode) {
                case FIXED:
                    actualMax = previewData.fixedThreads;
                    break;
                case RAMP_UP:
                    actualMax = Math.max(previewData.rampUpStartThreads, previewData.rampUpEndThreads);
                    break;
                case SPIKE:
                    actualMax = previewData.spikeMaxThreads;
                    break;
                case STAIRS:
                    actualMax = previewData.stairsEndThreads;
                    break;
                default:
                    actualMax = 20; // 默认值
            }
            // 增加50% 的安全余量
            return (int) (actualMax * 1.5);
        }

        private int getDuration() {
            // 根据当前模式返回持续时间（用于X轴刻度）
            switch (previewData.mode) {
                case FIXED:
                    return previewData.useTime ? previewData.duration : 60;
                case RAMP_UP:
                    return previewData.rampUpDuration;
                case SPIKE:
                    return previewData.spikeDuration;
                case STAIRS:
                    return previewData.stairsDuration;
                default:
                    return 60;
            }
        }

        private void drawCurve(Graphics2D g2d, int width, int height) {
            Color curveColor = UIManager.getColor("Performance.chart.curveColor");
            g2d.setColor(curveColor);
            g2d.setStroke(new BasicStroke(2.0f));

            List<Point> points = new ArrayList<>();

            switch (previewData.mode) {
                case FIXED:
                    drawFixedCurve(points, width, height);
                    break;
                case RAMP_UP:
                    drawRampUpCurve(points, width, height);
                    break;
                case SPIKE:
                    drawSpikeCurve(points, width, height);
                    break;
                case STAIRS:
                    drawStairsCurve(points, width, height);
                    break;
            }

            // 绘制线段
            if (points.size() >= 2) {
                for (int i = 0; i < points.size() - 1; i++) {
                    Point p1 = points.get(i);
                    Point p2 = points.get(i + 1);
                    g2d.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
                }
            }

            // 绘制点
            Color bgColor = ModernColors.getCardBackgroundColor();
            g2d.setColor(bgColor);
            g2d.setStroke(new BasicStroke(1.0f));
            for (Point p : points) {
                g2d.fill(new Rectangle2D.Double((double) p.x - 3, (double) p.y - 3, 6, 6));
                g2d.setColor(curveColor);
                g2d.draw(new Rectangle2D.Double((double) p.x - 3, (double) p.y - 3, 6, 6));
                g2d.setColor(bgColor);
            }
        }

        private void drawFixedCurve(List<Point> points, int width, int height) {
            int maxThreads = getMaxThreads();

            int x1 = PADDING;
            int y2 = PADDING + height - height * previewData.fixedThreads / maxThreads;
            if (y2 < PADDING) y2 = PADDING + 5;
            points.add(new Point(x1, y2));

            x1 = PADDING + width;
            points.add(new Point(x1, y2));
        }

        private void drawRampUpCurve(List<Point> points, int width, int height) {
            int maxThreads = getMaxThreads();
            int duration = getDuration();

            int x1 = PADDING;
            int y2 = PADDING + height - height * previewData.rampUpStartThreads / maxThreads;
            if (y2 < PADDING) y2 = PADDING + 5;
            points.add(new Point(x1, y2));

            int rampUpEndX = PADDING + (width * previewData.rampUpTime / duration);
            int y3 = PADDING + height - height * previewData.rampUpEndThreads / maxThreads;
            if (y3 < PADDING) y3 = PADDING + 5;
            points.add(new Point(rampUpEndX, y3));

            points.add(new Point(PADDING + width, y3));
        }

        private void drawSpikeCurve(List<Point> points, int width, int height) {
            int maxThreads = getMaxThreads();
            int duration = getDuration();

            int x = PADDING;
            int yMin = PADDING + height - height * previewData.spikeMinThreads / maxThreads;
            if (yMin < PADDING) yMin = PADDING + 5;
            int yMax = PADDING + height - height * previewData.spikeMaxThreads / maxThreads;
            if (yMax < PADDING) yMax = PADDING + 5;


            // 最小线程
            points.add(new Point(x, yMin));

            // 计算时间比例
            int availWidth = width;

            // 上升
            x = PADDING + (availWidth * previewData.spikeRampUpTime / duration);
            points.add(new Point(x, yMax));

            // 保持
            x = PADDING + (availWidth * (previewData.spikeRampUpTime + previewData.spikeHoldTime) / duration);
            points.add(new Point(x, yMax));

            // 下降
            x = PADDING + (availWidth * (previewData.spikeRampUpTime + previewData.spikeHoldTime + previewData.spikeRampDownTime) / duration);
            points.add(new Point(x, yMin));

            points.add(new Point(PADDING + width, yMin));
        }

        private void drawStairsCurve(List<Point> points, int width, int height) {
            int maxThreads = getMaxThreads();
            int duration = previewData.stairsDuration; // 总持续时间

            int x = PADDING;
            int startThreads = previewData.stairsStartThreads;
            int endThreads = previewData.stairsEndThreads;
            int step = previewData.stairsStep;
            int holdTime = previewData.stairsHoldTime;

            int currentThreads = startThreads;
            int time = 0;

            // 计算y坐标
            int y = PADDING + height - height * currentThreads / maxThreads;
            if (y < PADDING) y = PADDING + 5;
            points.add(new Point(x, y));

            while (time < duration && currentThreads < endThreads) {
                // 计算下一个阶梯的时间
                int nextTime = time + holdTime;
                if (nextTime > duration) nextTime = duration;

                // 计算x坐标
                int nextX = PADDING + (width * nextTime / duration);
                // 水平线：用户数不变，x推进
                points.add(new Point(nextX, y));

                // 竖线：用户数递增
                currentThreads += step;
                if (currentThreads > endThreads) currentThreads = endThreads;
                int nextY = PADDING + height - height * currentThreads / maxThreads;
                if (nextY < PADDING) nextY = PADDING + 5;
                points.add(new Point(nextX, nextY));

                // 推进时间
                time = nextTime;
                y = nextY;
                x = nextX;
            }

            // 如果最后一个点未到duration，补一段水平线到结束
            int endX = PADDING + width;
            if (x < endX) {
                points.add(new Point(endX, y));
            }
        }
    }
}
