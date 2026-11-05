package com.laker.postman.panel.mock;

import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

public class MockRouteEditorControllerFileInfoTest {

    @Test
    public void shouldKeepFileInfoWhenBodyIsUnchanged() {
        SavedResponse example = fileExample(false, "");

        assertSame(MockRouteEditorController.fileInfoAfterEdit(example, ""), example.getFile(),
                "只改延迟/脚本时仍需返回 501，而不是 0 字节文件");
        assertSame(MockRouteEditorController.fileInfoAfterEdit(example, null), example.getFile());
    }

    @Test
    public void shouldDropOmittedFileInfoWhenUserWritesBody() {
        SavedResponse example = fileExample(false, "");

        assertNull(MockRouteEditorController.fileInfoAfterEdit(example, "{\"ok\":true}"));
    }

    @Test
    public void shouldKeepTextAttachmentFileInfoWhenBodyIsEdited() {
        SavedResponse example = fileExample(true, "a,b");

        assertSame(MockRouteEditorController.fileInfoAfterEdit(example, "a,b,c"), example.getFile());
    }

    private static SavedResponse fileExample(boolean contentInBody, String body) {
        SavedResponse.FileInfo file = new SavedResponse.FileInfo();
        file.setFileName("orders.xlsx");
        file.setContentInBody(contentInBody);
        SavedResponse example = new SavedResponse();
        example.setBody(body);
        example.setFile(file);
        return example;
    }

    @Test
    public void shouldUpdateBodySizeOnlyWhenBodyIsEdited() {
        SavedResponse example = fileExample(false, "");
        example.setBodySize(4096);

        org.testng.Assert.assertEquals(MockRouteEditorController.bodySizeAfterEdit(example, ""), 4096L);
        org.testng.Assert.assertEquals(MockRouteEditorController.bodySizeAfterEdit(example, "名称"), 6L);
    }
}
