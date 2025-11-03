package com.laker.postman.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.ClientCertificate;
import com.laker.postman.panel.sidebar.ConsolePanel;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端证书管理服务
 * 负责管理 mTLS 客户端证书配置
 */
@Slf4j
public class ClientCertificateService {
    private static final Path CERT_CONFIG_FILE = SystemUtil.EASY_POSTMAN_HOME.resolve("client_certificates.json");
    private static final List<ClientCertificate> certificates = new CopyOnWriteArrayList<>();

    private ClientCertificateService() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    static {
        load();
    }

    /**
     * 从文件加载证书配置
     */
    public static void load() {
        File file = CERT_CONFIG_FILE.toFile();
        if (!file.exists()) {
            log.info("Client certificate config file not found, creating new one");
            return;
        }

        try {
            String jsonContent = FileUtil.readUtf8String(file);
            JSONArray jsonArray = JSONUtil.parseArray(jsonContent);
            certificates.clear();
            for (int i = 0; i < jsonArray.size(); i++) {
                ClientCertificate cert = jsonArray.getBean(i, ClientCertificate.class);
                certificates.add(cert);
            }
            log.info("Loaded {} client certificate configurations", certificates.size());
        } catch (Exception e) {
            log.error("Failed to load client certificates", e);
        }
    }

    /**
     * 保存证书配置到文件
     */
    public static void save() {
        try {
            File file = CERT_CONFIG_FILE.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean created = parent.mkdirs();
                if (!created) {
                    log.warn("Failed to create directory: {}", parent.getAbsolutePath());
                }
            }
            String jsonContent = JSONUtil.toJsonPrettyStr(certificates);
            FileUtil.writeUtf8String(jsonContent, file);
            log.info("Saved {} client certificate configurations", certificates.size());
        } catch (Exception e) {
            log.error("Failed to save client certificates", e);
        }
    }

    /**
     * 获取所有证书配置
     */
    public static List<ClientCertificate> getAllCertificates() {
        return new ArrayList<>(certificates);
    }

    /**
     * 添加证书配置
     */
    public static void addCertificate(ClientCertificate cert) {
        String id = cert.getId();
        if (id == null || id.isEmpty()) {
            cert.setId(UUID.randomUUID().toString());
        }
        cert.setCreatedAt(System.currentTimeMillis());
        cert.setUpdatedAt(System.currentTimeMillis());
        certificates.add(cert);
        save();
        log.info("Added client certificate: {} for host: {}", cert.getId(), cert.getHost());
    }

    /**
     * 更新证书配置
     */
    public static void updateCertificate(ClientCertificate cert) {
        for (int i = 0; i < certificates.size(); i++) {
            if (certificates.get(i).getId().equals(cert.getId())) {
                cert.setUpdatedAt(System.currentTimeMillis());
                certificates.set(i, cert);
                save();
                log.info("Updated client certificate: {}", cert.getId());
                return;
            }
        }
    }

    /**
     * 删除证书配置
     */
    public static void deleteCertificate(String id) {
        certificates.removeIf(cert -> cert.getId().equals(id));
        save();
        log.info("Deleted client certificate: {}", id);
    }

    /**
     * 根据主机名和端口查找匹配的证书
     * 返回第一个匹配的启用证书
     */
    public static ClientCertificate findMatchingCertificate(String host, int port) {
        for (ClientCertificate cert : certificates) {
            if (cert.matches(host, port)) {
                log.debug("Found matching certificate for {}:{} - {}", host, port, cert.getName());

                // 输出到控制台
                String certName = cert.getName() != null && !cert.getName().isEmpty()
                        ? cert.getName()
                        : cert.getCertPath();
                String message = MessageFormat.format(
                        I18nUtil.getMessage(MessageKeys.CERT_CONSOLE_MATCHED),
                        host, port, cert.getCertType(), certName
                );
                ConsolePanel.appendLog(message, ConsolePanel.LogType.SUCCESS);

                return cert;
            }
        }
        return null;
    }

    /**
     * 验证证书文件是否存在且可读
     */
    public static boolean validateCertificatePaths(ClientCertificate cert) {
        if (cert.getCertPath() == null || cert.getCertPath().trim().isEmpty()) {
            String message = MessageFormat.format(
                    I18nUtil.getMessage(MessageKeys.CERT_CONSOLE_VALIDATION_FAILED),
                    cert.getName() != null ? cert.getName() : "Unknown"
            );
            ConsolePanel.appendLog(message, ConsolePanel.LogType.WARN);
            return false;
        }

        File certFile = new File(cert.getCertPath());
        if (!certFile.exists() || !certFile.canRead()) {
            log.warn("Certificate file not found or not readable: {}", cert.getCertPath());
            String message = MessageFormat.format(
                    I18nUtil.getMessage(MessageKeys.CERT_CONSOLE_FILE_NOT_FOUND),
                    cert.getCertPath()
            );
            ConsolePanel.appendLog(message, ConsolePanel.LogType.ERROR);
            return false;
        }

        // 如果是 PEM 格式，还需要检查私钥文件
        if (ClientCertificate.CERT_TYPE_PEM.equals(cert.getCertType())) {
            if (cert.getKeyPath() == null || cert.getKeyPath().trim().isEmpty()) {
                String message = MessageFormat.format(
                        I18nUtil.getMessage(MessageKeys.CERT_CONSOLE_VALIDATION_FAILED),
                        cert.getName() != null ? cert.getName() : "Unknown"
                );
                ConsolePanel.appendLog(message, ConsolePanel.LogType.WARN);
                return false;
            }
            File keyFile = new File(cert.getKeyPath());
            if (!keyFile.exists() || !keyFile.canRead()) {
                log.warn("Private key file not found or not readable: {}", cert.getKeyPath());
                String message = MessageFormat.format(
                        I18nUtil.getMessage(MessageKeys.CERT_CONSOLE_FILE_NOT_FOUND),
                        cert.getKeyPath()
                );
                ConsolePanel.appendLog(message, ConsolePanel.LogType.ERROR);
                return false;
            }
        }

        return true;
    }
}

