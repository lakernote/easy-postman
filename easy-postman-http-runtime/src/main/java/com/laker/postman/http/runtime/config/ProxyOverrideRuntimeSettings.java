package com.laker.postman.http.runtime.config;

import com.laker.postman.certificate.TrustedCertificateEntry;
import lombok.Builder;

import java.util.List;

/**
 * Runtime settings that replace only the proxy section of a base configuration.
 */
@Builder
public record ProxyOverrideRuntimeSettings(
        HttpRuntimeSettings base,
        boolean proxyEnabled,
        String proxyMode,
        String proxyType,
        String proxyHost,
        int proxyPort,
        String proxyUsername,
        String proxyPassword,
        boolean proxySslVerificationDisabled
) implements HttpRuntimeSettings {

    private HttpRuntimeSettings delegate() {
        return base == null ? new HttpRuntimeSettings() {
        } : base;
    }

    @Override
    public int getMaxBodySize() {
        return delegate().getMaxBodySize();
    }

    @Override
    public int getMaxDownloadSize() {
        return delegate().getMaxDownloadSize();
    }

    @Override
    public int getRequestTimeout() {
        return delegate().getRequestTimeout();
    }

    @Override
    public boolean isFollowRedirects() {
        return delegate().isFollowRedirects();
    }

    @Override
    public boolean isRequestSslVerificationDisabled() {
        return delegate().isRequestSslVerificationDisabled();
    }

    @Override
    public boolean isCustomTrustMaterialEnabled() {
        return delegate().isCustomTrustMaterialEnabled();
    }

    @Override
    public List<TrustedCertificateEntry> getCustomTrustMaterialEntries() {
        return delegate().getCustomTrustMaterialEntries();
    }

    @Override
    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    @Override
    public String getProxyMode() {
        return proxyMode == null ? PROXY_MODE_MANUAL : proxyMode;
    }

    @Override
    public String getProxyType() {
        return proxyType == null ? PROXY_TYPE_HTTP : proxyType;
    }

    @Override
    public String getProxyHost() {
        return proxyHost == null ? "" : proxyHost;
    }

    @Override
    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    public String getProxyUsername() {
        return proxyUsername == null ? "" : proxyUsername;
    }

    @Override
    public String getProxyPassword() {
        return proxyPassword == null ? "" : proxyPassword;
    }

    @Override
    public boolean isProxySslVerificationDisabled() {
        return proxySslVerificationDisabled;
    }

    @Override
    public String toString() {
        return "ProxyOverrideRuntimeSettings[mode=" + getProxyMode() + ", type=" + getProxyType()
                + ", host=" + getProxyHost() + ", port=" + proxyPort + ", enabled=" + proxyEnabled + "]";
    }
}
