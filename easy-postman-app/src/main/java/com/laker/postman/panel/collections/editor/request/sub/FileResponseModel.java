package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.file.ResponseFileNames;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.util.FileExtensionUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import java.io.File;
import java.util.Locale;

/**
 * 文件概览的展示数据，以及“响应是否以文件概览展示”的判定规则。
 *
 * @param fileName            下载文件名
 * @param fileType            展示用文件类型（如 XLSX）
 * @param bodySize            响应体字节数
 * @param declaredContentType 服务端声明的 Content-Type，可为空
 * @param detectedFileType    根据内容识别出的类型；非空时提示与声明类型不符
 * @param canViewAsText       是否提供“按文本查看”（仅文本附件，如 CSV）
 * @param contentAvailable    文件内容是否可下载；示例/历史只保存文件信息时为 false
 */
record FileResponseModel(String fileName,
                         String fileType,
                         long bodySize,
                         String declaredContentType,
                         String detectedFileType,
                         boolean canViewAsText,
                         boolean contentAvailable) {

    /**
     * 识别出文件、附件声明或二进制 Content-Type，且有可下载的内容（或内容已明确省略）时以文件概览展示。
     */
    static boolean shouldShow(HttpResponse response) {
        if (response == null) {
            return false;
        }
        if (response.fileContentOmitted) {
            return true;
        }
        if (!hasDownloadablePayload(response)) {
            return false;
        }
        return response.detectedFileExtension != null
                || response.fileAttachment
                || FileExtensionUtil.isBinaryType(ResponseFiles.contentType(response));
    }

    static FileResponseModel from(HttpResponse response) {
        String contentType = ResponseFiles.contentType(response);
        String fileType = resolveFileType(response, contentType);
        boolean detectedBinaryFile = response.detectedFileExtension != null;
        return new FileResponseModel(
                response.fileName,
                fileType,
                response.bodySize,
                contentType,
                detectedBinaryFile ? fileType : null,
                // 识别出的二进制格式按文本查看只有乱码，仅对文本附件（如 CSV）提供
                response.rawBodyBytes != null && !detectedBinaryFile,
                !response.fileContentOmitted
        );
    }

    private static boolean hasDownloadablePayload(HttpResponse response) {
        if (response.rawBodyBytes != null) {
            return true;
        }
        if (response.filePath == null || response.filePath.isBlank()) {
            return false;
        }
        File responseFile = new File(response.filePath);
        return responseFile.isFile() && responseFile.canRead();
    }

    private static String resolveFileType(HttpResponse response, String contentType) {
        String extension = response.detectedFileExtension;
        if (extension == null || extension.isBlank()) {
            extension = FileExtensionUtil.guessExtension(contentType);
        }
        if (extension == null || extension.isBlank()) {
            extension = ResponseFileNames.extensionOf(response.fileName);
        }
        if (extension == null || extension.isBlank()) {
            return I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_GENERIC_TYPE);
        }
        return (extension.startsWith(".") ? extension.substring(1) : extension).toUpperCase(Locale.ROOT);
    }
}
