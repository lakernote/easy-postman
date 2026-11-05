package com.laker.postman.util;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * FileExtensionUtil 单元测试类
 * 测试文件扩展名推断、文件名生成和类型判断功能
 */
public class FileExtensionUtilTest {

    // ==================== guessExtension 测试 ====================

    @DataProvider(name = "documentContentTypes")
    public Object[][] documentContentTypes() {
        return new Object[][]{
                // Office 文档（现代格式）
                {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"},
                {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"},
                {"application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"},
                // Office 文档（旧格式）
                {"application/msword", ".doc"},
                {"application/vnd.ms-excel", ".xls"},
                {"application/vnd.ms-powerpoint", ".ppt"},
                // PDF
                {"application/pdf", ".pdf"},
        };
    }

    @Test(dataProvider = "documentContentTypes", description = "测试文档类型的扩展名推断")
    public void testGuessExtension_Documents(String contentType, String expectedExt) {
        assertEquals(FileExtensionUtil.guessExtension(contentType), expectedExt,
                "Content-Type: " + contentType + " 应该返回扩展名: " + expectedExt);
    }

    @DataProvider(name = "textContentTypes")
    public Object[][] textContentTypes() {
        return new Object[][]{
                {"application/json", ".json"},
                {"text/json", ".json"},
                {"application/xml", ".xml"},
                {"text/xml", ".xml"},
                {"text/html", ".html"},
                {"application/javascript", ".js"},
                {"text/javascript", ".js"},
                {"text/css", ".css"},
                {"text/csv", ".csv"},
                {"text/plain", ".txt"},
                {"application/yaml", ".yaml"},
                {"text/yaml", ".yaml"},
        };
    }

    @Test(dataProvider = "textContentTypes", description = "测试文本类型的扩展名推断")
    public void testGuessExtension_TextTypes(String contentType, String expectedExt) {
        assertEquals(FileExtensionUtil.guessExtension(contentType), expectedExt);
    }

    @DataProvider(name = "imageContentTypes")
    public Object[][] imageContentTypes() {
        return new Object[][]{
                {"image/png", ".png"},
                {"image/jpeg", ".jpg"},
                {"image/gif", ".gif"},
                {"image/bmp", ".bmp"},
                {"image/webp", ".webp"},
                {"image/svg+xml", ".svg"},
                {"image/x-icon", ".ico"},
        };
    }

    @Test(dataProvider = "imageContentTypes", description = "测试图片类型的扩展名推断")
    public void testGuessExtension_Images(String contentType, String expectedExt) {
        assertEquals(FileExtensionUtil.guessExtension(contentType), expectedExt);
    }

    @DataProvider(name = "archiveContentTypes")
    public Object[][] archiveContentTypes() {
        return new Object[][]{
                {"application/zip", ".zip"},
                {"application/x-zip-compressed", ".zip"},
                {"application/gzip", ".gz"},
                {"application/x-tar", ".tar"},
                {"application/x-7z-compressed", ".7z"},
                {"application/x-rar-compressed", ".rar"},
        };
    }

    @Test(dataProvider = "archiveContentTypes", description = "测试压缩文件类型的扩展名推断")
    public void testGuessExtension_Archives(String contentType, String expectedExt) {
        assertEquals(FileExtensionUtil.guessExtension(contentType), expectedExt);
    }

    @DataProvider(name = "mediaContentTypes")
    public Object[][] mediaContentTypes() {
        return new Object[][]{
                // 音频
                {"audio/mpeg", ".mp3"},
                {"audio/mp3", ".mp3"},
                {"audio/wav", ".wav"},
                {"audio/x-wav", ".wav"},
                {"audio/ogg", ".ogg"},
                {"audio/aac", ".aac"},
                {"audio/mp4", ".m4a"},
                {"audio/x-m4a", ".m4a"},
                {"audio/flac", ".flac"},
                {"audio/aiff", ".aiff"},
                {"audio/basic", ".au"},
                {"audio/webm", ".webm"},
                {"audio/opus", ".opus"},
                // 视频
                {"video/mp4", ".mp4"},
                {"video/mpeg", ".mpeg"},
                {"video/webm", ".webm"},
        };
    }

    @Test(dataProvider = "mediaContentTypes", description = "测试多媒体类型的扩展名推断")
    public void testGuessExtension_Media(String contentType, String expectedExt) {
        assertEquals(FileExtensionUtil.guessExtension(contentType), expectedExt);
    }

    @Test(description = "测试空值或无法识别的 Content-Type")
    public void testGuessExtension_NullAndUnknown() {
        assertNull(FileExtensionUtil.guessExtension(null), "null 应该返回 null");
        assertNull(FileExtensionUtil.guessExtension(""), "空字符串应该返回 null");
        assertNull(FileExtensionUtil.guessExtension("unknown/type"), "未知类型应该返回 null");
    }

    @Test(description = "测试 Content-Type 带字符集参数的情况")
    public void testGuessExtension_WithCharset() {
        assertEquals(FileExtensionUtil.guessExtension("application/json; charset=UTF-8"), ".json");
        assertEquals(FileExtensionUtil.guessExtension("text/html; charset=UTF-8"), ".html");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; charset=binary"), ".xlsx");
    }

    // ==================== generateSmartFileName 测试 ====================

    @Test(description = "测试生成智能文件名")
    public void testGenerateSmartFileName() {
        String fileName = FileExtensionUtil.generateSmartFileName(".json");
        assertTrue(fileName.startsWith("response_data_"), "JSON 文件名应该以 response_data_ 开头");
        assertTrue(fileName.endsWith(".json"), "文件名应该以 .json 结尾");

        fileName = FileExtensionUtil.generateSmartFileName("xlsx");
        assertTrue(fileName.startsWith("document_"), "Excel 文件名应该以 document_ 开头");
        assertTrue(fileName.endsWith(".xlsx"), "文件名应该以 .xlsx 结尾");

        fileName = FileExtensionUtil.generateSmartFileName(".png");
        assertTrue(fileName.startsWith("image_"), "图片文件名应该以 image_ 开头");
        assertTrue(fileName.endsWith(".png"), "文件名应该以 .png 结尾");
    }

    @Test(description = "测试生成文件名时扩展名为空的情况")
    public void testGenerateSmartFileName_NullExtension() {
        String fileName = FileExtensionUtil.generateSmartFileName(null);
        assertTrue(fileName.endsWith(".txt"), "null 扩展名应该默认为 .txt");

        fileName = FileExtensionUtil.generateSmartFileName("");
        assertTrue(fileName.endsWith(".txt"), "空扩展名应该默认为 .txt");
    }

    @Test(description = "测试生成的文件名唯一性")
    public void testGenerateSmartFileName_Uniqueness() throws InterruptedException {
        String fileName1 = FileExtensionUtil.generateSmartFileName(".json");
        Thread.sleep(1); // 确保时间戳不同
        String fileName2 = FileExtensionUtil.generateSmartFileName(".json");
        assertNotEquals(fileName1, fileName2, "两次生成的文件名应该不同（包含不同的时间戳）");
    }

    // ==================== extractMainType 测试 ====================

    @Test(description = "测试提取主类型")
    public void testExtractMainType() {
        assertEquals(FileExtensionUtil.extractMainType("text/plain"), "text");
        assertEquals(FileExtensionUtil.extractMainType("application/json"), "application");
        assertEquals(FileExtensionUtil.extractMainType("image/png"), "image");
        assertEquals(FileExtensionUtil.extractMainType("video/mp4"), "video");
    }

    @Test(description = "测试提取主类型时的异常情况")
    public void testExtractMainType_EdgeCases() {
        assertNull(FileExtensionUtil.extractMainType(null), "null 应该返回 null");
        assertNull(FileExtensionUtil.extractMainType(""), "空字符串应该返回 null");
        assertNull(FileExtensionUtil.extractMainType("invalid"), "无斜杠的字符串应该返回 null");
    }

    // ==================== isTextType 测试 ====================

    @DataProvider(name = "textTypes")
    public Object[][] textTypes() {
        return new Object[][]{
                {"text/plain"},
                {"text/html"},
                {"text/css"},
                {"application/json"},
                {"application/xml"},
                {"application/javascript"},
                {"application/x-www-form-urlencoded"},
                {"application/x-sh"},
                {"application/x-python"},
        };
    }

    @Test(dataProvider = "textTypes", description = "测试文本类型判断")
    public void testIsTextType_TextTypes(String contentType) {
        assertTrue(FileExtensionUtil.isTextType(contentType),
                contentType + " 应该被识别为文本类型");
    }

    @DataProvider(name = "nonTextTypes")
    public Object[][] nonTextTypes() {
        return new Object[][]{
                {"image/png"},
                {"video/mp4"},
                {"audio/mpeg"},
                {"application/pdf"},
                {"application/zip"},
                {"application/octet-stream"},
        };
    }

    @Test(dataProvider = "nonTextTypes", description = "测试非文本类型判断")
    public void testIsTextType_NonTextTypes(String contentType) {
        assertFalse(FileExtensionUtil.isTextType(contentType),
                contentType + " 不应该被识别为文本类型");
    }

    @Test(description = "测试文本类型判断的边界情况")
    public void testIsTextType_EdgeCases() {
        assertFalse(FileExtensionUtil.isTextType(null));
        assertFalse(FileExtensionUtil.isTextType(""));
    }

    // ==================== isBinaryType 测试 ====================

    @DataProvider(name = "binaryTypes")
    public Object[][] binaryTypes() {
        return new Object[][]{
                {"application/octet-stream"},
                {"image/png"},
                {"image/jpeg"},
                {"audio/mpeg"},
                {"video/mp4"},
                {"application/pdf"},
                {"application/zip"},
                {"application/gzip"},
                {"application/msword"},
                // 关键测试：Office 文档（包含 xml 但应该是二进制）
                {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                {"application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"application/vnd.openxmlformats-officedocument.presentationml.presentation"},
                {"application/vnd.ms-excel"},
        };
    }

    @Test(dataProvider = "binaryTypes", description = "测试二进制类型判断")
    public void testIsBinaryType_BinaryTypes(String contentType) {
        assertTrue(FileExtensionUtil.isBinaryType(contentType),
                contentType + " 应该被识别为二进制类型");
    }

    @DataProvider(name = "nonBinaryTypes")
    public Object[][] nonBinaryTypes() {
        return new Object[][]{
                {"text/plain"},
                {"text/html"},
                {"application/json"},
                {"application/xml"},
                {"application/javascript"},
        };
    }

    @Test(dataProvider = "nonBinaryTypes", description = "测试非二进制类型判断")
    public void testIsBinaryType_NonBinaryTypes(String contentType) {
        assertFalse(FileExtensionUtil.isBinaryType(contentType),
                contentType + " 不应该被识别为二进制类型");
    }

    @Test(description = "测试二进制类型判断的边界情况")
    public void testIsBinaryType_EdgeCases() {
        assertFalse(FileExtensionUtil.isBinaryType(null));
        assertFalse(FileExtensionUtil.isBinaryType(""));
    }

    // ==================== 关键问题回归测试 ====================

    @Test(description = "回归测试：xlsx 文件不应该被误判为 xml 文本")
    public void testXlsxNotMisidentifiedAsXml() {
        String xlsxContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        // 1. 应该返回正确的扩展名
        assertEquals(FileExtensionUtil.guessExtension(xlsxContentType), ".xlsx",
                "xlsx 应该返回 .xlsx 而不是 .xml");

        // 2. 应该被识别为二进制类型
        assertTrue(FileExtensionUtil.isBinaryType(xlsxContentType),
                "xlsx 应该被识别为二进制类型");

        // 3. 不应该被识别为文本类型
        assertFalse(FileExtensionUtil.isTextType(xlsxContentType),
                "xlsx 不应该被识别为文本类型");
    }

    @Test(description = "回归测试：docx 文件不应该被误判为 xml 文本")
    public void testDocxNotMisidentifiedAsXml() {
        String docxContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        assertEquals(FileExtensionUtil.guessExtension(docxContentType), ".docx");
        assertTrue(FileExtensionUtil.isBinaryType(docxContentType));
        assertFalse(FileExtensionUtil.isTextType(docxContentType));
    }

    @Test(description = "回归测试：pptx 文件不应该被误判为 xml 文本")
    public void testPptxNotMisidentifiedAsXml() {
        String pptxContentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

        assertEquals(FileExtensionUtil.guessExtension(pptxContentType), ".pptx");
        assertTrue(FileExtensionUtil.isBinaryType(pptxContentType));
        assertFalse(FileExtensionUtil.isTextType(pptxContentType));
    }

    @Test(description = "回归测试：真正的 XML 应该被正确识别为文本")
    public void testRealXmlIdentifiedAsText() {
        String xmlContentType = "application/xml";

        assertEquals(FileExtensionUtil.guessExtension(xmlContentType), ".xml");
        assertTrue(FileExtensionUtil.isTextType(xmlContentType));
        assertFalse(FileExtensionUtil.isBinaryType(xmlContentType));
    }

    @Test(description = "回归测试：Vendor JSON 响应应该被识别为文本")
    public void testVendorJsonIdentifiedAsText() {
        String contentType = "application/vnd.spring-boot.actuator.v3+json";

        assertEquals(FileExtensionUtil.guessExtension(contentType), ".json");
        assertTrue(FileExtensionUtil.isTextType(contentType));
        assertFalse(FileExtensionUtil.isBinaryType(contentType));
    }

    @Test(description = "回归测试：验证类型判断的互斥性")
    public void testTypeExclusivity() {
        String[] allContentTypes = {
                "text/plain",
                "application/json",
                "application/xml",
                "image/png",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream"
        };

        for (String contentType : allContentTypes) {
            boolean isText = FileExtensionUtil.isTextType(contentType);
            boolean isBinary = FileExtensionUtil.isBinaryType(contentType);

            // 一个 Content-Type 应该要么是文本，要么是二进制，要么都不是（unknown），但不应该同时是两者
            assertFalse(isText && isBinary,
                    contentType + " 不应该同时被判断为文本和二进制类型");
        }
    }

    @Test(description = "只按媒体类型判断，忽略参数")
    public void testContentTypeParametersAreIgnored() {
        String contentType = "application/octet-stream; name=\"data.json\"";

        assertFalse(FileExtensionUtil.isTextType(contentType));
        assertTrue(FileExtensionUtil.isBinaryType(contentType));
        assertNull(FileExtensionUtil.guessExtension(contentType));
        assertTrue(FileExtensionUtil.isTextType("Application/JSON; charset=UTF-8"));
        assertTrue(FileExtensionUtil.isTextType("application/xml; charset=UTF-8"));
        assertFalse(FileExtensionUtil.isBinaryType(" ; charset=UTF-8"));
    }

    @Test
    public void shouldMapOfficeVariantsOdfAndJavaArchive() {
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.ms-excel.sheet.macroEnabled.12"), ".xlsm");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.ms-excel.sheet.binary.macroEnabled.12"), ".xlsb");
        assertEquals(FileExtensionUtil.guessExtension(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template"), ".dotx");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.ms-powerpoint.slideshow.macroEnabled.12"), ".ppsm");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.oasis.opendocument.text-template"), ".ott");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.oasis.opendocument.spreadsheet"), ".ods");
        assertEquals(FileExtensionUtil.guessExtension("application/java-archive"), ".jar");
        assertEquals(FileExtensionUtil.guessExtension("application/x-java-archive"), ".jar");
        assertEquals(FileExtensionUtil.guessExtension("application/vnd.ms-excel"), ".xls");
        assertFalse(FileExtensionUtil.isTextType("application/x-java-archive"));
        assertTrue(FileExtensionUtil.isBinaryType("application/x-java-archive"));
    }
}
