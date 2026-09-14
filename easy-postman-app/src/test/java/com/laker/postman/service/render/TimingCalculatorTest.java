package com.laker.postman.service.render;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TimingCalculatorTest {

    @Test
    public void shouldUseFailedOrCanceledTimeAsTotalEnd() {
        HttpEventInfo failed = new HttpEventInfo();
        failed.setCallStart(1_000L);
        failed.setCallFailed(1_125L);
        HttpEventInfo canceled = new HttpEventInfo();
        canceled.setCallStart(2_000L);
        canceled.setCanceled(2_075L);

        assertEquals(new TimingCalculator(failed).getTotal(), 125L);
        assertEquals(new TimingCalculator(canceled).getTotal(), 75L);
    }

    @Test
    public void shouldPreferDispatcherQueueCallbacks() {
        HttpEventInfo info = new HttpEventInfo();
        info.setQueueStart(1_000L);
        info.setCallStart(1_010L);
        info.setDispatcherQueueStart(1_020L);
        info.setDispatcherQueueEnd(1_070L);

        assertEquals(new TimingCalculator(info).getQueueing(), 50L);
    }
}
