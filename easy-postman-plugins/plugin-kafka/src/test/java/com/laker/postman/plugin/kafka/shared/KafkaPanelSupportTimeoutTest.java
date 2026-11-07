package com.laker.postman.plugin.kafka.shared;

import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class KafkaPanelSupportTimeoutTest {

    @Test
    public void futureWaitShouldOutlastClientTimeout() {
        assertTrue(KafkaPanelSupport.futureWaitMs(KafkaPanelSupport.CLIENT_TIMEOUT_MS) > KafkaPanelSupport.CLIENT_TIMEOUT_MS);
        assertEquals(KafkaPanelSupport.futureWaitMs(1_000), 1_000 + KafkaPanelSupport.FUTURE_WAIT_GRACE_MS);
    }

    @Test
    public void shouldDetectJdkAndKafkaTimeouts() {
        assertTrue(KafkaPanelSupport.isTimeout(new TimeoutException()));
        assertTrue(KafkaPanelSupport.isTimeout(new ExecutionException(
                new org.apache.kafka.common.errors.TimeoutException("Timed out waiting for a node assignment"))));
        assertFalse(KafkaPanelSupport.isTimeout(new ExecutionException(
                new org.apache.kafka.common.errors.SaslAuthenticationException("bad credentials"))));
        assertFalse(KafkaPanelSupport.isTimeout(null));
    }
}
