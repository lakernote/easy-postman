package com.laker.postman.panel.mock;

import com.laker.postman.mock.app.MockScriptExecutorAdapter;
import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.script.MockScriptContext;
import com.laker.postman.mock.script.MockState;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class MockCodeExampleCatalogTest {

    @Test
    public void shouldOfferCuratedExamplesAcrossAllCategoriesWithLocalizedText() {
        List<MockCodeExampleCatalog.Example> examples = MockCodeExampleCatalog.examples();
        assertEquals(examples.stream().map(MockCodeExampleCatalog.Example::id).toList(), List.of(
                "body-condition",
                "body-validation",
                "query-pagination",
                "bearer-auth",
                "echo-request",
                "polling-sequence",
                "fixed-delay",
                "random-error"
        ));
        assertEquals(new HashSet<>(examples.stream().map(MockCodeExampleCatalog.Example::id).toList()).size(),
                examples.size());
        assertEquals(new HashSet<>(examples.stream().map(MockCodeExampleCatalog.Example::category).toList()).size(),
                MockCodeExampleCatalog.Category.values().length);

        ResourceBundle zh = ResourceBundle.getBundle("messages", Locale.SIMPLIFIED_CHINESE);
        ResourceBundle en = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        for (MockCodeExampleCatalog.Category category : MockCodeExampleCatalog.Category.values()) {
            assertFalse(zh.getString(category.messageKey()).isBlank());
            assertFalse(en.getString(category.messageKey()).isBlank());
        }
        for (MockCodeExampleCatalog.Example example : examples) {
            assertFalse(zh.getString(example.titleKey()).isBlank(), example.id());
            assertFalse(zh.getString(example.descriptionKey()).isBlank(), example.id());
            assertFalse(en.getString(example.titleKey()).isBlank(), example.id());
            assertFalse(en.getString(example.descriptionKey()).isBlank(), example.id());
            assertTrue(example.code().contains("pm."), example.id());
        }
    }

    @Test
    public void everyBuiltInExampleShouldExecuteWithTheSupportedMockApi() {
        MockScriptExecutorAdapter executor = new MockScriptExecutorAdapter();
        MockRequest request = new MockRequest(
                "POST",
                "/users/42",
                Map.of("page", List.of("2"), "size", List.of("3"), "count", List.of("4")),
                Map.of(
                        "Authorization", List.of("Bearer demo-token"),
                        "X-Tenant", List.of("acme"),
                        "X-Trace-Id", List.of("trace-1"),
                        "Accept", List.of("application/json")
                ),
                """
                        {"amount":1200,"name":"Ada","email":"ada@example.com","quantity":2,
                         "unitPrice":60,"username":"demo","password":"demo","price":12.5}
                        """,
                Map.of("id", "42")
        );

        for (MockCodeExampleCatalog.Example example : MockCodeExampleCatalog.examples()) {
            try {
                MockResponse response = new MockResponse(200,
                        Map.of("Content-Type", "application/json"), "{}");
                executor.execute(example.code(), new MockScriptContext(
                        request, response, new MockState(new ConcurrentHashMap<>())));
            } catch (Exception exception) {
                fail("Built-in Code Mock example failed: " + example.id(), exception);
            }
        }
    }
}
