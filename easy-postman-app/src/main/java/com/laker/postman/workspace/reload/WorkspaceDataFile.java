package com.laker.postman.workspace.reload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;

/**
 * 工作区数据目录中可被外部工具（IDE、AI、Git）修改并需要重新加载的数据文件。
 */
@Getter
@RequiredArgsConstructor
public enum WorkspaceDataFile {
    COLLECTIONS("collections.json"),
    ENVIRONMENTS("environments.json"),
    FUNCTIONAL("functional_config.json"),
    PERFORMANCE("performance_config.json"),
    MOCK_SERVERS("mock_servers.json");

    private final String fileName;

    public Path resolve(Path dataRoot) {
        return dataRoot.resolve(fileName);
    }
}
