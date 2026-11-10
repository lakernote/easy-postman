package com.laker.postman.common.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatComboBoxUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import com.laker.postman.util.FontsUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.plaf.ComboBoxUI;
import java.awt.*;

/**
 * 增强型下拉框组件
 * <p>
 * 特性：
 * - 根据当前选中项动态调整宽度，节省空间，可限制最小/最大宽度（超出最大宽度时 tooltip 显示完整内容）
 * - 支持固定宽度模式
 * - 自动适配 FlatLaf 主题
 * - 统一的字体和样式
 * </p>
 *
 * @param <E> 下拉框项的类型
 */
public class EasyComboBox<E> extends JComboBox<E> {

    /**
     * 宽度模式
     */
    public enum WidthMode {
        /**
         * 根据当前选中项动态调整宽度
         */
        DYNAMIC,
        /**
         * 根据所有选项中最长的项固定宽度
         */
        FIXED_MAX,
        /**
         * 手动指定固定宽度
         */
        FIXED_CUSTOM
    }

    private static final int DEFAULT_MIN_WIDTH = 80;

    private WidthMode widthMode = WidthMode.DYNAMIC;
    private int customWidth = -1;
    /** DYNAMIC 模式宽度下限（未缩放），≤0 表示使用默认行为 */
    private int minDynamicWidth = -1;
    /** DYNAMIC 模式宽度上限（未缩放），≤0 表示不限制 */
    private int maxDynamicWidth = -1;
    /** 固定高度（未缩放），≤0 表示使用 LAF 计算高度 */
    private int fixedHeight = -1;

    /** model 内容变化计数，用于判断尺寸缓存是否过期 */
    private int modelVersion;
    private JList<E> measureList;
    private SizeKey cachedKey;
    private DynamicLayout cachedLayout;

    /**
     * 创建默认的动态宽度下拉框
     */
    public EasyComboBox() {
        this(WidthMode.DYNAMIC);
    }

    /**
     * 创建指定宽度模式的下拉框
     *
     * @param widthMode 宽度模式
     */
    public EasyComboBox(WidthMode widthMode) {
        super();
        this.widthMode = widthMode;
        initComponent();
    }

    /**
     * 创建指定宽度模式和选项的下拉框
     *
     * @param items     下拉框选项
     * @param widthMode 宽度模式
     */
    public EasyComboBox(E[] items, WidthMode widthMode) {
        super(items);
        this.widthMode = widthMode;
        initComponent();
    }

    /**
     * 创建动态宽度的下拉框
     *
     * @param items 下拉框选项
     */
    public EasyComboBox(E[] items) {
        this(items, WidthMode.DYNAMIC);
    }

    /**
     * 创建指定固定宽度的下拉框
     *
     * @param items 下拉框选项
     * @param width 固定宽度（像素）
     */
    public EasyComboBox(E[] items, int width) {
        super(items);
        this.widthMode = WidthMode.FIXED_CUSTOM;
        this.customWidth = width;
        initComponent();
    }

