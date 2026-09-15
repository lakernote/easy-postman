package com.laker.postman.mcp.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Adds an observable termination signal to the SDK stdio provider.
 *
 * <p>The SDK closes its session when EOF, malformed JSON, an oversized message or an I/O
 * error terminates the inbound reader. Wrapping the session transport lets the CLI wait for
 * all of those outcomes instead of waiting only for EOF on the underlying input stream.</p>
 */
final class LifecycleAwareStdioServerTransportProvider implements McpServerTransportProvider {
    private final StdioServerTransportProvider delegate;
    private final CountDownLatch terminated = new CountDownLatch(1);

    LifecycleAwareStdioServerTransportProvider(McpJsonMapper jsonMapper,
                                               InputStream input,
                                               OutputStream output) {
        this.delegate = new StdioServerTransportProvider(jsonMapper, input, output);
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        delegate.setSessionFactory(transport -> sessionFactory.create(
                new TerminationAwareTransport(transport, terminated)
        ));
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        return delegate.notifyClients(method, params);
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return delegate.notifyClient(sessionId, method, params);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return delegate.closeGracefully().doFinally(ignored -> terminated.countDown());
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            terminated.countDown();
        }
    }

    @Override
    public List<String> protocolVersions() {
        return delegate.protocolVersions();
    }

    void awaitTermination() throws InterruptedException {
        terminated.await();
    }

    private record TerminationAwareTransport(McpServerTransport delegate,
                                             CountDownLatch terminated) implements McpServerTransport {
        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return delegate.sendMessage(message);
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return delegate.unmarshalFrom(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return delegate.closeGracefully().doFinally(ignored -> terminated.countDown());
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } finally {
                terminated.countDown();
            }
        }

        @Override
        public List<String> protocolVersions() {
            return delegate.protocolVersions();
        }
    }
}
