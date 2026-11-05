package com.laker.postman.http.runtime.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 构造最小可识别的 Office Open XML 文件，用于响应文件识别相关测试。
 */
public final class ResponseFileFixtures {

    private ResponseFileFixtures() {
    }

    public static byte[] xlsx() throws IOException {
        return ooxml("xl/workbook.xml");
    }

    public static byte[] ooxml(String mainPartName) throws IOException {
        return ooxml(mainPartName, null);
    }

    /**
     * @param mainContentType 主部件的内容类型（如宏/模板变体），为空时不声明
     */
    public static byte[] ooxml(String mainPartName, String mainContentType) throws IOException {
        String types = mainContentType == null ? "<Types/>"
                : "<Types><Override PartName=\"/" + mainPartName + "\" ContentType=\"" + mainContentType + "\"/></Types>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(types.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(mainPartName));
            // 包含无法按 UTF-8 解码的字节，模拟真实二进制内容
            zip.write(new byte[]{(byte) 0xC3, (byte) 0x28, 0x00, (byte) 0xFF});
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
