package com.laker.postman.panel.workspace.components;

import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.service.WorkspaceService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 目录探测结果：所在 Git 仓库、是否已有工作区数据、是否为空目录。
 * <p>
 * 探测会访问磁盘（网络盘、大仓库可能较慢），必须在后台线程调用 {@link #probe(Path)}；
 * 界面线程只使用探测结果。
 *
 * @param layout 所在仓库布局；不在 Git 仓库中时为 {@code null}
 */
public record WorkspaceDirectoryProbe(Path directory, GitWorkspaceLayout layout, boolean hasData, boolean missingOrEmpty) {

    public static WorkspaceDirectoryProbe probe(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        GitWorkspaceLayout layout;
        try {
            layout = GitWorkspaceLayout.detectDirectory(normalized).orElse(null);
        } catch (Exception e) {
            layout = null;
        }
        return new WorkspaceDirectoryProbe(normalized, layout,
                WorkspaceService.containsWorkspaceData(normalized), isMissingOrEmpty(normalized));
    }

    public boolean inRepository() {
        return layout != null;
    }

    public boolean isFor(Path other) {
        return other != null && directory.equals(other.toAbsolutePath().normalize());
    }

    private static boolean isMissingOrEmpty(Path directory) {
        if (!Files.isDirectory(directory)) {
            return true;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