    private void initComponent() {
        setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        setFocusable(false);
        // 选中项变化时 BasicComboBoxUI 不会重新布局，DYNAMIC 模式需手动触发
        addItemListener(e -> {
            if (isDynamicSizing()) {
                revalidate();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        if (widthMode == WidthMode.FIXED_CUSTOM && customWidth > 0) {
            return new Dimension(customWidth, resolveHeight(super.getPreferredSize().height));
        }
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        if (widthMode == WidthMode.FIXED_MAX) {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, resolveHeight(size.height));
        }
        DynamicLayout layout = dynamicLayout();
        return new Dimension(layout.width(), layout.height());
    }

    @Override
    public Dimension getMaximumSize() {
        // BoxLayout 会用 getMaximumSize() 限制组件，让它等于 preferredSize 即可
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = getPreferredSize();
        int min = minDynamicWidth > 0 ? UIScale.scale(minDynamicWidth) : DEFAULT_MIN_WIDTH;
        return new Dimension(Math.min(min, d.width), d.height);
    }

    /**
     * 选中项因最大宽度被截断时显示完整内容，否则显示外部设置的 tooltip。
     */
    @Override
    public String getToolTipText() {
        if (isDynamicSizing() && dynamicLayout().truncated()) {
            return displayText(selectedElement());
        }
        return super.getToolTipText();
    }

    @Override
    public void setToolTipText(String text) {
        super.setToolTipText(text);
        // super 在 text 为 null 时会注销 ToolTipManager，截断提示仍需要它
        registerToolTipIfNeeded();
    }

    /**
     * 设置 DYNAMIC 模式的宽度范围（未缩放像素，按 UI 缩放）。
     * 设置了最大宽度后，选中项被截断时 tooltip 显示完整内容。
     */
    public void setDynamicWidthRange(int minWidth, int maxWidth) {
        this.minDynamicWidth = minWidth;
        this.maxDynamicWidth = maxWidth;
        registerToolTipIfNeeded();
        revalidate();
    }

    /**
     * 设置固定高度（未缩放像素，按 UI 缩放），≤0 恢复为 LAF 计算的高度。
     */
    public void setFixedHeight(int height) {
        this.fixedHeight = height;
        revalidate();
    }

    /**
     * 设置宽度模式
     *
     * @param widthMode 宽度模式
     */
    public void setWidthMode(WidthMode widthMode) {
        this.widthMode = widthMode;
        revalidate();
    }

    /**
     * 设置自定义固定宽度
     *
     * @param width 宽度（像素）
     */
    public void setCustomWidth(int width) {
        this.widthMode = WidthMode.FIXED_CUSTOM;
        this.customWidth = width;
        revalidate();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // 主题切换后重建测量用 JList，使其 UI 与当前主题一致
        measureList = null;
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        modelVersion++;
        super.contentsChanged(e);
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        modelVersion++;
        super.intervalAdded(e);
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        modelVersion++;
        super.intervalRemoved(e);
    }

    private boolean isDynamicSizing() {
        return widthMode != WidthMode.FIXED_MAX
                && !(widthMode == WidthMode.FIXED_CUSTOM && customWidth > 0)
                && !isPreferredSizeSet();
    }

    private void registerToolTipIfNeeded() {
        if (maxDynamicWidth > 0) {
            ToolTipManager.sharedInstance().registerComponent(this);
        }
    }

    /**
     * 按需计算并缓存 DYNAMIC 尺寸。缓存以影响尺寸的状态快照为键，
     * 不依赖事件失效，因此组件未加入容器、主题切换等场景下也不会过期，且不会在 UI 安装过程中提前计算。
     */
    private DynamicLayout dynamicLayout() {
        SizeKey key = new SizeKey(getUI(), getFont(), getRenderer(), getPrototypeDisplayValue(), getBorder(),
                getModel(), modelVersion, getSelectedItem(), minDynamicWidth, maxDynamicWidth, fixedHeight,
                UIScale.getUserScaleFactor(), getClientProperty(FlatClientProperties.STYLE),
                getClientProperty(FlatClientProperties.STYLE_CLASS),
                getClientProperty(FlatClientProperties.MINIMUM_WIDTH));
        if (!key.equals(cachedKey) || cachedLayout == null) {
            cachedLayout = calcDynamicLayout();
            cachedKey = key;
        }
        return cachedLayout;
    }

    /**
     * super 首选宽度 = max(参照宽度（prototype 或最宽项）+ 装饰（内边距、箭头按钮、边框），LAF 最小宽度)，
     * 据此推导装饰宽度，无需临时替换 model。super 已被 LAF 最小宽度兜底时，任一选项宽度也都不超过该值，直接取兜底值。
     * 设置了 prototype 时沿用其宽度（JComboBox 语义），否则按选中项宽度，最后限制在 [min, max] 区间。
     */
    private DynamicLayout calcDynamicLayout() {
        Dimension superSize = super.getPreferredSize();
        int height = resolveHeight(superSize.height);
        ListCellRenderer<? super E> renderer = getRenderer();
        if (renderer == null || getUI() == null) {
            return new DynamicLayout(clampDynamicWidth(superSize.width), height, false);
        }

        E prototype = getPrototypeDisplayValue();
        int referenceWidth = prototype != null ? measureWidth(prototype) : widestItemWidth();
        int lafMinWidth = lafMinimumWidth();
        E selected = selectedElement();
        int selectedNaturalWidth;
        if (superSize.width <= lafMinWidth) {
            selectedNaturalWidth = superSize.width;
        } else {
            int decoration = Math.max(0, superSize.width - referenceWidth);
            int contentWidth = selected == null ? 0 : measureWidth(selected) + decoration;
            selectedNaturalWidth = Math.max(contentWidth, lafMinWidth);
        }

        int width = clampDynamicWidth(prototype != null ? superSize.width : selectedNaturalWidth);
        return new DynamicLayout(width, height, selectedNaturalWidth > width);
    }

    /**
     * FlatLaf 对下拉框有最小宽度（ComboBox.minimumWidth，可被 style / JComponent.minimumWidth 覆盖），
     * 与 FlatComboBoxUI#getMinimumSize 保持一致；其它 LAF 无此限制。
     */
    private int lafMinimumWidth() {
        if (!(getUI() instanceof FlatComboBoxUI flatUI)) {
            return 0;
        }
        int minimumWidth;
        try {
            minimumWidth = ((Number) flatUI.getStyleableValue(this, "minimumWidth")).intValue();
        } catch (RuntimeException e) {
            minimumWidth = UIManager.getInt("ComboBox.minimumWidth");
        }
        int focusWidth = Math.round(FlatUIUtils.getBorderFocusWidth(this) * 2);
        return UIScale.scale(FlatUIUtils.minimumWidth(this, minimumWidth)) + focusWidth;
    }

    private int widestItemWidth() {
        int widest = 0;
        for (int i = 0; i < getItemCount(); i++) {
            widest = Math.max(widest, measureWidth(getItemAt(i)));
        }
        return widest;
    }

    private int clampDynamicWidth(int width) {
        if (maxDynamicWidth > 0) {
            width = Math.min(width, UIScale.scale(maxDynamicWidth));
        }
        if (minDynamicWidth > 0) {
            width = Math.max(width, UIScale.scale(minDynamicWidth));
        }
        return width;
    }

    private int resolveHeight(int lafHeight) {
        return fixedHeight > 0 ? UIScale.scale(fixedHeight) : lafHeight;
    }

    private int measureWidth(E value) {
        return renderComponent(value).getPreferredSize().width;
    }

    private String displayText(E value) {
        return renderComponent(value) instanceof JLabel label ? label.getText() : String.valueOf(value);
    }

    private Component renderComponent(E value) {
        if (measureList == null) {
            measureList = new JList<>();
        }
        measureList.setFont(getFont());
        return getRenderer().getListCellRendererComponent(measureList, value, -1, false, false);
    }

    @SuppressWarnings("unchecked")
    private E selectedElement() {
        return (E) getSelectedItem();
    }

    private record SizeKey(ComboBoxUI ui, Font font, ListCellRenderer<?> renderer, Object prototype, Border border,
                           ComboBoxModel<?> model, int modelVersion, Object selected,
                           int minWidth, int maxWidth, int fixedHeight, float scale,
                           Object style, Object styleClass, Object minimumWidthProperty) {
    }

    private record DynamicLayout(int width, int height, boolean truncated) {
    }
}
