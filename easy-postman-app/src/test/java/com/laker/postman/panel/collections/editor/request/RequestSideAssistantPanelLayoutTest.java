package com.laker.postman.panel.collections.editor.request;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.common.component.ToolWindowStripeMetrics;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.button.CopyButton;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.CollectionTreeRootRegistry;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.VariableResolver;
import com.laker.postman.service.variable.VariablesService;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RequestSideAssistantPanelLayoutTest extends AbstractSwingUiTest {

    @Test
    public void codeSnippetShouldResolveByDefaultAndRefreshOnVariableToggle() {
        AtomicInteger refreshCount = new AtomicInteger();
        RequestCodeSnippetPanel panel = new RequestCodeSnippetPanel(() -> { }, refreshCount::incrementAndGet);
        JCheckBox resolveVariables = findComponent(panel, JCheckBox.class);
        JComboBox<?> language = findComponent(panel, JComboBox.class);

        assertTrue(resolveVariables.isSelected());
        assertEquals(resolveVariables.getText(),
                I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_RESOLVE_VARIABLES));
        assertEquals(resolveVariables.getToolTipText(),
                I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_RESOLVE_VARIABLES_TOOLTIP));

        Map<String, String> previousVariables = VariablesService.getInstance().getCurrentContextMap();
        VariablesService.getInstance().attachContextMap(Map.of("__snippet_default_host__", "https://example.com"));
        try {
            HttpRequestItem item = new HttpRequestItem();
            item.setUrl("{{__snippet_default_host__}}/users");
            panel.updateRequest(item);
            assertTrue(findComponent(panel, RSyntaxTextArea.class).getText().contains("https://example.com/users"));
        } finally {
            VariablesService.getInstance().attachContextMap(previousVariables);
        }

        resolveVariables.doClick();
        assertFalse(resolveVariables.isSelected());
        assertEquals(refreshCount.get(), 1);

        language.setSelectedItem(RequestCodeSnippetLanguage.JAVA_OKHTTP);
        assertEquals(refreshCount.get(), 1);
        assertTrue(findComponent(panel, RSyntaxTextArea.class).getText().contains("{{__snippet_default_host__}}/users"));

        resolveVariables.doClick();
        assertTrue(resolveVariables.isSelected());
        assertEquals(refreshCount.get(), 2);
    }

    @Test
    public void assistantActionsShouldUseMatchingIconSizesAndClickAreas() {
        List<JPanel> panels = List.of(
                new RequestCodeSnippetPanel(() -> { }, () -> { }),
                new RequestVariablesInRequestPanel(() -> { }, () -> { }));
        Dimension actionSize = new Dimension(ToolWindowActionToolbar.ACTION_SIZE, ToolWindowActionToolbar.ACTION_SIZE);
        var referenceIcon = new CopyButton().getIcon();

        for (JPanel panel : panels) {
            List<JButton> buttons = collectToolbarButtons((Container) panel.getComponent(0));
            assertEquals(buttons.size(), panel instanceof RequestCodeSnippetPanel ? 3 : 2);
            for (JButton button : buttons) {
                assertEquals(button.getIcon().getIconWidth(), referenceIcon.getIconWidth());
                assertEquals(button.getIcon().getIconHeight(), referenceIcon.getIconHeight());
                assertEquals(button.getPreferredSize(), actionSize);
                assertEquals(button.getMinimumSize(), actionSize);
                assertEquals(button.getMaximumSize(), actionSize);
            }
        }
    }

    @Test
    public void codeSnippetVariableToggleShouldFitNarrowSidebarInBothLanguages() {
        RequestCodeSnippetPanel panel = new RequestCodeSnippetPanel(() -> { }, () -> { });
        JCheckBox resolveVariables = findComponent(panel, JCheckBox.class);
        JComboBox<?> language = findComponent(panel, JComboBox.class);
        panel.setSize(240, 500);

        for (Locale locale : List.of(Locale.ENGLISH, Locale.CHINESE)) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            resolveVariables.setText(bundle.getString(MessageKeys.REQUEST_ASSISTANT_CODE_RESOLVE_VARIABLES));
            layoutRecursively(panel);

            assertTrue(resolveVariables.getWidth() >= resolveVariables.getPreferredSize().width,
                    "Variable option should fit in " + locale);
            assertTrue(resolveVariables.getY() >= language.getParent().getY() + language.getParent().getHeight(),
                    "Variable option should have its own row");
        }
    }

    @Test
    public void collapsedAssistantToolbarShouldUseCenteredSidebarActionCells() {
        RequestSideAssistantPanel panel = new RequestSideAssistantPanel(() -> null);
        Dimension actionSize = ToolWindowStripeMetrics.actionSize();

        assertEquals(panel.getPreferredSize().width, ToolWindowStripeMetrics.STRIPE_THICKNESS);
        assertEquals(panel.getMinimumSize().width, ToolWindowStripeMetrics.STRIPE_THICKNESS);

        JPanel toolWindowArea = (JPanel) panel.getComponent(0);
        BorderLayout layout = (BorderLayout) toolWindowArea.getLayout();
        Component toolbar = layout.getLayoutComponent(BorderLayout.EAST);

        assertEquals(toolbar.getPreferredSize().width, ToolWindowStripeMetrics.STRIPE_THICKNESS);
        assertEquals(toolbar.getMinimumSize().width, ToolWindowStripeMetrics.STRIPE_THICKNESS);
        Insets toolbarInsets = ((JPanel) toolbar).getBorder().getBorderInsets(toolbar);
        assertEquals(toolbarInsets.top, 4);
        assertEquals(toolbarInsets.bottom, 4);

        List<JLabel> labels = collectIconLabels((JPanel) toolbar);
        assertEquals(labels.size(), 2);
        for (JLabel label : labels) {
            assertEquals(label.getPreferredSize(), actionSize);
            assertEquals(label.getMinimumSize(), actionSize);
            assertEquals(label.getMaximumSize(), actionSize);
            assertEquals(label.getHorizontalAlignment(), SwingConstants.CENTER);
            assertEquals(label.getVerticalAlignment(), SwingConstants.CENTER);
            Insets insets = label.getBorder().getBorderInsets(label);
            assertEquals(insets.left, 0);
            assertEquals(insets.right, 8);
        }
    }

    @Test
    public void selectedAssistantToolBackgroundShouldFollowShiftedIconCenter() {
        RequestSideAssistantPanel panel = new RequestSideAssistantPanel(() -> null);
        JLabel label = collectIconLabels(findToolbar(panel)).get(0);
        Dimension actionSize = ToolWindowStripeMetrics.actionSize();
        label.setSize(actionSize);

        MouseEvent mousePressed = new MouseEvent(
                label,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                actionSize.width / 2,
                actionSize.height / 2,
                1,
                false,
                MouseEvent.BUTTON1
        );
        for (var listener : label.getMouseListeners()) {
            listener.mousePressed(mousePressed);
        }

        Rectangle selectedBackground = paintPrimaryBackgroundBounds(label, actionSize);

        assertTrue(selectedBackground.x <= 1);
        assertTrue(selectedBackground.width <= 27);
        assertTrue(selectedBackground.getCenterX() < actionSize.width / 2.0);
    }

    @Test
    public void assistantRefreshShouldClearStaleGroupScopeWhenRequestIsMissing() {
        HttpRequestItem missingRequest = new HttpRequestItem();
        missingRequest.setId("missing-request");
        RequestExecutionContext.setCurrentScope(RequestExecutionScope.fromGroupVariables(Map.of("testname", "333")));
        CollectionTreeRootRegistry.clear();
        try {
            RequestSideAssistantPanel panel = new RequestSideAssistantPanel(() -> missingRequest);
            JLabel variablesTool = collectIconLabels(findToolbar(panel)).get(0);

            MouseEvent mousePressed = new MouseEvent(
                    variablesTool,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    1,
                    1,
                    1,
                    false,
                    MouseEvent.BUTTON1
            );
            for (var listener : variablesTool.getMouseListeners()) {
                listener.mousePressed(mousePressed);
            }

            assertEquals(VariableResolver.resolveVariable("testname"), null);
        } finally {
            RequestExecutionContext.clearCurrentScope();
            CollectionTreeRootRegistry.clear();
        }
    }

    private static List<JLabel> collectIconLabels(JPanel panel) {
        List<JLabel> labels = new ArrayList<>();
        for (Component component : panel.getComponents()) {
            if (component instanceof JLabel label && label.getIcon() != null) {
                labels.add(label);
            }
        }
        assertTrue(labels.size() <= 2);
        return labels;
    }

    private static JPanel findToolbar(RequestSideAssistantPanel panel) {
        JPanel toolWindowArea = (JPanel) panel.getComponent(0);
        BorderLayout layout = (BorderLayout) toolWindowArea.getLayout();
        return (JPanel) layout.getLayoutComponent(BorderLayout.EAST);
    }

    private static <T extends Component> T findComponent(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container child) {
                T match = findComponent(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static List<JButton> collectToolbarButtons(Container container) {
        List<JButton> buttons = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) {
                if (FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON.equals(
                        button.getClientProperty(FlatClientProperties.BUTTON_TYPE))) {
                    buttons.add(button);
                }
            } else if (component instanceof Container child) {
                buttons.addAll(collectToolbarButtons(child));
            }
        }
        return buttons;
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutRecursively(child);
            }
        }
    }

    private static Rectangle paintPrimaryBackgroundBounds(JLabel label, Dimension size) {
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            label.paint(graphics);
        } finally {
            graphics.dispose();
        }

        Color primary = ModernColors.getPrimary();
        Rectangle bounds = null;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (isPrimary(pixel, primary)) {
                    if (bounds == null) {
                        bounds = new Rectangle(x, y, 1, 1);
                    } else {
                        bounds.add(x, y);
                    }
                }
            }
        }
        assertTrue(bounds != null);
        return bounds;
    }

    private static boolean isPrimary(Color pixel, Color primary) {
        return pixel.getAlpha() > 200
                && Math.abs(pixel.getRed() - primary.getRed()) <= 2
                && Math.abs(pixel.getGreen() - primary.getGreen()) <= 2
                && Math.abs(pixel.getBlue() - primary.getBlue()) <= 2;
    }
}
