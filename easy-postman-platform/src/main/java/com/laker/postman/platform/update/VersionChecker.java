package com.laker.postman.platform.update;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.laker.postman.platform.update.asset.PlatformDownloadUrlResolver;
import com.laker.postman.platform.update.model.UpdateInfo;
import com.laker.postman.platform.update.source.UpdateSource;
import com.laker.postman.platform.update.source.UpdateSourceSelector;
import com.laker.postman.platform.update.version.VersionComparator;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 版本检查器 - 负责检查远程版本信息
 *
 * <p>重构后的版本：使用策略模式分离不同更新源的实现，提高代码可维护性和可扩展性。</p>
 */
@Slf4j
public class VersionChecker {

    private final UpdateSourceSelector sourceSelector;
    private final PlatformDownloadUrlResolver downloadUrlResolver;

    public VersionChecker() {
        this(UpdateSettingsProvider.defaults());
    }

    public VersionChecker(UpdateSettingsProvider settingsProvider) {
        this.sourceSelector = new UpdateSourceSelector(settingsProvider);
        this.downloadUrlResolver = new PlatformDownloadUrlResolver();
    }

    /**
     * 检查更新信息
     */
    public UpdateInfo checkForUpdate() {
        try {
            String currentVersion = SystemUtil.getCurrentVersion();

            // 选择最佳更新源
            UpdateSource primarySource = sourceSelector.selectBestSource();
            JSONObject releaseInfo = primarySource.fetchLatestReleaseInfo();

            if (releaseInfo == null) {
                // 尝试备用源
                UpdateSource fallbackSource = sourceSelector.getFallbackSource(primarySource);
                log.info("Primary source failed, trying fallback source: {}", fallbackSource.getName());
                releaseInfo = fallbackSource.fetchLatestReleaseInfo();
            }

            if (releaseInfo == null) {
                return UpdateInfo.noUpdateAvailable(I18nUtil.getMessage(MessageKeys.UPDATE_FETCH_RELEASE_FAILED));
            }

            String latestVersion = releaseInfo.getStr("tag_name");
            String sourceName = releaseInfo.getStr(UpdateSource.SOURCE_METADATA_KEY, "unknown");
            log.info("Current version: {}, Latest version: {} (from {})", currentVersion, latestVersion, sourceName);

            if (latestVersion == null) {
                return UpdateInfo.noUpdateAvailable(I18nUtil.getMessage(MessageKeys.UPDATE_NO_VERSION_INFO));
            }

            if (VersionComparator.isNewer(latestVersion, currentVersion)) {
                String downloadUrl = getDownloadUrl(releaseInfo);
                if (CharSequenceUtil.isNotBlank(downloadUrl)) {
                    return UpdateInfo.updateAvailable(currentVersion, latestVersion, releaseInfo);
                } else {
                    // 有新版本，但当前平台没有对应的安装包（可能还在上传中）
                    log.info("Newer version {} found, but no download asset available for current platform", latestVersion);
                    return UpdateInfo.updateAvailableNoAsset(currentVersion, latestVersion, releaseInfo);
                }
            } else {
                return UpdateInfo.noUpdateAvailable(I18nUtil.getMessage(MessageKeys.UPDATE_ALREADY_LATEST, currentVersion));
            }

        } catch (Exception e) {
            log.debug("Failed to check for updates", e);
            return UpdateInfo.checkFailed(e.getMessage());
        }
    }

    /**
     * 获取下载链接
     */
    public String getDownloadUrl(JSONObject releaseInfo) {
        if (releaseInfo == null) {
            return null;
        }

        JSONArray assets = releaseInfo.getJSONArray("assets");
        if (assets == null || assets.isEmpty()) {
            log.info("Release assets is empty");
            return null;
        }

        String source = releaseInfo.getStr(UpdateSource.SOURCE_METADATA_KEY, "unknown");
        String downloadUrl = downloadUrlResolver.resolveDownloadUrl(assets);

        if (downloadUrl != null) {
            log.info("Found download URL from {}: {}", source, downloadUrl);
        }

        return downloadUrl;
    }
}
