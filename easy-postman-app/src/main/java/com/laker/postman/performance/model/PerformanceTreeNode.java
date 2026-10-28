package com.laker.postman.performance.model;

import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.extractor.ExtractorData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;


import com.laker.postman.service.variable.RequestExecutionScope;

public class PerformanceTreeNode {
    public String name;
    public NodeType type;
    public HttpRequestItem httpRequestItem; // 仅REQUEST节点用
    public PerformanceRequestSnapshot requestSnapshot; // REQUEST节点的无GUI请求快照
    public ThreadGroupData threadGroupData; // 线程组数据
    public CsvDataSetData csvDataSetData;   // CSV Data Set 配置
    public LoopData loopData;             // Loop 控制器数据
    public ConditionData conditionData;   // 条件控制器数据
    public WhileData whileData;   // While 控制器数据
    public AssertionData assertionData;   // 断言数据
    public ExtractorData extractorData;   // 提取器数据
    public TimerData timerData;           // 定时器数据
    public SsePerformanceData ssePerformanceData; // SSE 压测配置，仅 SSE 阶段节点使用
    public boolean ssePerformanceEnabled; // REQUEST 节点显式选择的 SSE 压测场景
    public WebSocketPerformanceData webSocketPerformanceData; // WebSocket 压测配置，REQUEST/WS 节点共用
    public boolean enabled = true;        // 是否启用，默认启用
    public RequestExecutionScope requestExecutionScope; // REQUEST 节点执行所需分组变量快照

    public PerformanceTreeNode(String name, NodeType type) {
        this.name = name;
        this.type = type;
    }

    public PerformanceTreeNode(String name, NodeType type, Object data) {
        this.name = name;
        this.type = type;
        switch (type) {
            case THREAD_GROUP -> this.threadGroupData = (ThreadGroupData) data;
            case CSV_DATA_SET -> this.csvDataSetData = (CsvDataSetData) data;
            case LOOP -> this.loopData = (LoopData) data;
            case CONDITION -> this.conditionData = (ConditionData) data;
            case WHILE -> this.whileData = (WhileData) data;
            case REQUEST -> this.httpRequestItem = (HttpRequestItem) data;
            case ASSERTION -> this.assertionData = (AssertionData) data;
            case EXTRACTOR -> this.extractorData = (ExtractorData) data;
            case TIMER -> this.timerData = (TimerData) data;
            case SIMPLE, ONCE_ONLY, SSE_CONNECT, SSE_READ, WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE, ROOT -> {
            }
        }
    }

    public Object getNodeData() {
        return switch (type) {
            case THREAD_GROUP -> threadGroupData;
            case CSV_DATA_SET -> csvDataSetData;
            case LOOP -> loopData;
            case CONDITION -> conditionData;
            case WHILE -> whileData;
            case REQUEST -> httpRequestItem;
            case ASSERTION -> assertionData;
            case EXTRACTOR -> extractorData;
            case TIMER -> timerData;
            case SIMPLE, ONCE_ONLY, SSE_CONNECT, SSE_READ, WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE, ROOT -> null;
            default -> null;
        };
    }

    public void setNodeData(Object data) {
        switch (type) {
            case THREAD_GROUP -> this.threadGroupData = (ThreadGroupData) data;
            case CSV_DATA_SET -> this.csvDataSetData = (CsvDataSetData) data;
            case LOOP -> this.loopData = (LoopData) data;
            case CONDITION -> this.conditionData = (ConditionData) data;
            case WHILE -> this.whileData = (WhileData) data;
            case REQUEST -> this.httpRequestItem = (com.laker.postman.request.model.HttpRequestItem) data;
            case ASSERTION -> this.assertionData = (AssertionData) data;
            case EXTRACTOR -> this.extractorData = (ExtractorData) data;
            case TIMER -> this.timerData = (TimerData) data;
            case SIMPLE, ONCE_ONLY, SSE_CONNECT, SSE_READ, WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE, ROOT -> {
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
