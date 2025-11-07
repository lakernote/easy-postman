package com.laker.postman.service.http.ssl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.laker.postman.model.ClientCertificate;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.ssl.pem.util.PemUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 客户端证书加载器
 * 支持加载 PFX/P12 和 PEM 格式的客户端证书
 */
@Slf4j
public class ClientCertificateLoader {

    private ClientCertificateLoader() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * 从客户端证书配置创建 KeyManager
     */
    public static KeyManager[] createKeyManagers(ClientCertificate cert) throws Exception {
        if (cert == null) {
            return new KeyManager[0];
        }

        if (ClientCertificate.CERT_TYPE_PFX.equals(cert.getCertType())) {
            return createKeyManagersFromPFX(cert);
        } else if (ClientCertificate.CERT_TYPE_PEM.equals(cert.getCertType())) {
            return createKeyManagersFromPEM(cert);
        } else {
            throw new IllegalArgumentException("Unsupported certificate type: " + cert.getCertType());
        }
    }

    /**
     * 从 PFX/P12 文件创建 KeyManager
     */
    private static KeyManager[] createKeyManagersFromPFX(ClientCertificate cert) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = cert.getCertPassword() != null ?
            cert.getCertPassword().toCharArray() : new char[0];

        try (FileInputStream fis = new FileInputStream(cert.getCertPath())) {
            keyStore.load(fis, password);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        log.debug("Loaded PFX/P12 certificate from: {}", cert.getCertPath());
        return kmf.getKeyManagers();
    }

    /**
     * 从 PEM 文件创建 KeyManager
     */
    private static KeyManager[] createKeyManagersFromPEM(ClientCertificate cert) throws Exception {
        log.debug("Loaded PEM certificate from: {} and key from: {}", cert.getCertPath(), cert.getKeyPath());
        try (BufferedInputStream certInputStream = FileUtil.getInputStream(cert.getCertPath());
             BufferedInputStream keyInputStream = FileUtil.getInputStream(cert.getKeyPath())) {

            X509ExtendedKeyManager keyManager = StrUtil.isNotBlank(cert.getKeyPassword())
                    ? PemUtils.loadIdentityMaterial(certInputStream, keyInputStream, cert.getKeyPassword().toCharArray())
                    : PemUtils.loadIdentityMaterial(certInputStream, keyInputStream);

            return new KeyManager[]{keyManager};
        }
    }

    /**
     * 创建一个 X509KeyManager 包装器，用于在握手时选择正确的证书
     */
    public static class HostAwareKeyManager implements X509KeyManager {
        private final X509KeyManager delegate;
        private final String targetHost;

        public HostAwareKeyManager(X509KeyManager delegate, String targetHost) {
            this.delegate = delegate;
            this.targetHost = targetHost;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return delegate.getClientAliases(keyType, issuers);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            String alias = delegate.chooseClientAlias(keyType, issuers, socket);
            log.debug("Choosing client certificate alias: {} for host: {}", alias, targetHost);
            return alias;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return delegate.getPrivateKey(alias);
        }
    }
}

