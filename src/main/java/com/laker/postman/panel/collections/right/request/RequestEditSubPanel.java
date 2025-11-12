package com.laker.postman.panel.collections.right.request;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.SingletonFactory;
import com.laker.postman.common.component.table.EasyPostmanFormDataTablePanel;
import com.laker.postman.common.component.table.EasyPostmanFormUrlencodedTablePanel;
import com.laker.postman.model.*;
import com.laker.postman.panel.collections.left.RequestCollectionsLeftPanel;
import com.laker.postman.panel.collections.right.RequestEditPanel;
import com.laker.postman.panel.collections.right.request.sub.*;
import com.laker.postman.panel.history.HistoryPanel;
import com.laker.postman.panel.sidebar.ConsolePanel;
import com.laker.postman.service.EnvironmentService;
import com.laker.postman.service.collections.GroupInheritanceHelper;
import com.laker.postman.service.http.HttpSingleRequestExecutor;
import com.laker.postman.service.http.HttpUtil;
import com.laker.postman.service.http.PreparedRequestBuilder;
import com.laker.postman.service.http.RedirectHandler;
import com.laker.postman.service.http.sse.SseEventListener;
import com.laker.postman.service.http.sse.SseUiCallback;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.XmlUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSource;
import okio.ByteString;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.InterruptedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static com.laker.postman.service.http.HttpUtil.*;

/**
 * 单个请求编辑子面板，包含 URL、方法选择、Headers、Body 和响应展示
 */
@Slf4j
public class RequestEditSubPanel extends JPanel {
    private final JTextField urlField;
    private final JComboBox<String> methodBox;
    private final EasyPostmanParamsTablePanel paramsPanel;
    private final EasyHttpHeadersPanel headersPanel;
    @Getter
    private String id;
    private String name;
    private final RequestItemProtocolEnum protocol;
    private final RequestLinePanel requestLinePanel;
    //  RequestBodyPanel
    private final RequestBodyPanel requestBodyPanel;
    @Getter
    private HttpRequestItem originalRequestItem;
    private final AuthTabPanel authTabPanel;
    private final ScriptPanel scriptPanel;
    private final JTabbedPane reqTabs; // 请求选项卡面板

    // 当前请求的 SwingWorker，用于支持取消
    private transient SwingWorker<Void, Runnable> currentWorker;
    // 当前 SSE 事件源, 用于取消 SSE 请求
    private transient EventSource currentEventSource;
    // WebSocket连接对象
    private volatile transient WebSocket currentWebSocket;
    // WebSocket连接ID，用于防止过期连接的回调
    private volatile String currentWebSocketConnectionId;
    JSplitPane splitPane;
    // 双向联动控制标志，防止循环更新
    private boolean isUpdatingFromUrl = false;
    private boolean isUpdatingFromParams = false;
    @Getter
    private final ResponsePanel responsePanel;

    public RequestEditSubPanel(String id, RequestItemProtocolEnum protocol) {
        this.id = id;
        this.protocol = protocol;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // 设置边距为5
        // 1. 顶部请求行面板
        requestLinePanel = new RequestLinePanel(this::sendRequest, protocol);
        methodBox = requestLinePanel.getMethodBox();
        urlField = requestLinePanel.getUrlField();
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                parseUrlParamsToParamsPanel();
            }

            public void removeUpdate(DocumentEvent e) {
                parseUrlParamsToParamsPanel();
            }

