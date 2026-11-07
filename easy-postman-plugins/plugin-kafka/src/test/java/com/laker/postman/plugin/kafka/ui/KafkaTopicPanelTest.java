package com.laker.postman.plugin.kafka.ui;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.laker.postman.plugin.kafka.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;

public class KafkaTopicPanelTest {

    @Test
    public void emptyTextShouldReflectConnectionAndFilterState() {
        KafkaTopicPanel panel = new KafkaTopicPanel(() -> {
        }, topic -> {
        });
        assertEquals(panel.emptyText(), t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NOT_CONNECTED));

        panel.setLoading(true);
        assertEquals(panel.emptyText(), t(MessageKeys.TOOLBOX_KAFKA_STATUS_LOADING_TOPICS));
        assertFalse(panel.isRefreshEnabled(), "refresh should be disabled while topics are loading");

        panel.setLoading(false);
        assertTrue(panel.isRefreshEnabled(), "refresh should be re-enabled after loading");
        panel.setConnected(true);
        assertEquals(panel.emptyText(), t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NONE));

        panel.topicListModel.addElement(new KafkaTopicItem("orders", 3));
        assertEquals(panel.emptyText(), t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NO_MATCH));
    }

    @Test
    public void unfocusedSelectedTopicShouldUseInactiveSelectionColors() {
        Color originalBg = UIManager.getColor("List.selectionInactiveBackground");
        Color originalFg = UIManager.getColor("List.selectionInactiveForeground");
        try {
            Color inactiveBg = new Color(0xDDE6F5);
            Color inactiveFg = new Color(0x1F2937);
            UIManager.put("List.selectionInactiveBackground", inactiveBg);
            UIManager.put("List.selectionInactiveForeground", inactiveFg);
            KafkaTopicPanel panel = new KafkaTopicPanel(() -> {
            }, topic -> {
            });
            panel.topicFilteredModel.addElement(new KafkaTopicItem("orders", 3));

            JComponent cell = (JComponent) panel.topicList.getCellRenderer().getListCellRendererComponent(
                    panel.topicList, panel.topicFilteredModel.get(0), 0, true, false);

            assertEquals(cell.getBackground(), inactiveBg, "unfocused selection should use inactive background");
            for (Component child : cell.getComponents()) {
                assertEquals(child.getForeground(), inactiveFg,
                        "unfocused selection labels should use inactive foreground to stay readable");
            }
        } finally {
            UIManager.put("List.selectionInactiveBackground", originalBg);
            UIManager.put("List.selectionInactiveForeground", originalFg);
        }
    }
}
