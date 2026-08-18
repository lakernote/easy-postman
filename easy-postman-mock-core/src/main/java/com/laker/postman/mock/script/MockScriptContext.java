package com.laker.postman.mock.script;

import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MockScriptContext {
    private final MockRequest request;
    private final MockResponse response;
    private final MockState state;
}