            public void changedUpdate(DocumentEvent e) {
                parseUrlParamsToParamsPanel();
            }
        });
        // 自动补全URL协议
        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                autoPrependHttpsIfNeeded();
            }
        });
        urlField.addActionListener(e -> autoPrependHttpsIfNeeded());
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(requestLinePanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // 创建请求选项卡面板
        reqTabs = new JTabbedPane(); // 2. 创建请求选项卡面板
        reqTabs.setMinimumSize(new Dimension(400, 120));

        // 2.1 Params
        paramsPanel = new EasyPostmanParamsTablePanel();
        reqTabs.addTab(I18nUtil.getMessage(MessageKeys.TAB_PARAMS), paramsPanel); // 2.1 添加参数选项卡

        // 添加Params面板的监听器，实现从Params到URL的联动
        paramsPanel.addTableModelListener(e -> {
            if (!isUpdatingFromUrl) {
                parseParamsPanelToUrl();
            }
        });

        // 2.2 Auth 面板
        authTabPanel = new AuthTabPanel();
        reqTabs.addTab(I18nUtil.getMessage(MessageKeys.TAB_AUTHORIZATION), authTabPanel);

        // 2.3 Headers
        headersPanel = new EasyHttpHeadersPanel();
        reqTabs.addTab(I18nUtil.getMessage(MessageKeys.TAB_REQUEST_HEADERS), headersPanel);

        // 2.4 Body 面板
        requestBodyPanel = new RequestBodyPanel(protocol);
        reqTabs.addTab(I18nUtil.getMessage(MessageKeys.TAB_REQUEST_BODY), requestBodyPanel);


        // 2.5 脚本Tab
        scriptPanel = new ScriptPanel();
        reqTabs.addTab(I18nUtil.getMessage(MessageKeys.TAB_SCRIPTS), scriptPanel);

        // 3. 响应面板
        responsePanel = new ResponsePanel(protocol);
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reqTabs, responsePanel);
        splitPane.setDividerSize(5); // 设置分割条的宽度（增大以提高拖拽灵敏度）
        splitPane.setResizeWeight(0.4); // 设置分割线位置，请求和响应各占40%（降低响应面板默认高度）
        add(splitPane, BorderLayout.CENTER);

        if (protocol.isWebSocketProtocol()) {
            // WebSocket消息发送按钮事件绑定（只绑定一次）
            requestBodyPanel.setWsSendActionListener(e -> sendWebSocketMessage());
            splitPane.setResizeWeight(0.2); // 设置分割线位置，表示请求部分占20%
            // 切换到WebSocket协议时，默认选中Body Tab
            reqTabs.setSelectedComponent(requestBodyPanel);
            // 隐藏认证tab
            reqTabs.remove(authTabPanel);
            // 初始时禁用发送和定时按钮，只有连接后才可用
            requestBodyPanel.setWebSocketConnected(false);
        }
        if (protocol.isSseProtocol()) {
            splitPane.setResizeWeight(0.2); // 设置分割线位置，表示请求部分占20%
            // 隐藏认证tab
            reqTabs.remove(authTabPanel);
        }
        // 监听表单内容变化，动态更新tab红点
        addDirtyListeners();

        // bodyTypeComboBox 变化时，自动设置 Content-Type
        requestBodyPanel.getBodyTypeComboBox().addActionListener(e -> {
            String selectedType = (String) requestBodyPanel.getBodyTypeComboBox().getSelectedItem();
            if (RequestBodyPanel.BODY_TYPE_NONE.equals(selectedType)) {
                headersPanel.removeHeader("Content-Type");
            } else {
                String contentType = null;
                if (RequestBodyPanel.BODY_TYPE_RAW.equals(selectedType)) {
                    contentType = "application/json";
                } else if (RequestBodyPanel.BODY_TYPE_FORM_URLENCODED.equals(selectedType)) {
                    contentType = "application/x-www-form-urlencoded";
                } else if (RequestBodyPanel.BODY_TYPE_FORM_DATA.equals(selectedType)) {
                    contentType = "multipart/form-data";
                }
                if (contentType != null) {
                    headersPanel.setOrUpdateHeader("Content-Type", contentType);
                }
            }
        });
    }

    /**
     * 添加监听器，表单内容变化时在tab标题显示红点
     */
    private void addDirtyListeners() {
        // 监听urlField
        addDocumentListener(urlField.getDocument());
        // 监听methodBox
        methodBox.addActionListener(e -> updateTabDirty());
        // 监听headersPanel
        headersPanel.addTableModelListener(e -> updateTabDirty());
        // 监听paramsPanel
        paramsPanel.addTableModelListener(e -> updateTabDirty());
        // 监听认证面板
        authTabPanel.addDirtyListener(this::updateTabDirty);
        if (protocol.isHttpProtocol()) {
            // 监听bodyArea
            if (requestBodyPanel.getBodyArea() != null) {
                addDocumentListener(requestBodyPanel.getBodyArea().getDocument());
            }
            if (requestBodyPanel.getFormDataTablePanel() != null) {
                requestBodyPanel.getFormDataTablePanel().addTableModelListener(e -> updateTabDirty());

            }
            if (requestBodyPanel.getFormUrlencodedTablePanel() != null) {
                requestBodyPanel.getFormUrlencodedTablePanel().addTableModelListener(e -> updateTabDirty());
            }
        }
        // 监听脚本面板
        scriptPanel.addDirtyListeners(this::updateTabDirty);
    }

    private void addDocumentListener(Document document) {
        document.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateTabDirty();
            }

            public void removeUpdate(DocumentEvent e) {
                updateTabDirty();
            }

            public void changedUpdate(DocumentEvent e) {
                updateTabDirty();
            }
        });
    }


    /**
     * 设置原始请求数据（脏数据检测）
     */
    public void setOriginalRequestItem(HttpRequestItem item) {
        if (item != null && !item.isNewRequest()) {
            // 深拷贝，避免引用同一对象导致脏检测失效
            this.originalRequestItem = JSONUtil.toBean(JSONUtil.parse(item).toString(), HttpRequestItem.class);
        } else {
            this.originalRequestItem = null;
        }
    }

    /**
     * 判断当前表单内容是否被修改（与原始请求对比）
     */
    public boolean isModified() {
        if (originalRequestItem == null) return false;
        HttpRequestItem current = getCurrentRequest();
        String oriJson = JSONUtil.toJsonStr(originalRequestItem);
        String curJson = JSONUtil.toJsonStr(current);
        boolean isModified = !oriJson.equals(curJson);
        if (isModified) {
            log.debug("Request form has been modified,Request Name: {}", current.getName());
            log.debug("oriJson: {}", oriJson);
            log.debug("curJson: {}", curJson);
        }
        return isModified;
    }

    /**
     * 检查脏状态并更新tab标题
     */
    private void updateTabDirty() {
        SwingUtilities.invokeLater(() -> {
            if (originalRequestItem == null) return; // 如果没有原始请求数据，则不进行脏检测
            boolean dirty = isModified();
            SingletonFactory.getInstance(RequestEditPanel.class).updateTabDirty(this, dirty);
        });
    }

    private void sendRequest(ActionEvent e) {
        if (currentWorker != null) {
            cancelCurrentRequest();
            return;
        }

        // 清理上次请求的临时变量
        EnvironmentService.clearTemporaryVariables();

        HttpRequestItem item = getCurrentRequest();

        // 根据协议类型进行URL验证
        String url = item.getUrl();
        RequestItemProtocolEnum protocol = item.getProtocol();
        if (protocol.isWebSocketProtocol()) {
            // WebSocket只允许ws://或wss://协议
            if (!url.toLowerCase().startsWith("ws://") && !url.toLowerCase().startsWith("wss://")) {
                JOptionPane.showMessageDialog(this,
                        "WebSocket requests must use ws:// or wss:// protocol",
                        "Invalid URL Protocol", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        // 应用分组级别的认证和脚本继承
        HttpRequestItem effectiveItem = applyGroupInheritance(item);

        PreparedRequest req = PreparedRequestBuilder.build(effectiveItem);
        Map<String, Object> bindings = prepareBindings(req);
        if (!executePrescript(effectiveItem, bindings)) return;

        // 前置脚本执行完成后，再进行变量替换
        PreparedRequestBuilder.replaceVariablesAfterPreScript(req);

        if (!validateRequest(req, item)) return;
        updateUIForRequesting();

        // 协议分发 - 根据HttpRequestItem的protocol字段分发
        // 注意：这里传递 effectiveItem 以便后置脚本能够正确执行分组级别的脚本
        if (protocol.isWebSocketProtocol()) {
            handleWebSocketRequest(effectiveItem, req, bindings);
        } else if (protocol.isSseProtocol()) {
            handleSseRequest(effectiveItem, req, bindings);
        } else {
            handleHttpRequest(effectiveItem, req, bindings);
        }
    }

    /**
     * 应用分组级别的认证和脚本继承
     * 查找请求所在的分组，合并分组级别的配置
     */
    private HttpRequestItem applyGroupInheritance(HttpRequestItem item) {
        if (item == null || item.getId() == null) {
            return item;
        }

        try {
            // 获取集合树的根节点
            RequestCollectionsLeftPanel leftPanel =
                    SingletonFactory.getInstance(RequestCollectionsLeftPanel.class);
            DefaultMutableTreeNode rootNode = leftPanel.getRootTreeNode();

            // 查找请求节点
            DefaultMutableTreeNode requestNode =
                    GroupInheritanceHelper.findRequestNode(rootNode, item.getId());

            if (requestNode != null) {
                // 合并分组设置
                return GroupInheritanceHelper.mergeGroupSettings(item, requestNode);
            }
        } catch (Exception e) {
            log.warn("Failed to apply group inheritance: {}", e.getMessage());
        }

        // 如果无法应用继承，返回原始请求
        return item;
    }

    // 普通HTTP请求处理
    private void handleHttpRequest(HttpRequestItem item, PreparedRequest req, Map<String, Object> bindings) {
        currentWorker = new SwingWorker<>() {
            String statusText;
            HttpResponse resp;

            @Override
            protected void process(List<Runnable> runnables) {
                for(Runnable runnable: runnables) {
                    runnable.run();
                }
            }

            @Override
            protected Void doInBackground() {
                try {
                    publish(() -> {
                        responsePanel.setResponseTabButtonsEnable(true);
                    });
                    resp = RedirectHandler.executeWithRedirects(req, 10);
                    if (resp != null) {
                        statusText = (resp.code > 0 ? String.valueOf(resp.code) : "Unknown Status");
                    }
                } catch (InterruptedIOException ex) {
                    log.warn(ex.getMessage());
                    statusText = ex.getMessage();
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                    ConsolePanel.appendLog("[Error] " + ex.getMessage(), ConsolePanel.LogType.ERROR);
                    statusText = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                updateUIForResponse(statusText, resp);
                handleResponse(item, bindings, req, resp);
                requestLinePanel.setSendButtonToSend(RequestEditSubPanel.this::sendRequest);
                currentWorker = null;

                // 只有当前协议是 HTTP 且响应是 SSE 类型时，才提示切换到 SSE 协议
                if (resp != null && resp.isSse && protocol == RequestItemProtocolEnum.HTTP) {
                    // 弹窗提示用户是否切换到SSE监听模式
                    SingletonFactory.getInstance(RequestEditPanel.class).switchCurrentTabToSseProtocol();
                }
            }
        };
        currentWorker.execute();
    }

    // SSE请求处理
    private void handleSseRequest(HttpRequestItem item, PreparedRequest req, Map<String, Object> bindings) {
        currentWorker = new SwingWorker<>() {
            HttpResponse resp;
            StringBuilder sseBodyBuilder;
            long startTime;

            @Override
            protected void process(List<Runnable> runnables) {
                for(Runnable runnable: runnables) {
                    runnable.run();
                }
            }

            @Override
            protected Void doInBackground() {
                try {
                    startTime = System.currentTimeMillis();
                    resp = new HttpResponse();
                    sseBodyBuilder = new StringBuilder();
                    SseUiCallback callback = new SseUiCallback() {
                        @Override
                        public void onOpen(HttpResponse r, String headersText) {
                            publish(() -> {
                                updateUIForResponse(String.valueOf(r.code), r);
                                // 添加连接成功消息
                                if (responsePanel.getSseResponsePanel() != null) {
                                    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                    responsePanel.getSseResponsePanel().addMessage(MessageType.CONNECTED, timestamp, "Connected to SSE stream", null);
                                }
                            });
                        }

                        @Override
                        public void onEvent(String id, String type, String data) {
                            publish(() -> {
                                // 使用 SSEResponsePanel 来显示 SSE 消息
                                if (responsePanel.getSseResponsePanel() != null) {
                                    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                    List<TestResult> testResults = handleStreamMessage(item, bindings, data);
                                    responsePanel.getSseResponsePanel().addMessage(MessageType.RECEIVED, timestamp, data, testResults);
                                }
                            });
                        }

                        @Override
                        public void onClosed(HttpResponse r) {
                            publish(() -> {
                                updateUIForResponse(String.valueOf(r.code), r);
                                requestLinePanel.setSendButtonToSend(RequestEditSubPanel.this::sendRequest);
                                // 添加连接关闭消息
                                if (responsePanel.getSseResponsePanel() != null) {
                                    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                    responsePanel.getSseResponsePanel().addMessage(MessageType.CLOSED, timestamp, "SSE stream closed", null);
                                }
                            });
                            currentEventSource = null;
                            currentWorker = null;
                        }

                        @Override
                        public void onFailure(String errorMsg, HttpResponse r) {
                            publish(() -> {
                                responsePanel.getStatusCodeLabel().setText(I18nUtil.getMessage(MessageKeys.SSE_FAILED, errorMsg));
                                responsePanel.getStatusCodeLabel().setForeground(Color.RED);
                                updateUIForResponse(I18nUtil.getMessage(MessageKeys.SSE_FAILED, errorMsg), r);
                                requestLinePanel.setSendButtonToSend(RequestEditSubPanel.this::sendRequest);
                                // 添加错误消息
                                if (responsePanel.getSseResponsePanel() != null) {
                                    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                    responsePanel.getSseResponsePanel().addMessage(MessageType.WARNING, timestamp, "Error: " + errorMsg, null);
                                }
                            });
                            currentEventSource = null;
                            currentWorker = null;
                        }
                    };
                    currentEventSource = HttpSingleRequestExecutor.executeSSE(req, new SseEventListener(callback, resp, sseBodyBuilder, startTime));
                    publish(() -> {
                        responsePanel.setResponseTabButtonsEnable(true); // 启用响应区的tab按钮
                    });
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                    publish(() -> {
                        responsePanel.getStatusCodeLabel().setText(I18nUtil.getMessage(MessageKeys.SSE_ERROR, ex.getMessage()));
                        responsePanel.getStatusCodeLabel().setForeground(Color.RED);
                    });
                }
                return null;
            }

            @Override
            protected void done() {
                if (resp != null) {
                    SingletonFactory.getInstance(HistoryPanel.class).addRequestHistory(req, resp);
                }
            }
        };
        currentWorker.execute();
    }

    // WebSocket请求处理
    private void handleWebSocketRequest(HttpRequestItem item, PreparedRequest req, Map<String, Object> bindings) {
        // 生成新的连接ID，用于识别当前有效连接
        final String connectionId = UUID.randomUUID().toString();
        currentWebSocketConnectionId = connectionId;

        currentWorker = new SwingWorker<>() {
            final HttpResponse resp = new HttpResponse();
            long startTime;
            volatile boolean closed = false;

            @Override
            protected void process(List<Runnable> runnables) {
                for(Runnable runnable: runnables) {
                    runnable.run();
                }
            }

            @Override
            protected Void doInBackground() {
                try {
                    startTime = System.currentTimeMillis();
                    // 在连接开始时记录连接状态日志
                    log.debug("Starting WebSocket connection with ID: {}", connectionId);

                    HttpSingleRequestExecutor.executeWebSocket(req, new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocket webSocket, Response response) {
                            // 检查连接ID是否还有效，防止过期连接回调
                            if (!connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("Ignoring onOpen callback for expired connection ID: {}, current ID: {}",
                                        connectionId, currentWebSocketConnectionId);
                                // 关闭过期的连接
                                webSocket.close(1000, "Connection expired");
                                return;
                            }

                            resp.headers = new LinkedHashMap<>();
                            for (String name : response.headers().names()) {
                                resp.addHeader(name, response.headers(name));
                            }
                            resp.code = response.code();
                            resp.protocol = response.protocol().toString();
                            currentWebSocket = webSocket;
                            publish(() -> {
                                updateUIForResponse(String.valueOf(resp.code), resp);
                                reqTabs.setSelectedComponent(requestBodyPanel);
                                requestBodyPanel.getWsSendButton().requestFocusInWindow();
                                requestLinePanel.setSendButtonToClose(RequestEditSubPanel.this::sendRequest);
                                // 连接成功后启用发送和定时按钮
                                requestBodyPanel.setWebSocketConnected(true);
                            });
                            appendWebSocketMessage(MessageType.CONNECTED, response.message());
                        }

                        @Override
                        public void onMessage(okhttp3.WebSocket webSocket, String text) {
                            // 检查连接ID是否还有效
                            if (!connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("Ignoring onMessage callback for expired connection ID: {}", connectionId);
                                return;
                            }
                            appendWebSocketMessage(MessageType.RECEIVED, text, handleStreamMessage(item, bindings, text));
                        }

                        @Override
                        public void onMessage(okhttp3.WebSocket webSocket, ByteString bytes) {
                            // 检查连接ID是否还有效
                            if (!connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("Ignoring onMessage(binary) callback for expired connection ID: {}", connectionId);
                                return;
                            }
                            appendWebSocketMessage(MessageType.BINARY, bytes.hex());
                        }

                        @Override
                        public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) {
                            // 检查连接ID是否还有效
                            if (CharSequenceUtil.isBlank(currentWebSocketConnectionId) || connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("closing WebSocket: code={}, reason={}", code, reason);
                                handleWebSocketClose();
                            }
                        }

                        @Override
                        public void onClosed(WebSocket webSocket, int code, String reason) {
                            // 检查连接ID是否还有效
                            if (CharSequenceUtil.isBlank(currentWebSocketConnectionId) || connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("closed WebSocket: code={}, reason={}", code, reason);
                                appendWebSocketMessage(MessageType.CLOSED, code + " " + reason);
                                handleWebSocketClose();
                            }
                        }

                        private void handleWebSocketClose() {
                            closed = true;
                            resp.costMs = System.currentTimeMillis() - startTime;
                            currentWebSocket = null;
                            publish(() -> {
                                updateUIForResponse("closed", resp);
                                requestLinePanel.setSendButtonToSend(RequestEditSubPanel.this::sendRequest);
                                // 断开后禁用发送和定时按钮
                                requestBodyPanel.setWebSocketConnected(false);
                            });
                        }

                        @Override
                        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                            // 检查连接ID是否还有效
                            if (!connectionId.equals(currentWebSocketConnectionId)) {
                                log.debug("Ignoring onFailure callback for expired connection ID: {}", connectionId);
                                return;
                            }
                            log.error("WebSocket error", t);
                            appendWebSocketMessage(MessageType.WARNING, t.getMessage());
                            closed = true;
                            resp.costMs = System.currentTimeMillis() - startTime;
                            publish(() -> {
                                String statusMsg = response != null ? I18nUtil.getMessage(MessageKeys.WEBSOCKET_FAILED, t.getMessage() + " (" + response.code() + ")")
                                        : I18nUtil.getMessage(MessageKeys.WEBSOCKET_FAILED, t.getMessage());
                                responsePanel.getStatusCodeLabel().setText(statusMsg);
                                responsePanel.getStatusCodeLabel().setForeground(Color.RED);
                                updateUIForResponse(I18nUtil.getMessage(MessageKeys.WEBSOCKET_FAILED, t.getMessage()), resp);
                                requestLinePanel.setSendButtonToSend(RequestEditSubPanel.this::sendRequest);
                                // 失败后禁用发送和定时按钮
                                requestBodyPanel.setWebSocketConnected(false);
                            });
                        }
                    });
                    responsePanel.setResponseTabButtonsEnable(true); // 启用响应区的tab按钮
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                    publish(() -> {
                        responsePanel.getStatusCodeLabel().setText(I18nUtil.getMessage(MessageKeys.WEBSOCKET_ERROR, ex.getMessage()));
                        responsePanel.getStatusCodeLabel().setForeground(Color.RED);
                        // 失败后禁用发送和定时按钮
                        requestBodyPanel.setWebSocketConnected(false);
                    });
                }
                return null;
            }

            @Override
            protected void done() {
                // 只有当前有效连接才记录历史
                if (connectionId.equals(currentWebSocketConnectionId)) {
                    SingletonFactory.getInstance(HistoryPanel.class).addRequestHistory(req, resp);
                }
            }
        };
        currentWorker.execute();
    }

    // WebSocket消息发送逻辑
    private void sendWebSocketMessage() {

        if (currentWebSocket == null) {
            appendWebSocketMessage(MessageType.INFO, I18nUtil.getMessage(MessageKeys.WEBSOCKET_NOT_CONNECTED));
            return;
        }

        String msg = requestBodyPanel.getRawBody();
        if (CharSequenceUtil.isNotBlank(msg)) {
            currentWebSocket.send(msg); // 发送消息
            appendWebSocketMessage(MessageType.SENT, msg);
        }
    }

    private void appendWebSocketMessage(MessageType type, String text) {
        appendWebSocketMessage(type, text, null);
    }


    private void appendWebSocketMessage(MessageType type, String text, List<TestResult> testResults) {
        if (responsePanel.getProtocol().isWebSocketProtocol() && responsePanel.getWebSocketResponsePanel() != null) {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            responsePanel.getWebSocketResponsePanel().addMessage(type, timestamp, text, testResults);
        }
    }

    /**
     * 更新表单内容（用于切换请求或保存后刷新）
     */
    public void initPanelData(HttpRequestItem item) {
        this.id = item.getId();
        this.name = item.getName();
        // 拆解URL参数
        String url = item.getUrl();
        urlField.setText(url);
        urlField.setCaretPosition(0); // 设置光标到开头

        if (CollUtil.isNotEmpty(item.getParamsList())) {
            paramsPanel.setParamsList(item.getParamsList());
        } else {
            // 没有数据，尝试从 URL 解析参数
            Map<String, String> urlParams = HttpUtil.getParamsMapFromUrl(url);
            if (urlParams != null && !urlParams.isEmpty()) {
                paramsPanel.setMap(urlParams);
                item.setParamsList(paramsPanel.getParamsList());
            } else {
                paramsPanel.clear();
            }
        }
        methodBox.setSelectedItem(item.getMethod());

        if (CollUtil.isNotEmpty(item.getHeadersList())) {
            // 有数据，使用请求的 headers
            headersPanel.setHeadersList(item.getHeadersList());
        } else {
            // 没有数据，使用默认 headers
            headersPanel.setHeadersList(List.of());
        }
        // 获取最新的补充了默认值和排序的 headers 列表
        item.setHeadersList(headersPanel.getHeadersList());
        // Body
        requestBodyPanel.getBodyArea().setText(item.getBody());
        // 这是兼容性代码，防止旧数据bodyType字段为空
        if (CharSequenceUtil.isBlank(item.getBodyType())) {
            item.setBodyType(RequestBodyPanel.BODY_TYPE_NONE);
            // 根据请求headers尝试推断bodyType
            String contentType = HttpUtil.getHeaderIgnoreCase(item.getHeaders(), "Content-Type");
            if (CharSequenceUtil.isNotBlank(contentType)) {
                if (contentType.contains("application/x-www-form-urlencoded")) {
                    item.setBodyType(RequestBodyPanel.BODY_TYPE_FORM_URLENCODED);
                } else if (contentType.contains("multipart/form-data")) {
                    item.setBodyType(RequestBodyPanel.BODY_TYPE_FORM_DATA);
                } else {
                    item.setBodyType(RequestBodyPanel.BODY_TYPE_RAW);
                }
            }
        }
        requestBodyPanel.getBodyTypeComboBox().setSelectedItem(item.getBodyType());
        // rawTypeComboBox 根据body内容智能设置
        String body = item.getBody();
        if (CharSequenceUtil.isNotBlank(body)) {
            JComboBox<String> rawTypeComboBox = requestBodyPanel.getRawTypeComboBox();
            if (rawTypeComboBox != null) {
                if (JSONUtil.isTypeJSON(body)) {
                    rawTypeComboBox.setSelectedItem(RequestBodyPanel.RAW_TYPE_JSON);
                } else if (XmlUtil.isXml(body)) {
                    rawTypeComboBox.setSelectedItem(RequestBodyPanel.RAW_TYPE_XML);
                } else {
                    rawTypeComboBox.setSelectedItem(RequestBodyPanel.RAW_TYPE_TEXT);
                }
            }
        }

        if (CollUtil.isNotEmpty(item.getFormDataList())) {
            EasyPostmanFormDataTablePanel formDataTablePanel = requestBodyPanel.getFormDataTablePanel();
            formDataTablePanel.setFormDataList(item.getFormDataList());
        }

        if (CollUtil.isNotEmpty(item.getUrlencodedList())) {
            EasyPostmanFormUrlencodedTablePanel urlencodedTablePanel = requestBodyPanel.getFormUrlencodedTablePanel();
            urlencodedTablePanel.setFormDataList(item.getUrlencodedList());
        }

        // 认证Tab
        authTabPanel.setAuthType(item.getAuthType());
        authTabPanel.setUsername(item.getAuthUsername());
        authTabPanel.setPassword(item.getAuthPassword());
        authTabPanel.setToken(item.getAuthToken());

        // 前置/后置脚本
        scriptPanel.setPrescript(item.getPrescript() == null ? "" : item.getPrescript());
        scriptPanel.setPostscript(item.getPostscript() == null ? "" : item.getPostscript());
        // 设置原始数据用于脏检测
        setOriginalRequestItem(item);

        // 根据请求类型智能选择默认Tab
        selectDefaultTabByRequestType(item);
    }

    /**
     * 获取当前表单内容封装为HttpRequestItem
     */
    public HttpRequestItem getCurrentRequest() {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(this.id); // 保证id不丢失
        item.setName(this.name); // 保证name不丢失
        item.setUrl(urlField.getText().trim());
        item.setMethod((String) methodBox.getSelectedItem());
        item.setProtocol(protocol);
        item.setHeadersList(headersPanel.getHeadersList());
        item.setParamsList(paramsPanel.getParamsList());
        item.setBody(requestBodyPanel.getBodyArea().getText().trim());
        item.setBodyType(Objects.requireNonNull(requestBodyPanel.getBodyTypeComboBox().getSelectedItem()).toString());
        String bodyType = requestBodyPanel.getBodyType();
        if (RequestBodyPanel.BODY_TYPE_FORM_DATA.equals(bodyType)) {
            EasyPostmanFormDataTablePanel formDataTablePanel = requestBodyPanel.getFormDataTablePanel();
            item.setFormDataList(formDataTablePanel.getFormDataList());
            item.setBody(""); // form-data模式下，body通常不直接使用
            item.setUrlencodedList(new ArrayList<>());
        } else if (RequestBodyPanel.BODY_TYPE_FORM_URLENCODED.equals(bodyType)) {
            item.setBody(""); // x-www-form-urlencoded模式下，body通常不直接使用
            item.setFormDataList(new ArrayList<>());
            EasyPostmanFormUrlencodedTablePanel urlencodedTablePanel = requestBodyPanel.getFormUrlencodedTablePanel();
            item.setUrlencodedList(urlencodedTablePanel.getFormDataList());
        } else if (RequestBodyPanel.BODY_TYPE_RAW.equals(bodyType)) {
            item.setBody(requestBodyPanel.getRawBody());
            item.setFormDataList(new ArrayList<>());
            item.setUrlencodedList(new ArrayList<>());
        }
        // 认证Tab收集
        item.setAuthType(authTabPanel.getAuthType());
        item.setAuthUsername(authTabPanel.getUsername());
        item.setAuthPassword(authTabPanel.getPassword());
        item.setAuthToken(authTabPanel.getToken());
        // 脚本内容
        item.setPrescript(scriptPanel.getPrescript());
        item.setPostscript(scriptPanel.getPostscript());
        return item;
    }

    /**
     * 解析url中的参数到paramsPanel，并与现有params合并去重
     */
    private void parseUrlParamsToParamsPanel() {
        if (isUpdatingFromParams) {
            return; // 如果正在从Params更新URL，避免循环更新
        }

        isUpdatingFromUrl = true;
        try {
            String url = urlField.getText();
            Map<String, String> urlParams = getParamsMapFromUrl(url);

            // 获取当前Params面板的参数
            Map<String, String> currentParams = paramsPanel.getMap();

            // 如果URL中没有参数，清空Params面板
            if (urlParams == null || urlParams.isEmpty()) {
                if (!currentParams.isEmpty()) {
                    paramsPanel.clear();
                }
                return;
            }

            // 检查URL参数和当前Params参数是否完全一致
            if (!urlParams.equals(currentParams)) {
                // 完全用URL中的参数替换Params面板
                paramsPanel.setMap(urlParams);
            }
        } finally {
            isUpdatingFromUrl = false;
        }
    }

    /**
     * 从Params面板同步更新到URL栏（类似Postman的双向联动）
     */
    private void parseParamsPanelToUrl() {
        if (isUpdatingFromUrl) {
            return; // 如果正在从URL更新Params，避免循环更新
        }

        isUpdatingFromParams = true;
        try {
            String currentUrl = urlField.getText().trim();
            String baseUrl = HttpUtil.getBaseUrlWithoutParams(currentUrl);

            if (baseUrl == null || baseUrl.isEmpty()) {
                return; // 没有基础URL，无法构建完整URL
            }

            // 获取Params面板的所有参数
            Map<String, String> params = paramsPanel.getMap();

            // 使用HttpUtil中的方法构建完整URL
            String newUrl = HttpUtil.buildUrlFromParamsMap(baseUrl, params);

            // 只有在URL真正发生变化时才更新
            if (!newUrl.equals(currentUrl)) {
                urlField.setText(newUrl);
                urlField.setCaretPosition(0); // 设置光标到开头
            }
        } finally {
            isUpdatingFromParams = false;
        }
    }

    // 取消当前请求
    private void cancelCurrentRequest() {
        if (currentEventSource != null) {
            currentEventSource.cancel(); // 取消SSE请求
            currentEventSource = null;
        }
        if (currentWebSocket != null) {
            currentWebSocket.close(1000, "User canceled"); // 关闭WebSocket连接
            currentWebSocket = null;
        }
        // 清空WebSocket连接ID，使过期的连接回调失效
        currentWebSocketConnectionId = null;

        currentWorker.cancel(true);
        requestLinePanel.setSendButtonToSend(this::sendRequest);
        responsePanel.getStatusCodeLabel().setText(I18nUtil.getMessage(MessageKeys.STATUS_CANCELED));
        responsePanel.getStatusCodeLabel().setForeground(new Color(255, 140, 0));
        currentWorker = null;

        // 为WebSocket连接添加取消消息
        if (protocol.isWebSocketProtocol()) {
            appendWebSocketMessage(MessageType.WARNING, "User canceled");
        }
    }

    // UI状态：请求中
    private void updateUIForRequesting() {
        responsePanel.setStatus(I18nUtil.getMessage(MessageKeys.STATUS_REQUESTING), new Color(255, 140, 0));
        responsePanel.setResponseTime(0);
        responsePanel.setResponseSize(0);
        requestLinePanel.setSendButtonToCancel(this::sendRequest);
        if (protocol.isHttpProtocol()) {
            responsePanel.getNetworkLogPanel().clearLog();
            responsePanel.setResponseTabButtonsEnable(false);
            responsePanel.getResponseBodyPanel().setEnabled(false);
        }
        responsePanel.clearAll();
    }

    // UI状态：响应完成
    private void updateUIForResponse(String statusText, HttpResponse resp) {
        if (resp == null) {
            responsePanel.setStatus(I18nUtil.getMessage(MessageKeys.STATUS_PREFIX, statusText), Color.RED);
            if (protocol.isHttpProtocol()) {
                responsePanel.getResponseBodyPanel().setEnabled(true);
            }
            return;
        }
        responsePanel.setResponseHeaders(resp);
        if (!protocol.isWebSocketProtocol() && !protocol.isSseProtocol()) {
            responsePanel.setTiming(resp);
            responsePanel.setResponseBody(resp);
            responsePanel.getResponseBodyPanel().setEnabled(true);
        }
        Color statusColor = getStatusColor(resp.code);
        responsePanel.setStatus(I18nUtil.getMessage(MessageKeys.STATUS_PREFIX, statusText), statusColor);
        responsePanel.setResponseTime(resp.costMs);
        responsePanel.setResponseSize(resp.bodySize, resp.httpEventInfo);
    }

    private void setTestResults(List<TestResult> testResults) {
        responsePanel.setTestResults(testResults);
    }

    // 处理响应、后置脚本、变量提取、历史
    private void handleResponse(HttpRequestItem item, Map<String, Object> bindings, PreparedRequest
            req, HttpResponse resp) {
        if (resp == null) {
            log.error("Response is null, cannot handle response.");
            return;
        }
        try {
            HttpUtil.postBindings(bindings, resp);
            // 清空 pm.testResults，防止断言结果累加
            Postman pm = (Postman) bindings.get("pm");
            if (pm != null) {
                pm.testResults.clear();
            }
            executePostscript(item.getPostscript(), bindings);
            setTestResults(pm != null ? pm.testResults : new ArrayList<>());
            SingletonFactory.getInstance(HistoryPanel.class).addRequestHistory(req, resp);
        } catch (Exception ex) {
            log.error("Error handling response: {}", ex.getMessage(), ex);
            ConsolePanel.appendLog("[Error] " + ex.getMessage(), ConsolePanel.LogType.ERROR);
        }
    }

    /**
     * 如果urlField内容没有协议，自动补全https:// 或 wss://，根据protocol判断
     */
    private void autoPrependHttpsIfNeeded() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        // 如果是环境变量占位符开头，直接返回
        if (url.startsWith("{{")) return;
        String lower = url.toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ws://") || lower.startsWith("wss://"))) {
            if (protocol != null && protocol.isWebSocketProtocol()) {
                url = "wss://" + url;
            } else {
                url = "https://" + url;
            }
            urlField.setText(url);
        }
    }

    private List<TestResult> handleStreamMessage(HttpRequestItem item, Map<String, Object> bindings, String
            message) {
        try {
            HttpResponse resp = new HttpResponse();
            resp.body = message;
            resp.bodySize = message != null ? message.length() : 0;
            HttpUtil.postBindings(bindings, resp);
            // 清空 pm.testResults，防止累加
            Postman pm = (Postman) bindings.get("pm");
            if (pm != null) {
                pm.testResults.clear();
            }
            executePostscript(item.getPostscript(), bindings);
            if (pm != null) {
                return new ArrayList<>(pm.testResults); // 返回副本，避免后续修改影响
            }
        } catch (Exception ex) {
            log.error("Error handling stream message: {}", ex.getMessage(), ex);
            ConsolePanel.appendLog("[Error] " + ex.getMessage(), ConsolePanel.LogType.ERROR);
            return List.of();
        }
        return List.of();
    }

    /**
     * 根据请求类型智能选择默认Tab
     * 优化用户体验，根据请求的特点自动切换到最相关的Tab
     */
    private void selectDefaultTabByRequestType(HttpRequestItem item) {
        if (item == null) {
            return;
        }

        String method = item.getMethod();
        String bodyType = item.getBodyType();

        // WebSocket协议：默认选择Body Tab（用于发送消息）
        if (protocol.isWebSocketProtocol()) {
            reqTabs.setSelectedComponent(requestBodyPanel);
            return;
        }

        // SSE协议：默认选择Params Tab（SSE通常通过URL参数配置）
        if (protocol.isSseProtocol()) {
            reqTabs.setSelectedComponent(paramsPanel);
            return;
        }

        // HTTP协议智能判断
        // 1. 如果有Body内容（非空且非none类型），优先显示Body Tab
        if (CharSequenceUtil.isNotBlank(item.getBody())
                && !RequestBodyPanel.BODY_TYPE_NONE.equals(bodyType)) {
            reqTabs.setSelectedComponent(requestBodyPanel);
            return;
        }

        // 2. 如果有form-data或urlencoded数据，显示Body Tab
        if (CollUtil.isNotEmpty(item.getFormDataList())
                || CollUtil.isNotEmpty(item.getUrlencodedList())) {
            reqTabs.setSelectedComponent(requestBodyPanel);
            return;
        }

        // 3. POST/PUT/PATCH请求：默认显示Body Tab（这些方法通常需要发送数据）
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            reqTabs.setSelectedComponent(requestBodyPanel);
            return;
        }


        // 4. GET/DELETE/HEAD/OPTIONS等查询类请求：默认显示Params Tab
        if ("GET".equals(method) || "DELETE".equals(method)
                || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            reqTabs.setSelectedComponent(paramsPanel);
            return;
        }

        // 5. 默认情况：显示Params Tab（最常用）
        reqTabs.setSelectedComponent(paramsPanel);
    }

    /**
     * 程序化点击发送按钮
     */
    public void clickSendButton() {
        SwingUtilities.invokeLater(() -> requestLinePanel.getSendButton().doClick());
    }
}

