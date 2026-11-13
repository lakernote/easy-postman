package com.laker.postman.performance.model;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.script.model.TestResult;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ResultNodeInfoTest {

    @Test
    public void shouldApplyUnifiedSuccessRules() {
        HttpResponse switchingProtocols = new HttpResponse();
        switchingProtocols.code = 101;
        assertTrue(ResultNodeInfo.isActuallySuccessful(false, switchingProtocols, null));

        HttpResponse serverError = new HttpResponse();
        serverError.code = 500;
        assertFalse(ResultNodeInfo.isActuallySuccessful(false, serverError, null));

        TestResult failedAssertion = new TestResult("failed", false, "boom");
        assertFalse(ResultNodeInfo.isActuallySuccessful(false, switchingProtocols, List.of(failedAssertion)));

        TestResult passedAssertion = new TestResult("passed", true, "");
        assertTrue(ResultNodeInfo.isActuallySuccessful(false, serverError, List.of(passedAssertion)));

        assertFalse(ResultNodeInfo.isActuallySuccessful(true, switchingProtocols, null));
    }

    @Test
    public void incompleteResponseBodyShouldNotCountAsSuccess() {
        HttpResponse truncated = new HttpResponse();
        truncated.code = 200;
        truncated.bodyIncomplete = true;

        assertFalse(ResultNodeInfo.isActuallySuccessful(false, truncated, null));
        assertFalse(ResultNodeInfo.isActuallySuccessful(false, truncated, List.of(new TestResult("status", true, ""))));
    }
}
