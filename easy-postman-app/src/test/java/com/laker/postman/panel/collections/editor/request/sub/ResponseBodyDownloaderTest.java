package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.model.HttpResponse;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ResponseBodyDownloaderTest {

    @Test
    public void shouldPreferTempFileThenRawBytesThenBodyThenEditorText() throws Exception {
        Path dir = Files.createTempDirectory("response-download");
        try {
            Path temp = Files.write(dir.resolve("temp.bin"), new byte[]{9, 8, 7});
            HttpResponse response = new HttpResponse();
            response.filePath = temp.toString();
            response.rawBodyBytes = new byte[]{1};
            response.body = "body";
            assertEquals(write(dir, ResponseBodyDownloader.Source.of(response, "editor")), new byte[]{9, 8, 7});

            response.filePath = null;
            assertEquals(write(dir, ResponseBodyDownloader.Source.of(response, "editor")), new byte[]{1});

            response.rawBodyBytes = null;
            assertEquals(write(dir, ResponseBodyDownloader.Source.of(response, "editor")),
                    "body".getBytes(StandardCharsets.UTF_8));

            assertEquals(write(dir, ResponseBodyDownloader.Source.of(null, "editor")),
                    "editor".getBytes(StandardCharsets.UTF_8));
            assertEquals(write(dir, ResponseBodyDownloader.Source.of(null, null)), new byte[0]);
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void shouldUseResolvedFileNameOrGenerateFromContentType() {
        HttpResponse named = new HttpResponse();
        named.fileName = "export.xlsx";
        assertEquals(ResponseBodyDownloader.defaultFileName(named), "export.xlsx");

        HttpResponse json = new HttpResponse();
        json.headers = new LinkedHashMap<>();
        json.headers.put("Content-Type", List.of("application/json"));
        assertTrue(ResponseBodyDownloader.defaultFileName(json).endsWith(".json"));

        assertTrue(ResponseBodyDownloader.defaultFileName(null).endsWith(".txt"));
    }

    private static byte[] write(Path dir, ResponseBodyDownloader.Source source) throws Exception {
        Path target = Files.createTempFile(dir, "out", ".bin");
        source.writeTo(target.toFile());
        return Files.readAllBytes(target);
    }
}
