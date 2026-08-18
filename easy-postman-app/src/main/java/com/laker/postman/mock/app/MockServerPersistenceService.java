package com.laker.postman.mock.app;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.ioc.Component;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MockServerPersistenceService {
    private static final long MAX_CONFIG_BYTES = 16L * 1024 * 1024;

    public List<MockServerDefinition> load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            if (Files.size(path) > MAX_CONFIG_BYTES) {
                log.warn("Ignoring oversized mock server config: {}", path);
                return List.of();
            }
            JSONArray array = JSONUtil.readJSONArray(path.toFile(), StandardCharsets.UTF_8);
            return new ArrayList<>(JSONUtil.toList(array, MockServerDefinition.class));
        } catch (Exception ex) {
            log.warn("Failed to load mock server config: {}", path, ex);
            return List.of();
        }
    }

    public void save(List<MockServerDefinition> definitions) {
        Path target = configPath();
        Path parent = target.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            String json = JSONUtil.toJsonPrettyStr(definitions == null ? List.of() : definitions);
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException ex) {
            throw new IllegalStateException(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_PERSIST_FAILED), ex);
        }
    }

    Path configPath() {
        Workspace workspace = WorkspaceService.getInstance().getCurrentWorkspace();
        return Path.of(ConfigPathConstants.getMockServersPath(workspace));
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
