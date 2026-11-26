package com.laker.postman.common.component;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;

/**
 * RSyntaxTextArea that resolves one layout font for each token.
 *
 * <p>If the token's syntax font cannot display its complete text, the configured editor fallback
 * font is used for the entire token. If that font cannot display it either (for example a CJK
 * fallback font with Arabic text), the JDK logical {@link Font#DIALOG} font is used because it
 * follows the platform's composite font chain. Resolving at the text-area level is important: native token
 * painting, caret placement, mouse hit-testing, wrapping, and horizontal scrolling all consume the
 * same token font metrics.</p>
 */
public class FallbackAwareRSyntaxTextArea extends RSyntaxTextArea {

    private Font cachedFallbackBase;
    private float cachedFallbackSize = Float.NaN;
    private final Font[] derivedFallbackFonts = new Font[4];
    private float cachedSystemFallbackSize = Float.NaN;
    private final Font[] derivedSystemFallbackFonts = new Font[4];
    /** 当前文本是否有 token 使用了系统 Dialog 兜底字体；为 true 时行高需同时容纳该字体。 */
    private boolean systemFallbackInUse;

    public FallbackAwareRSyntaxTextArea() {
        super();
        installFallbackFontListener();
    }

    public FallbackAwareRSyntaxTextArea(int rows, int columns) {
        super(rows, columns);
        installFallbackFontListener();
    }

    @Override
    public Font getFontForToken(Token token) {
        if (token == null) {
            return getFont();
        }
        if (token instanceof ResolvedFontToken resolvedToken && resolvedToken.resolvedFont() != null) {
            return resolvedToken.resolvedFont();
        }

        Font primaryFont = super.getFontForToken(token);
        if (canDisplayToken(primaryFont, token)) {
            return primaryFont;
        }

        Font fallbackFont = resolveFallbackFont(primaryFont);
        if (fallbackFont != null && canDisplayToken(fallbackFont, token)) {
            return fallbackFont;
        }

        // 配置的回退字体（如微软雅黑）缺少阿拉伯语等文字时，使用 JDK 逻辑字体的系统级字体链兜底
        Font systemFallbackFont = resolveSystemFallbackFont(primaryFont);
        if (!canDisplayToken(systemFallbackFont, token)) {
            return primaryFont;
        }
        markSystemFallbackInUse();
        return systemFallbackFont;
    }

    @Override
    public void setText(String text) {
        boolean hadSystemFallback = systemFallbackInUse;
        long before = hadSystemFallback ? lineMetrics(fallbackFontProperty(), true) : 0;
        systemFallbackInUse = false;
        super.setText(text);
        if (hadSystemFallback && lineMetrics(fallbackFontProperty(), false) != before) {
            lineHeightsChanged();
        }
    }

    @Override
    public void setDocument(Document document) {
        boolean hadSystemFallback = systemFallbackInUse;
        long before = hadSystemFallback ? lineMetrics(fallbackFontProperty(), true) : 0;
        systemFallbackInUse = false;
        super.setDocument(document);
        if (hadSystemFallback && lineMetrics(fallbackFontProperty(), false) != before) {
            lineHeightsChanged();
        }
    }

    @Override
    public FontMetrics getFontMetricsForToken(Token token) {
        return getFontMetrics(getFontForToken(token));
    }

    @Override
    public int getLineHeight() {
        int lineHeight = super.getLineHeight();
        FontMetrics fallbackMetrics = editorFallbackMetrics();
        if (fallbackMetrics != null) {
            lineHeight = Math.max(lineHeight, fallbackMetrics.getHeight());
        }
        FontMetrics systemMetrics = systemFallbackMetrics();
        return systemMetrics == null ? lineHeight : Math.max(lineHeight, systemMetrics.getHeight());
    }

    @Override
    public int getMaxAscent() {
        int maxAscent = super.getMaxAscent();
        FontMetrics fallbackMetrics = editorFallbackMetrics();
        if (fallbackMetrics != null) {
            maxAscent = Math.max(maxAscent, fallbackMetrics.getMaxAscent());
        }
        FontMetrics systemMetrics = systemFallbackMetrics();
        return systemMetrics == null ? maxAscent : Math.max(maxAscent, systemMetrics.getMaxAscent());
    }

    private void markSystemFallbackInUse() {
        if (systemFallbackInUse) {
            return;
        }
        Object fallbackValue = fallbackFontProperty();
        long before = lineMetrics(fallbackValue, false);
        systemFallbackInUse = true;
        if (lineMetrics(fallbackValue, true) != before) {
            // 通常在绘制过程中首次发现，延后刷新以按新行高重新布局
            SwingUtilities.invokeLater(this::lineHeightsChanged);
        }
    }

    /**
     * 行高变化后通知视图和行号栏重新计算。行号栏（LineNumberList）缓存了行高，
     * 只在字体或配色方案属性变化时刷新，因此借用配色方案变更事件触发，与修改配色方案时的刷新路径一致。
     * 该事件会让 RSyntaxTextAreaUI 重建全部视图，调用方须先确认行高或基线确实变化。
     */
    private void lineHeightsChanged() {
        firePropertyChange(SYNTAX_SCHEME_PROPERTY, null, getSyntaxScheme());
        revalidate();
        repaint();
    }

