package com.laker.postman.panel.collections.editor.request.sub;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

/**
 * A plain-text log with a painted icon gutter. Icons never enter selection or clipboard text.
 */
final class NetworkLogTextPane extends JTextPane {
    private static final String STAGE_ICON = NetworkLogTextPane.class.getName() + ".stageIcon";
    private static final int ICON_GAP = 8;

    NetworkLogTextPane() {
        setEditorKit(new LogEditorKit());
        setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12),
                UIScale.scale(8), UIScale.scale(12)));
    }

    void styleEntry(int startOffset, NetworkLogPresentation.Style presentation, int fontSize) {
        StyledDocument document = getStyledDocument();
        int iconSize = Math.max(1, UIScale.unscale(Math.max(8, fontSize)));
        Icon icon = createIcon(presentation, iconSize);
        int iconWidth = icon == null ? UIScale.scale(iconSize) : icon.getIconWidth();
        SimpleAttributeSet paragraphStyle = new SimpleAttributeSet();
        StyleConstants.setLeftIndent(paragraphStyle, iconWidth + UIScale.scale(ICON_GAP));
        StyleConstants.setFirstLineIndent(paragraphStyle, 0);
        StyleConstants.setSpaceAbove(paragraphStyle, 0);
        paragraphStyle.addAttribute(STAGE_ICON, Boolean.FALSE);
        document.setParagraphAttributes(startOffset, document.getLength() - startOffset, paragraphStyle, false);
        document.setParagraphAttributes(document.getLength(), 0, paragraphStyle, false);

        if (icon != null) {
            SimpleAttributeSet headingStyle = new SimpleAttributeSet();
            headingStyle.addAttribute(STAGE_ICON, icon);
            document.setParagraphAttributes(startOffset, 0, headingStyle, false);
        }
        repaint();
    }

    void clearDecoration() {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        attributes.addAttribute(STAGE_ICON, Boolean.FALSE);
        getStyledDocument().setParagraphAttributes(0, 0, attributes, false);
        repaint();
    }

    private static Icon createIcon(NetworkLogPresentation.Style presentation, int size) {
        String path = switch (presentation.symbol()) {
            case "▶" -> "icons/start.svg";
            case "■", "⏹" -> "icons/stop.svg";
            case "✅" -> "icons/check.svg";
            case "❌" -> "icons/x.svg";
            case "⚠️" -> "icons/warning.svg";
            case "↑" -> "icons/upload.svg";
            case "↓" -> "icons/download.svg";
            case "↪" -> "icons/corner-down-right.svg";
            case "🌐" -> "icons/globe.svg";
            case "🔍", "📍" -> "icons/search.svg";
            case "🔌", "🔗", "↩" -> "icons/connect.svg";
            case "🔒" -> "icons/lock.svg";
            case "💾" -> "icons/save.svg";
            case "🔁" -> "icons/refresh.svg";
            case "⏳" -> "icons/clock.svg";
            default -> null;
        };
        if (path == null) {
            return null;
        }
        // One icon per heading, reused across repaints; resolve semantic colors when painting.
        return IconUtil.create(path, size, size).setColorFilter(
                new FlatSVGIcon.ColorFilter(ignored -> presentation.color()));
    }

    private static final class LogEditorKit extends StyledEditorKit {
        private final ViewFactory factory = element -> AbstractDocument.ParagraphElementName.equals(element.getName())
                ? new LogParagraphView(element) : super.getViewFactory().create(element);

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    private static final class LogParagraphView extends ParagraphView {
        LogParagraphView(Element element) {
            super(element);
        }

        @Override
        public void paint(Graphics graphics, Shape allocation) {
            super.paint(graphics, allocation);
            Object value = getAttributes().getAttribute(STAGE_ICON);
            if (!(value instanceof Icon icon) || getViewCount() == 0
                    || getStartOffset() >= getDocument().getLength()) {
                return;
            }
            Shape firstRow = getChildAllocation(0, allocation);
            if (firstRow == null) {
                return;
            }
            Rectangle rowBounds = firstRow.getBounds();
            int x = allocation.getBounds().x;
            int y = rowBounds.y + Math.max(0, (rowBounds.height - icon.getIconHeight()) / 2);
            icon.paintIcon(getContainer(), graphics, x, y);
        }
    }
}
