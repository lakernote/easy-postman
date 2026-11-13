package com.laker.postman.panel.performance.result;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.result.PerformanceResultMemoryBudget;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PerformanceResultTableModelRetentionTest {

    @Test
    public void shouldEvictOldestRowsWhenRetainedBytesExceedBudget() {
        ResultNodeInfo first = row("row-0", 1024);
        long rowBytes = PerformanceResultMemoryBudget.estimateBytes(first);
        PerformanceResultTablePanel.ResultTableModel model =
                new PerformanceResultTablePanel.ResultTableModel(rowBytes * 3);

        List<PerformanceResultTablePanel.PendingResult> batch = new ArrayList<>();
        batch.add(new PerformanceResultTablePanel.PendingResult(first, 1000, rowBytes));
        for (int i = 1; i < 10; i++) {
            ResultNodeInfo info = row("row-" + i, 1024);
            batch.add(new PerformanceResultTablePanel.PendingResult(info, 1000,
                    PerformanceResultMemoryBudget.estimateBytes(info)));
        }
        model.append(batch);

        assertEquals(model.getRowCount(), 3);
        assertEquals(model.getRow(0).name, "row-7");
        assertEquals(model.getRow(2).name, "row-9");
        assertTrue(model.retainedBytes() <= rowBytes * 3);
    }

    @Test
    public void shouldKeepNewestRowEvenWhenItAloneExceedsBudget() {
        ResultNodeInfo huge = row("huge", 64 * 1024);
        PerformanceResultTablePanel.ResultTableModel model = new PerformanceResultTablePanel.ResultTableModel(1024);

        model.append(List.of(
                new PerformanceResultTablePanel.PendingResult(row("small", 10), 1000, 2048),
                new PerformanceResultTablePanel.PendingResult(huge, 1000, PerformanceResultMemoryBudget.estimateBytes(huge))
        ));

        assertEquals(model.getRowCount(), 1);
        assertEquals(model.getRow(0).name, "huge");
    }

    private static ResultNodeInfo row(String name, int bodyChars) {
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "x".repeat(bodyChars);
        return new ResultNodeInfo(name, "", null, response, List.of(), false);
    }
}
