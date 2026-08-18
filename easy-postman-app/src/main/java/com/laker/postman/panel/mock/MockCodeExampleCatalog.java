package com.laker.postman.panel.mock;

import com.laker.postman.util.I18nUtil;

import java.util.List;

/**
 * Built-in, dependency-free Code Mock examples shown by the route and global editors.
 */
final class MockCodeExampleCatalog {
    private static final List<Example> EXAMPLES = List.of(
            example("body-condition", Category.REQUEST, "body_condition", """
                    const input = JSON.parse(pm.request.body || '{}');
                    const amount = Number(input.amount || 0);

                    if (amount >= 1000) {
                        pm.response.setStatusCode(202);
                        pm.response.setBody(JSON.stringify({ status: 'manual_review', amount }));
                    } else {
                        pm.response.setStatusCode(200);
                        pm.response.setBody(JSON.stringify({ status: 'approved', amount }));
                    }
                    """),
            example("body-validation", Category.REQUEST, "body_validation", """
                    const input = JSON.parse(pm.request.body || '{}');
                    const errors = [];
                    if (!input.name) errors.push('name is required');
                    if (!input.email || !String(input.email).includes('@')) errors.push('email is invalid');

                    if (errors.length > 0) {
                        pm.response.setStatusCode(422);
                        pm.response.setBody(JSON.stringify({ error: 'validation_failed', details: errors }));
                    } else {
                        pm.response.setStatusCode(201);
                        pm.response.setBody(JSON.stringify({ id: Date.now(), ...input }));
                    }
                    """),
            example("query-pagination", Category.REQUEST, "query_pagination", """
                    const page = Math.max(1, Number(pm.request.query('page') || 1));
                    const size = Math.min(20, Math.max(1, Number(pm.request.query('size') || 5)));
                    const items = Array.from({ length: size }, (_, index) => ({
                        id: (page - 1) * size + index + 1,
                        name: `Item ${(page - 1) * size + index + 1}`
                    }));

                    pm.response.setBody(JSON.stringify({ page, size, total: 100, items }));
                    """),
            example("bearer-auth", Category.REQUEST, "bearer_auth", """
                    const authorization = pm.request.header('Authorization') || '';
                    if (authorization !== 'Bearer demo-token') {
                        pm.response.setStatusCode(401);
                        pm.response.setHeader('WWW-Authenticate', 'Bearer');
                        pm.response.setBody(JSON.stringify({ error: 'invalid_token' }));
                    } else {
                        pm.response.setBody(JSON.stringify({ authenticated: true, user: 'demo' }));
                    }
                    """),
            example("echo-request", Category.DYNAMIC, "echo_request", """
                    let parsedBody = pm.request.body;
                    try { parsedBody = JSON.parse(pm.request.body || 'null'); } catch (ignored) {}

                    pm.response.setBody(JSON.stringify({
                        method: pm.request.method,
                        path: pm.request.path,
                        body: parsedBody,
                        traceId: pm.request.header('X-Trace-Id') || null
                    }));
                    """),
            example("polling-sequence", Category.STATE, "polling_sequence", """
                    const attempts = Number(pm.state.get('pollAttempts') || 0) + 1;
                    pm.state.set('pollAttempts', attempts);
                    const completed = attempts >= 3;

                    pm.response.setStatusCode(completed ? 200 : 202);
                    pm.response.setBody(JSON.stringify({
                        jobId: 'job-1001', attempts,
                        status: completed ? 'completed' : 'processing'
                    }));
                    """),
            example("fixed-delay", Category.FAILURE, "fixed_delay", """
                    pm.response.setDelayMs(1500);
                    pm.response.setBody(JSON.stringify({
                        ok: true,
                        message: 'This response was intentionally delayed by 1500 ms'
                    }));
                    """),
            example("random-error", Category.FAILURE, "random_error", """
                    if (Math.random() < 0.3) {
                        pm.response.setStatusCode(500);
                        pm.response.setBody(JSON.stringify({ error: 'temporary_failure' }));
                    } else {
                        pm.response.setBody(JSON.stringify({ ok: true }));
                    }
                    """)
    );

    private MockCodeExampleCatalog() {
    }

    static List<Example> examples() {
        return EXAMPLES;
    }

    private static Example example(String id, Category category, String keySuffix, String code) {
        return new Example(
                id,
                category,
                "mock.server.code_example." + keySuffix + ".title",
                "mock.server.code_example." + keySuffix + ".description",
                code.stripTrailing()
        );
    }

    enum Category {
        REQUEST("request"),
        DYNAMIC("dynamic"),
        STATE("state"),
        FAILURE("failure");

        private final String key;

        Category(String key) {
            this.key = "mock.server.code_example.category." + key;
        }

        String displayName() {
            return I18nUtil.getMessage(key);
        }

        String messageKey() {
            return key;
        }
    }

    record Example(String id, Category category, String titleKey, String descriptionKey, String code) {
        String title() {
            return I18nUtil.getMessage(titleKey);
        }

        String description() {
            return I18nUtil.getMessage(descriptionKey);
        }
    }
}
