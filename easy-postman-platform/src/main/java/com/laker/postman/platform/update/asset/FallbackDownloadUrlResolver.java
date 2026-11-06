package com.laker.postman.platform.update.asset;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.laker.postman.platform.update.source.UpdateSource;
import com.laker.postman.platform.update.source.UpdateSourceSelector;
import lombok.extern.slf4j.Slf4j;

/**
 * 从备用更新源的真实发布数据中查找同版本、同平台的安装包地址。
 */
@Slf4j
public class FallbackDownloadUrlResolver {

    private final UpdateSourceSelector sourceSelector;
    private final PlatformDownloadUrlResolver downloadUrlResolver;

    public FallbackDownloadUrlResolver(UpdateSourceSelector sourceSelector) {
        this(sourceSelector, new PlatformDownloadUrlResolver());
    }

    FallbackDownloadUrlResolver(UpdateSourceSelector sourceSelector,
                                PlatformDownloadUrlResolver downloadUrlResolver) {
        this.sourceSelector = sourceSelector;
        this.downloadUrlResolver = downloadUrlResolver;
    }

    /**
     * 查询备用源中与期望版本完全对应的当前平台安装包。
     *
     * @return 备用下载地址；备用源未知、尚未同步或没有当前平台附件时返回 {@code null}
     */
    public String resolve(String expectedVersion, String primarySourceName) {
        if (expectedVersion == null || expectedVersion.isBlank()) {
            return null;
        }

        UpdateSource fallbackSource = sourceSelector.getFallbackSource(primarySourceName);
        if (fallbackSource == null) {
            log.info("No fallback update source for primary source: {}", primarySourceName);
            return null;
        }

        try {
            JSONObject releaseInfo = fallbackSource.fetchReleaseByTag(expectedVersion);
            if (releaseInfo == null) {
                log.info("Fallback source {} has not published release {} yet",
                        fallbackSource.getName(), expectedVersion);
                return null;
            }

            String tagName = releaseInfo.getStr("tag_name");
            if (!expectedVersion.equals(tagName)) {
                log.warn("Fallback source {} returned unexpected release tag: expected {}, actual {}",
                        fallbackSource.getName(), expectedVersion, tagName);
                return null;
            }

            JSONArray assets = releaseInfo.getJSONArray("assets");
            if (assets == null || assets.isEmpty()) {
                log.info("Fallback release {} has no assets", tagName);
                return null;
            }
            String downloadUrl = downloadUrlResolver.resolveDownloadUrl(assets);
            if (downloadUrl == null) {
                log.info("Fallback release {} has no installer for the current platform", tagName);
                return null;
            }

            log.info("Resolved fallback download URL from {} for {}: {}",
                    fallbackSource.getName(), expectedVersion, downloadUrl);
            return downloadUrl;
        } catch (Exception ex) {
            log.warn("Failed to resolve fallback download URL from {}: {}",
                    fallbackSource.getName(), ex.getMessage());
            return null;
        }
    }
}
