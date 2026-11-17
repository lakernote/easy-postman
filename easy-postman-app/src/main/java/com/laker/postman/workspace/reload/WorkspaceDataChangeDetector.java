package com.laker.postman.workspace.reload;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.model.Environment;
import com.laker.postman.performance.plan.PerformancePlanStorage;
import tools.jackson.databind.JsonNode;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.util.JsonUtil;
import com.laker.postman.util.SafeFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 记录工作区数据文件的指纹，识别由外部工具产生的修改。
 * <p>
 * 每次检查都比较内容摘要（数据文件很小，只在窗口获得焦点时检查）：只比较大小和修改时间会漏掉
 * 同大小、同一毫秒内的外部修改。内容与上次快照一致，或就是内存数据最近一次加载/保存的版本时，不视为外部修改。
 * 摘要与结构校验使用同一次读取的内容。
 */
@Slf4j
public class WorkspaceDataChangeDetector {

    private Path dataRoot;
    private final Map<WorkspaceDataFile, FileState> states = new EnumMap<>(WorkspaceDataFile.class);

    /**
     * 以磁盘当前内容为基准重新建立快照。
     */
    public synchronized void reset(Path dataRoot) {
        this.dataRoot = normalize(dataRoot);
        states.clear();
        if (this.dataRoot == null) {
            return;
        }
        for (WorkspaceDataFile file : WorkspaceDataFile.values()) {
            states.put(file, FileState.read(file.resolve(this.dataRoot)));
        }
    }

    /**
     * 返回自上次快照以来被外部修改的文件，并把快照推进到当前磁盘状态。
     * 数据目录变化（切换了工作区）时只重建快照，不报告变化。
     */
    public synchronized WorkspaceDataChanges detect(Path dataRoot) {
        Path normalized = normalize(dataRoot);
        if (normalized == null || !normalized.equals(this.dataRoot)) {
            reset(normalized);
            return WorkspaceDataChanges.NONE;
        }
        Set<WorkspaceDataFile> changed = EnumSet.noneOf(WorkspaceDataFile.class);
        Set<WorkspaceDataFile> unreadable = EnumSet.noneOf(WorkspaceDataFile.class);
        for (WorkspaceDataFile file : WorkspaceDataFile.values()) {
            Path path = file.resolve(normalized);
            FileState previous = states.get(file);
            FileState current = FileState.read(path);
            states.put(file, current);
            if (previous != null && Objects.equals(previous.digest(), current.digest())) {
                continue;
            }
            // 磁盘就是内存数据最近一次加载/保存的版本（如自身保存）时无需重新加载；
            // 只比较最近一次，历史写入值被外部恢复时仍按外部修改处理。
            if (SafeFileUtil.isLoadedVersion(path, current.digest())) {
                continue;
            }
            if (!isLoadable(file, current.content())) {
                unreadable.add(file);
            } else {
                changed.add(file);
            }
        }
        return new WorkspaceDataChanges(changed, unreadable);
    }

    /**
     * 文件缺失（按空数据加载）或内容是结构正确的 JSON 时才可以重新加载；
     * 写了一半、格式正确但结构不对的文件都不加载，避免界面被清空。
     */
    public static boolean isLoadable(WorkspaceDataFile file, Path path) {
        return isLoadable(file, FileState.read(path).content());
    }

    /**
     * @param content 文件内容；{@code null} 表示文件不存在
     */
    static boolean isLoadable(WorkspaceDataFile file, String content) {
        if (content == null) {
            return true;
        }
        try {
            if (content.isBlank()) {
                return false;
            }
            JsonNode root = JsonUtil.readTree(content);
            return root != null && hasExpectedStructure(file, root, content);
        } catch (Exception e) {
            log.debug("Workspace data file {} is not loadable yet", file, e);
            return false;
        }
    }

    /**
     * 尽量使用与真实加载相同的解析逻辑，避免“校验通过、加载失败后按空数据显示”。
     */
    private static boolean hasExpectedStructure(WorkspaceDataFile file, JsonNode root, String content) {
        return switch (file) {
            case COLLECTIONS -> isArrayOfObjects(root) && parsesAsCollections(content);
            case ENVIRONMENTS -> isArrayOfObjects(root) && parsesAs(content, Environment.class);
            case MOCK_SERVERS -> isArrayOfObjects(root) && parsesAs(content, MockServerDefinition.class);
            case FUNCTIONAL -> root.isObject()
                    && (!root.has("rows") || root.get("rows").isArray())
                    && (!root.has("csvState") || root.get("csvState").isObject() || root.get("csvState").isNull());
            case PERFORMANCE -> root.isObject() && new PerformancePlanStorage().loadWorkspaceFromJson(content) != null;
        };
    }

    private static boolean parsesAs(String content, Class<?> type) {
        JSONArray array = JSONUtil.parseArray(content);
        for (Object item : array) {
            JSONUtil.toBean((JSONObject) item, type);
        }
        return true;
    }

    private static boolean isArrayOfObjects(JsonNode node) {
        if (!node.isArray()) {
            return false;
        }
        for (JsonNode element : node) {
            if (!element.isObject()) {
                return false;
            }
        }
        return true;
    }

    private static boolean parsesAsCollections(String content) {
        CollectionDocumentJsonCodec.fromJson(JSONUtil.parseArray(content));
        return true;
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    /**
     * @param digest  内容摘要；文件不存在或不可读时为 {@code null}
     * @param content 与摘要同一次读取的内容
     */
    private record FileState(String digest, String content) {

        static FileState read(Path path) {
            try {
                if (!Files.isRegularFile(path)) {
                    return new FileState(null, null);
                }
                byte[] bytes = Files.readAllBytes(path);
                return new FileState(SafeFileUtil.digest(bytes), new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException e) {
                // 存在但暂时读不了（被占用等）：不能当作“文件不存在”按空数据加载
                return new FileState("unreadable", "");
            }
        }
    }

    public record WorkspaceDataChanges(Set<WorkspaceDataFile> changed, Set<WorkspaceDataFile> unreadable) {

        public static final WorkspaceDataChanges NONE = new WorkspaceDataChanges(Set.of(), Set.of());

        public WorkspaceDataChanges {
            changed = Collections.unmodifiableSet(copy(changed));
            unreadable = Collections.unmodifiableSet(copy(unreadable));
        }

        public boolean isEmpty() {
            return changed.isEmpty() && unreadable.isEmpty();
        }

        private static Set<WorkspaceDataFile> copy(Set<WorkspaceDataFile> files) {
            return Optional.ofNullable(files).filter(set -> !set.isEmpty())
                    .map(EnumSet::copyOf).orElseGet(() -> EnumSet.noneOf(WorkspaceDataFile.class));
        }
    }
}