    private FontMetrics systemFallbackMetrics() {
        Font font = getFont();
        return systemFallbackInUse && font != null ? getFontMetrics(resolveSystemFallbackFont(font)) : null;
    }

    private void installFallbackFontListener() {
        // 回退字体本身变化（低频的设置操作）会改变 token 宽度和折行，与 setFont 一样需要重建视图；
        // 相同值不会触发属性事件
        addPropertyChangeListener(EditorFontProperties.FALLBACK_FONT_CLIENT_PROPERTY, ignored -> lineHeightsChanged());
    }

    private Object fallbackFontProperty() {
        return getClientProperty(EditorFontProperties.FALLBACK_FONT_CLIENT_PROPERTY);
    }

    /**
     * 按给定的回退字体属性和系统兜底状态计算行高与最大上升高度（高 32 位为行高，低 32 位为上升高度），
     * 与 {@link #getLineHeight()}/{@link #getMaxAscent()} 规则一致，用于判断是否需要刷新布局。
     */
    private long lineMetrics(Object fallbackValue, boolean systemInUse) {
        int lineHeight = super.getLineHeight();
        int maxAscent = super.getMaxAscent();
        Font font = getFont();
        if (font != null) {
            Font fallbackFont = fallbackValue instanceof Font fallbackBase ? deriveFallback(fallbackBase, font) : null;
            if (fallbackFont != null) {
                FontMetrics metrics = getFontMetrics(fallbackFont);
                lineHeight = Math.max(lineHeight, metrics.getHeight());
                maxAscent = Math.max(maxAscent, metrics.getMaxAscent());
            }
            if (systemInUse) {
                FontMetrics metrics = getFontMetrics(resolveSystemFallbackFont(font));
                lineHeight = Math.max(lineHeight, metrics.getHeight());
                maxAscent = Math.max(maxAscent, metrics.getMaxAscent());
            }
        }
        return ((long) lineHeight << 32) | (maxAscent & 0xFFFFFFFFL);
    }

    private Font deriveFallback(Font fallbackBase, Font primaryFont) {
        float size = primaryFont.getSize2D();
        int style = primaryFont.getStyle();
        Font fallbackFont = fallbackBase.getStyle() == style && Float.compare(fallbackBase.getSize2D(), size) == 0
                ? fallbackBase
                : fallbackBase.deriveFont(style, size);
        return samePhysicalFont(primaryFont, fallbackFont) ? null : fallbackFont;
    }

    private FontMetrics editorFallbackMetrics() {
        Font fallbackFont = resolveFallbackFont(getFont());
        return fallbackFont == null ? null : getFontMetrics(fallbackFont);
    }

    private boolean canDisplayToken(Font font, Token token) {
        if (font == null || token.length() <= 0) {
            return true;
        }
        int start = token.getTextOffset();
        try {
            return font.canDisplayUpTo(token.getTextArray(), start, start + token.length()) == -1;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private synchronized Font resolveSystemFallbackFont(Font primaryFont) {
        float size = primaryFont.getSize2D();
        if (Float.compare(size, cachedSystemFallbackSize) != 0) {
            cachedSystemFallbackSize = size;
            Arrays.fill(derivedSystemFallbackFonts, null);
        }
        int style = primaryFont.getStyle();
        Font systemFallbackFont = derivedSystemFallbackFonts[style];
        if (systemFallbackFont == null) {
            systemFallbackFont = new Font(Font.DIALOG, style, 1).deriveFont(size);
            derivedSystemFallbackFonts[style] = systemFallbackFont;
        }
        return systemFallbackFont;
    }

    private synchronized Font resolveFallbackFont(Font primaryFont) {
        Object value = getClientProperty(EditorFontProperties.FALLBACK_FONT_CLIENT_PROPERTY);
        if (!(value instanceof Font fallbackBase) || primaryFont == null) {
            return null;
        }

        float size = primaryFont.getSize2D();
        if (!fallbackBase.equals(cachedFallbackBase) || Float.compare(size, cachedFallbackSize) != 0) {
            cachedFallbackBase = fallbackBase;
            cachedFallbackSize = size;
            Arrays.fill(derivedFallbackFonts, null);
        }

        int style = primaryFont.getStyle();
        Font fallbackFont = derivedFallbackFonts[style];
        if (fallbackFont == null) {
            fallbackFont = fallbackBase.getStyle() == style
                    && Float.compare(fallbackBase.getSize2D(), size) == 0
                    ? fallbackBase
                    : fallbackBase.deriveFont(style, size);
            derivedFallbackFonts[style] = fallbackFont;
        }

        return samePhysicalFont(primaryFont, fallbackFont) ? null : fallbackFont;
    }

    private boolean samePhysicalFont(Font first, Font second) {
        return first.getFamily().equals(second.getFamily())
                && first.getStyle() == second.getStyle()
                && Float.compare(first.getSize2D(), second.getSize2D()) == 0;
    }

    /** Token slice whose font was already resolved from its complete source token. */
    interface ResolvedFontToken {
        Font resolvedFont();
    }
}
