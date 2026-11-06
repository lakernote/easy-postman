package com.laker.postman.platform.update.source;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class AbstractUpdateSourceTest {

    @Test
    public void shouldContinueToNextPageWhenPluginReleasesFillFirstPage() throws IOException {
        List<String> requestedQueries = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/releases", exchange -> {
            requestedQueries.add(exchange.getRequestURI().getQuery());
            int page = queryParameter(exchange, "page");
            JSONArray releases = page == 1 ? pluginReleases(21) : appReleases("v6.0.14");
            writeJson(exchange, releases);
        });
        server.start();

        try {
            TestUpdateSource source = new TestUpdateSource(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/releases?direction=desc");

            JSONObject latest = source.fetchLatestReleaseInfo();

            assertNotNull(latest);
            assertEquals(latest.getStr("tag_name"), "v6.0.14");
            assertEquals(requestedQueries.size(), 2);
            assertTrue(requestedQueries.get(0).contains("direction=desc"));
            assertTrue(requestedQueries.get(0).contains("per_page=21"));
            assertTrue(requestedQueries.get(0).contains("page=1"));
            assertTrue(requestedQueries.get(1).contains("page=2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldFetchReleaseDirectlyByEncodedTag() throws IOException {
        List<String> requestedRawPaths = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/releases/tags", exchange -> {
            requestedRawPaths.add(exchange.getRequestURI().getRawPath());
            writeJson(exchange, release("v6.3.1+build/test"));
        });
        server.start();

        try {
            TestUpdateSource source = new TestUpdateSource(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/releases?direction=desc");

            JSONObject release = source.fetchReleaseByTag("v6.3.1+build/test");

            assertNotNull(release);
            assertEquals(release.getStr("tag_name"), "v6.3.1+build/test");
            assertEquals(release.getStr(UpdateSource.SOURCE_METADATA_KEY), "Test");
            assertEquals(requestedRawPaths, List.of("/releases/tags/v6.3.1%2Bbuild%2Ftest"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldReturnNullWhenReleaseTagDoesNotExist() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/releases/tags", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        try {
            TestUpdateSource source = new TestUpdateSource(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/releases");

            assertNull(source.fetchReleaseByTag("v9.9.9"));
        } finally {
            server.stop(0);
        }
    }

    private static int queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                return Integer.parseInt(parts[1]);
            }
        }
        return 0;
    }

    private static JSONArray pluginReleases(int count) {
        JSONArray releases = new JSONArray();
        for (int i = 0; i < count; i++) {
            releases.add(release("plugin-capture-v6.0." + i));
        }
        return releases;
    }

    private static JSONArray appReleases(String version) {
        JSONArray releases = new JSONArray();
        releases.add(release(version));
        return releases;
    }

    private static JSONObject release(String tagName) {
        JSONObject release = new JSONObject();
        release.set("tag_name", tagName);
        release.set("draft", false);
        release.set("prerelease", false);
        return release;
    }

    private static void writeJson(HttpExchange exchange, Object body) throws IOException {
        byte[] response = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static final class TestUpdateSource extends AbstractUpdateSource {
        private final String releasesUrl;

        private TestUpdateSource(String releasesUrl) {
            this.releasesUrl = releasesUrl;
        }

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public String getApiUrl() {
            return releasesUrl;
        }

        @Override
        public String getAllReleasesApiUrl() {
            return releasesUrl;
        }

        @Override
        public String getWebUrl() {
            return releasesUrl;
        }
    }
}
