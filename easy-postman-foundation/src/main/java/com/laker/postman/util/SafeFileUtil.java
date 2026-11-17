package com.laker.postman.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作区数据文件的安全读写工具。
 * <p>
 * 数据文件可能被 IDE、AI 或 Git 在外部修改，因此写入必须原子替换，
 * 读取失败时必须保留原文件，不能删除或用默认内容覆盖。
 */
@Slf4j
@UtilityClass
public class SafeFileUtil {

    private static final String ABSENT = "";
    /**
     * 每个数据文件上次加载/保存时的磁盘状态与对应的内存内容，用于跳过无变化的写入和识别自身写入。
     */
    private static final Map<Path, Baseline> BASELINES = new ConcurrentHashMap<>();
    /**
     * 加载开始前的磁盘摘要：基准必须对应真正被读取的版本，读取后再取摘要可能拿到之后被外部改写的版本。
     */
    private static final Map<Path, String> LOADING_DIGESTS = new ConcurrentHashMap<>();

    private record Baseline(String diskDigest, String contentDigest) {
    }

    /**
     * 读取数据文件用于加载，并把这次读到的字节摘要记为待定基准，供随后的 {@link #rememberLoaded} 使用。
     * 摘要与返回内容来自同一次读取，不会出现“解析的是旧版本、基准却是新版本”的情况。
     *
     * @return 文件内容；文件不存在时为空（“不存在”同样记为基准）
     */
    public static Optional<String> readForLoad(Path file) throws IOException {
        Path key = registryKey(file);
        if (!Files.isRegularFile(file)) {
            LOADING_DIGESTS.put(key, ABSENT);
            return Optional.empty();
        }
        byte[] bytes = Files.readAllBytes(file);
        LOADING_DIGESTS.put(key, digest(bytes));
        return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 记录加载基准：加载时的磁盘状态（文件不存在也是一种状态）以及按加载结果重新序列化的内容。
     * 磁盘状态取自 {@link #readForLoad}；没有进行中的加载时沿用已有基准的磁盘状态（只更新内存内容，
     * 例如界面在加载后补齐默认数据），都没有时才读取当前磁盘。
     *
     * @param loadedContent 加载后的内存数据按保存格式序列化的内容；无法得到时传 {@code null}
     */
    public static void rememberLoaded(Path file, String loadedContent) {
        if (file == null) {
            return;
        }
        Path key = registryKey(file);
        String diskDigest = LOADING_DIGESTS.remove(key);
        if (diskDigest == null) {
            Baseline existing = BASELINES.get(key);
            diskDigest = existing == null ? null : existing.diskDigest();
        }
        BASELINES.put(key, new Baseline(
                diskDigest != null ? diskDigest : fileDigest(file).orElse(ABSENT),
                loadedContent == null ? null : digest(loadedContent.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * 磁盘当前内容（摘要，文件不存在时为 {@code null}）是否就是本进程最近一次加载或保存的版本，
     * 即内存数据与该磁盘版本一致，不需要重新加载。
     */
    public static boolean isLoadedVersion(Path file, String currentDigest) {
        if (file == null) {
            return false;
        }
        Baseline baseline = BASELINES.get(registryKey(file));
        return baseline != null && baseline.diskDigest().equals(currentDigest == null ? ABSENT : currentDigest);
    }

    /**
     * 内容与加载基准相同时不写入：避免仅仅因为切换/退出就重新生成被 Git 删除的文件，
     * 也避免用未改动的内存数据覆盖用户推迟重新加载的外部修改。
     *
     * @return 是否实际写入
     */
    public static boolean writeIfChanged(Path target, String content) throws IOException {
        Baseline baseline = BASELINES.get(registryKey(target));
        String contentDigest = digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        if (baseline != null && Objects.equals(baseline.contentDigest(), contentDigest)) {
            return false;
        }
        writeStringAtomically(target, content);
        return true;
    }

    /**
     * 先写入同目录临时文件，再原子替换目标文件，避免外部读取到半个文件。
     */
    public static void writeStringAtomically(Path target, String content) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName(), ".tmp");
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(temporary, bytes);
            backupExternalVersion(absoluteTarget, digest(bytes));
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            String written = digest(bytes);
            LOADING_DIGESTS.remove(registryKey(absoluteTarget));
            BASELINES.put(registryKey(absoluteTarget), new Baseline(written, written));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 磁盘在上次加载/保存之后被外部修改（例如用户推迟了重新加载）时，覆盖前先备份外部版本。
     */
    private static void backupExternalVersion(Path target, String newDigest) {
        Baseline baseline = BASELINES.get(registryKey(target));
        String current = fileDigest(target).orElse(ABSENT);
        if (baseline != null && !current.equals(ABSENT) && !current.equals(baseline.diskDigest())
                && !current.equals(newDigest)) {
            backupUnreadable(target, overwrittenBackupDirectory());
        }
    }

    static Path overwrittenBackupDirectory() {
        return Paths.get(SystemUtil.getEasyPostmanPath(), "backups", "overwritten");
    }

    /**
     * 把无法解析的数据文件复制到应用数据目录下的备份目录，原文件保持不变。
     * 备份放在工作区之外，避免污染工作区所在的 Git 仓库。
     */
    public static Optional<Path> backupUnreadable(Path file) {
        return backupUnreadable(file, Paths.get(SystemUtil.getEasyPostmanPath(), "backups", "unreadable"));
    }

    static Optional<Path> backupUnreadable(Path file, Path backupDirectory) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] content = Files.readAllBytes(file);
            // 以内容摘要命名，同一份损坏内容反复加载时只备份一次。
            String backupName = file.getFileName() + "." + shortDigest(content) + ".bak";
            Path backup = backupDirectory.resolve(backupName);
            if (!Files.exists(backup)) {
                Files.createDirectories(backupDirectory);
                Files.write(backup, content);
            }
            log.warn("Data file backed up: {}, backup: {}", file.toAbsolutePath(), backup);
            return Optional.of(backup);
        } catch (IOException e) {
            log.error("Failed to back up unreadable data file: {}", file.toAbsolutePath(), e);
            return Optional.empty();
        }
    }

    /**
     * 文件内容的 SHA-256 摘要；文件不存在或不可读时返回空。
     */
    public static Optional<String> fileDigest(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            log.debug("Failed to read file for digest: {}", file, e);
            return Optional.empty();
        }
    }

    public static String digest(byte[] content) {
        return HexFormat.of().formatHex(sha256(content));
    }

    private static Path registryKey(Path file) {
        return file.toAbsolutePath().normalize();
    }

    private static String shortDigest(byte[] content) {
        return HexFormat.of().formatHex(sha256(content), 0, 6);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
