package com.laker.postman.mock.script;

@FunctionalInterface
public interface MockScriptExecutor {
    MockScriptExecutor NO_OP = (script, context) -> {
    };

    void execute(String script, MockScriptContext context) throws Exception;
}
