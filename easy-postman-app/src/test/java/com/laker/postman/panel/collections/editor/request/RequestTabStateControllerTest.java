package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertTrue;

public class RequestTabStateControllerTest extends AbstractSwingUiTest {

    @Test
    public void shouldBindHttpBodyEditsToDirtyState() throws Exception {
        assertBodyEditTriggersDirtyUpdate(RequestItemProtocolEnum.HTTP);
    }

    @Test
    public void shouldBindWebSocketBodyEditsToDirtyState() throws Exception {
        assertBodyEditTriggersDirtyUpdate(RequestItemProtocolEnum.WEBSOCKET);
    }

    private void assertBodyEditTriggersDirtyUpdate(RequestItemProtocolEnum protocol) throws Exception {
        AtomicInteger dirtyUpdates = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            RequestViewComponents view = RequestViewFactory.create(protocol, RequestEditSubPanelType.NORMAL, e -> {
            });
            RequestTabStateController controller = new RequestTabStateController(protocol, view);
            controller.bindListeners(dirtyUpdates::incrementAndGet, () -> {
            });

            view.requestBodyPanel.getBodyTypeComboBox().setSelectedItem(RequestBodyTypes.BODY_TYPE_RAW);
            view.requestBodyPanel.setRawBodyText("{\"stream\":true}");
        });

        assertTrue(dirtyUpdates.get() > 0);
    }
}
