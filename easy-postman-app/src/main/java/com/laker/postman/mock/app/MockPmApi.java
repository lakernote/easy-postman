package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.script.MockScriptContext;
import com.laker.postman.mock.script.MockState;
import org.graalvm.polyglot.Value;

/**
 * Small Postman-shaped API for local mock scripts.
 */
public final class MockPmApi {
    public final RequestApi request;
    public final MockResponse response;
    public final StateApi state;

    public MockPmApi(MockScriptContext context) {
        this.request = new RequestApi(context.getRequest());
        this.response = context.getResponse();
        this.state = new StateApi(context.getState());
    }

    public static final class RequestApi {
        public final String method;
        public final String path;
        public final String body;
        private final MockRequest delegate;

        private RequestApi(MockRequest delegate) {
            this.delegate = delegate;
            this.method = delegate.method();
            this.path = delegate.path();
            this.body = delegate.body();
        }

        public String header(String name) {
            return delegate.header(name);
        }

        public String query(String name) {
            return delegate.query(name);
        }

        public String pathVariable(String name) {
            return delegate.pathVariable(name);
        }
    }

    public static final class StateApi {
        private final MockState delegate;

        private StateApi(MockState delegate) {
            this.delegate = delegate;
        }

        public Object get(String key) {
            return delegate.get(key);
        }

        public void set(String key, Object value) {
            delegate.set(key, detach(value));
        }

        public boolean has(String key) {
            return delegate.has(key);
        }

        public void unset(String key) {
            delegate.unset(key);
        }

        public void clear() {
            delegate.clear();
        }

        public Object toObject() {
            return delegate.toMap();
        }

        private static Object detach(Object value) {
            if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue();
            }
            if (!(value instanceof Value polyglotValue)) {
                return value;
            }
            if (polyglotValue.isNull()) return null;
            if (polyglotValue.isBoolean()) return polyglotValue.asBoolean();
            if (polyglotValue.fitsInLong()) return polyglotValue.asLong();
            if (polyglotValue.fitsInDouble()) return polyglotValue.asDouble();
            if (polyglotValue.isString()) return polyglotValue.asString();
            return polyglotValue.toString();
        }
    }
}
