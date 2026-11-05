package com.laker.postman.panel.mock;

import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class MockRouteEditorControllerFileInfoTest {

    private static final List<HttpHeader> ATTACHMENT = List.of(
            new HttpHeader(true, "Content-Disposition", "attachment; filename=orders.csv"));

    @Test
    public void shouldKeepFileInfoWhenBodyIsUnchanged() {
        SavedResponse example = fileExample(false, "");

        assertEquals(MockRouteEditorController.fileInfoAfterEdit(example, "", List.of()).getFileName(), "orders.xlsx",
                "只改延迟/脚本时仍需返回 501，而不是 0 字节文件");
        assertEquals(MockRouteEditorController.fileInfoAfterEdit(example, null, null).getFileName(), "orders.xlsx");
    }

    @Test
    public void shouldDropOmittedFileInfoWhenUserWritesBody() {
        SavedResponse example = fileExample(false, "");

        assertNull(MockRouteEditorController.fileInfoAfterEdit(example, "{\"ok\":true}", List.of()));
    }

    @Test
    public void shouldKeepTextAttachmentFileInfoOnlyWhileAttachmentHeaderRemains() {
        SavedResponse example = fileExample(true, "a,b");

        assertEquals(MockRouteEditorController.fileInfoAfterEdit(example, "a,b,c", ATTACHMENT).getFileName(), "orders.csv");
        assertNull(MockRouteEditorController.fileInfoAfterEdit(example, "a,b", List.of()),
                "删除 attachment 后按普通文本示例处理");
        assertNull(MockRouteEditorController.fileInfoAfterEdit(example, "a,b", List.of(
                new HttpHeader(false, "Content-Disposition", "attachment"))), "禁用的响应头不生效");
    }

    @Test
    public void shouldDropOmittedFileInfoWhenHeadersNoLongerDescribeAFile() {
        SavedResponse json = fileExample(false, "");
        json.setHeaders(List.of(new HttpHeader(true, "Content-Type", "application/json")));
        json.setBodySize(4096);
        List<HttpHeader> plainText = List.of(new HttpHeader(true, "Content-Type", "text/plain"));

        assertNull(MockRouteEditorController.fileInfoAfterEdit(json, "", plainText), "改成普通文本后不再返回 501");
        assertEquals(MockRouteEditorController.bodySizeAfterEdit(json, "", plainText, null), 0L);
        assertEquals(MockRouteEditorController.fileInfoAfterEdit(json, "", json.getHeaders()).getFileName(),
                "orders.xlsx", "响应头未改动时保留（服务端声明 JSON、实际返回 xlsx）");
        assertEquals(MockRouteEditorController.fileInfoAfterEdit(json, "", List.of(
                new HttpHeader(true, "Content-Type", "application/octet-stream"))).getFileName(), "orders.xlsx");
    }

    @Test
    public void shouldUpdateFileNameFromEditedHeaders() {
        SavedResponse csv = fileExample(true, "a,b");
        csv.getFile().setFileName("orders.csv");

        assertEquals(MockRouteEditorController.fileInfoAfterEdit(csv, "a,b", List.of(
                new HttpHeader(true, "Content-Disposition", "attachment; filename=customers.csv"))).getFileName(),
                "customers.csv");
        assertEquals(MockRouteEditorController.fileInfoAfterEdit(csv, "a,b", List.of(
                new HttpHeader(true, "Content-Disposition", "attachment"),
                new HttpHeader(true, "Content-Type", "text/plain"))).getFileName(),
                "orders.txt", "未声明文件名时按 Content-Type 更新扩展名");

        SavedResponse omitted = fileExample(false, "");
        assertEquals(MockRouteEditorController.fileInfoAfterEdit(omitted, "", List.of(
                new HttpHeader(true, "Content-Disposition", "attachment; filename=\"q3.xlsx\""))).getFileName(), "q3.xlsx");
    }

    @Test
    public void shouldUpdateBodySizeWhenBodyOrCharsetChanges() {
        SavedResponse omitted = fileExample(false, "");
        omitted.setBodySize(4096);
        assertEquals(MockRouteEditorController.bodySizeAfterEdit(omitted, "", List.of(
                new HttpHeader(true, "Content-Type", "application/pdf")), omitted.getFile()), 4096L, "未保存内容的文件沿用原大小");
        assertEquals(MockRouteEditorController.bodySizeAfterEdit(omitted, "名称", List.of(), null), 6L);

        SavedResponse csv = fileExample(true, "名称");
        csv.setHeaders(List.of(new HttpHeader(true, "Content-Type", "text/csv; charset=UTF-8")));
        csv.setBodySize(6);
        assertEquals(MockRouteEditorController.bodySizeAfterEdit(csv, "名称", csv.getHeaders(), csv.getFile()), 6L);
        assertEquals(MockRouteEditorController.bodySizeAfterEdit(csv, "名称", List.of(
                new HttpHeader(true, "Content-Type", "text/csv; charset=GBK")), csv.getFile()), 4L, "改 charset 后按新字符集计算");
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
}
