package com.laker.postman.panel.mock;

import com.laker.postman.mock.model.MockCallLog;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MockLogTableModelTest {

    @Test
    public void shouldNotResetTableWhenPeriodicRefreshContainsTheSameLogs() {
        MockServerPanel.LogTableModel model = new MockServerPanel.LogTableModel();
        AtomicInteger changes = new AtomicInteger();
        model.addTableModelListener(event -> changes.incrementAndGet());
        MockCallLog log = logAt(Instant.parse("2026-08-18T07:12:32.327Z"));

        assertTrue(model.setRows(List.of(log)));
        assertFalse(model.setRows(List.of(log)));

        assertEquals(changes.get(), 1);
        assertEquals(model.indexOf(log), 0);
    }

    @Test
    public void shouldLocateSelectedLogAfterNewerRowsArePrepended() {
        MockServerPanel.LogTableModel model = new MockServerPanel.LogTableModel();
        MockCallLog selected = logAt(Instant.parse("2026-08-18T07:12:32.327Z"));
        MockCallLog newer = logAt(Instant.parse("2026-08-18T07:12:36.452Z"));

        model.setRows(List.of(selected));
        model.setRows(List.of(newer, selected));

        assertEquals(model.indexOf(selected), 1);
    }

    private static MockCallLog logAt(Instant timestamp) {
        return new MockCallLog(timestamp, "GET", "/api/example", 200, 7,
                "Example", "Success", "", "{}", null);
    }
}
