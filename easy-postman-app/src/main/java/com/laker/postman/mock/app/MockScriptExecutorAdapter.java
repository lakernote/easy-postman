package com.laker.postman.mock.app;

import com.laker.postman.ioc.Component;
import com.laker.postman.mock.script.MockScriptContext;
import com.laker.postman.mock.script.MockScriptExecutor;
import com.laker.postman.service.js.JsScriptExecutor;

import java.util.Map;

@Component
public class MockScriptExecutorAdapter implements MockScriptExecutor {
    @Override
    public void execute(String script, MockScriptContext context) throws Exception {
        JsScriptExecutor.executeScript(script, Map.of("pm", new MockPmApi(context)), ignored -> {
        });
    }
}
