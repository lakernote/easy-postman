package com.laker.postman.plugin.redis;

import com.laker.postman.common.component.notification.NotificationCenter;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.EasyJSpinner;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSidebarHeader;
import com.laker.postman.common.component.ToolWindowSidebarToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ClearButton;
import com.laker.postman.common.component.button.CompactPrimaryButton;
import com.laker.postman.common.component.button.FormatButton;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.component.connection.ConnectionToolbarUi;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.plugin.api.PluginStorage;
import com.laker.postman.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.laker.postman.plugin.redis.RedisI18n.t;

@Slf4j
public class RedisPanel extends JPanel {
    private static final int MAX_KEY_SCAN = 2000;
    private static final int MAX_HISTORY = 30;
    private static final int MAX_DB_INDEX = 9999;
    private static final String CONNECT_CARD = "connect";
    private static final String DISCONNECT_CARD = "disconnect";
    private static final int GROUP_GAP = 12;
    private static final int PROFILE_GROUP_MIN_WIDTH = 150;
    private static final int PROFILE_GROUP_PREF_WIDTH = 240;
    private static final int HOST_GROUP_MIN_WIDTH = 110;
    private static final int HOST_GROUP_PREF_WIDTH = 220;
    private static final int PORT_DB_GROUP_MIN_WIDTH = 180;
    private static final int PORT_DB_GROUP_PREF_WIDTH = 190;
    private static final int PORT_FIELD_MIN_WIDTH = 84;
    private static final int DB_FIELD_WIDTH = 58;

    private static final String CMD_GET = "GET";
    private static final String CMD_SET = "SET";
    private static final String CMD_DEL = "DEL";
    private static final String CMD_EXISTS = "EXISTS";
    private static final String CMD_TYPE = "TYPE";
    private static final String CMD_TTL = "TTL";
    private static final String CMD_HGET = "HGET";
    private static final String CMD_HGETALL = "HGETALL";
    private static final String CMD_LRANGE = "LRANGE";
    private static final String CMD_SMEMBERS = "SMEMBERS";
    private static final String CMD_ZRANGE = "ZRANGE";

    private static final String[] COMMANDS = {
            CMD_GET, CMD_SET, CMD_DEL, CMD_EXISTS, CMD_TYPE, CMD_TTL, CMD_HGET, CMD_HGETALL, CMD_LRANGE, CMD_SMEMBERS, CMD_ZRANGE
    };

    private static final String[][] TEMPLATES = {
            {MessageKeys.TOOLBOX_REDIS_TPL_GET, CMD_GET, "user:1", "", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_SET_JSON, CMD_SET, "user:1", "", "{\n  \"name\": \"alice\",\n  \"age\": 18\n}"},
            {MessageKeys.TOOLBOX_REDIS_TPL_HGETALL, CMD_HGETALL, "user:100", "", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_LRANGE, CMD_LRANGE, "queue:jobs", "0 20", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_SMEMBERS, CMD_SMEMBERS, "tag:online", "", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_ZRANGE, CMD_ZRANGE, "rank:score", "0 20", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_TTL, CMD_TTL, "session:token", "", ""},
            {MessageKeys.TOOLBOX_REDIS_TPL_DEL, CMD_DEL, "tmp:key", "", ""}
    };

    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");
    private static final Pattern SIMPLE_HOST_PORT_PATTERN = Pattern.compile("^([^:]+):(\\d{1,5})$");

    private static class HistoryEntry {
        final String command;
        final String key;
        final String args;
        final String value;

        HistoryEntry(String command, String key, String args, String value) {
            this.command = command;
            this.key = key;
            this.args = args;
            this.value = value;
        }
    }

    private JComboBox<String> profileCombo;
    private JButton newProfileBtn;
    private JButton saveProfileBtn;
    private JButton saveAsProfileBtn;
    private JButton deleteProfileBtn;
    private JTextField hostField;
    private EasyJSpinner portSpinner;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private EasyJSpinner dbSpinner;
    private JComboBox<AuthMode> authModeCombo;
    private JPanel connectionPanel;
    private JPanel connectionForm;
    private JComponent[] authComponents;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JButton testConnectionBtn;
    private CardLayout btnCardLayout;
    private JPanel btnCard;

    private SearchTextField keySearchField;
    private DefaultListModel<String> keyListModel;
    private DefaultListModel<String> keyFilteredModel;
    private JList<String> keyList;
    private final Map<String, String> keyTypeMap = new HashMap<>();
    private final Map<String, Long> keyTtlMap = new HashMap<>();

    private JComboBox<String> templateCombo;
    private JComboBox<String> commandCombo;
    private JTextField keyField;
    private JTextField argsField;
    private RSyntaxTextArea valueEditor;
    private RSyntaxTextArea resultArea;
    private SearchableTextArea searchableValueArea;
    private SearchableTextArea searchableResultArea;
    private JLabel keyMetaLabel;
    private JLabel respStatusLabel;
    private CompactPrimaryButton executeBtn;
    private RefreshButton refreshKeysBtn;

    private final Deque<HistoryEntry> requestHistory = new ArrayDeque<>();
    private DefaultListModel<HistoryEntry> historyListModel;
    private JList<HistoryEntry> historyList;

    private JSplitPane mainSplit;
    private transient JedisPooled jedis;
    private boolean connected = false;
    private boolean loadingKeys;
    private final RedisConnectionProfileStore connectionProfileStore;
    private final Map<String, RedisConnectionProfile> connectionProfilesByName = new LinkedHashMap<>();
    private boolean loadingConnectionProfiles;

    @RequiredArgsConstructor
    private enum AuthMode {
        NONE(MessageKeys.TOOLBOX_REDIS_AUTH_NONE),
        BASIC(MessageKeys.TOOLBOX_REDIS_AUTH_BASIC);

        private final String messageKey;

        String displayName() {
            return t(messageKey);
        }
    }

    public RedisPanel() {
        this(PluginStorage.noop());
    }

    public RedisPanel(PluginStorage storage) {
        this.connectionProfileStore = new RedisConnectionProfileStore(storage);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        ToolWindowSurfaceStyle.applyCard(this);

        add(buildConnectionPanel(), BorderLayout.NORTH);

        mainSplit = ToolWindowChrome.createHorizontalInnerSplitPane(
                buildLeftPanel(),
                buildMainPanel(),
                240
        );
        mainSplit.setResizeWeight(0.0);
        mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            int loc = (int) e.getNewValue();
            if (loc > 10) mainSplit.putClientProperty("savedDividerLocation", loc);
        });
        add(mainSplit, BorderLayout.CENTER);
        ToolWindowSurfaceStyle.applyPanelTreeCard(this);
        updateConnectedState(false);
        respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_NOT_CONNECTED));
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        connectionPanel = panel;
        ToolWindowSurfaceStyle.applySectionHeader(panel, 3, 6, 3, 6);

        profileCombo = new JComboBox<>();
        profileCombo.setEditable(false);
        compactRedisControl(profileCombo);
        profileCombo.setRenderer(ConnectionToolbarUi.displayRenderer(value -> value == null ? "" : value));
        profileCombo.addActionListener(e -> applySelectedConnectionProfile());

        newProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_REDIS_PROFILE_NEW),
                "icons/plus.svg", e -> createNewConnectionProfile());
        saveProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVE),
                "icons/save.svg", e -> saveCurrentConnectionProfile(true));
        saveProfileBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVE) + " (Ctrl+S)");
        saveAsProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVE_AS),
                "icons/duplicate.svg", e -> saveCurrentConnectionProfileAs());
        deleteProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_REDIS_PROFILE_DELETE),
                "icons/delete.svg", e -> deleteSelectedConnectionProfile());

        hostField = new JTextField("localhost");
        compactRedisControl(hostField);
        hostField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_REDIS_HOST_PLACEHOLDER));
        hostField.addActionListener(e -> doConnect());

        portSpinner = EasyJSpinner.intSpinner(6379, 1, 65535, 1);
        compactRedisControl(portSpinner);

        dbSpinner = EasyJSpinner.intSpinner(0, 0, MAX_DB_INDEX, 1);
        compactRedisControl(dbSpinner);

        usernameField = new JTextField("");
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_REDIS_USER_PLACEHOLDER));
        compactRedisControl(usernameField);
        usernameField.addActionListener(e -> doConnect());

        passwordField = new JPasswordField("");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_REDIS_PASS_PLACEHOLDER));
        compactRedisControl(passwordField);
        passwordField.addActionListener(e -> doConnect());

        authModeCombo = ConnectionToolbarUi.comboBox(AuthMode.values(), AuthMode::displayName);
        compactRedisControl(authModeCombo);
        authModeCombo.addActionListener(e -> setAuthFieldsEnabled(getSelectedAuthMode() == AuthMode.BASIC));

        connectBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_REDIS_CONNECT), true, "icons/connect.svg");
        connectBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_CONNECT_TOOLTIP));
        connectBtn.addActionListener(e -> doConnect());

        disconnectBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_REDIS_DISCONNECT), false, "icons/ws-close.svg");
        disconnectBtn.addActionListener(e -> doDisconnect());

        testConnectionBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION), false, "icons/check.svg");
        testConnectionBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION_TOOLTIP));
        testConnectionBtn.addActionListener(e -> testConnection());

        btnCardLayout = new CardLayout();
        btnCard = new JPanel(btnCardLayout);
        btnCard.setOpaque(false);
        btnCard.add(connectBtn, CONNECT_CARD);
        btnCard.add(disconnectBtn, DISCONNECT_CARD);
        btnCardLayout.show(btnCard, CONNECT_CARD);

        JPanel profileGroup = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gapx 0",
                "[grow,fill]2[" + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]",
                "[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]"
        ));
        profileGroup.setOpaque(false);
        String controlHeight = "wmin 0, h " + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!";
        profileGroup.add(profileCombo, controlHeight);
        profileGroup.add(newProfileBtn);
        profileGroup.add(saveProfileBtn);
        profileGroup.add(saveAsProfileBtn);
        profileGroup.add(deleteProfileBtn);

        JPanel portDbGroup = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gapx 0",
                "[" + PORT_FIELD_MIN_WIDTH + ":pref,grow,fill]8[pref!,right]4[" + DB_FIELD_WIDTH + "!,fill]",
                "[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]"
        ));
        portDbGroup.setOpaque(false);
        portDbGroup.add(portSpinner, controlHeight);
        portDbGroup.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_DB)));
        portDbGroup.add(dbSpinner, controlHeight);

        JPanel actions = new JPanel(new MigLayout("insets 0, novisualpadding, gapx 6", "[][]", "[]"));
        actions.setOpaque(false);
        String buttonHeight = "h " + ConnectionToolbarUi.CONNECTION_BUTTON_HEIGHT + "!";
        actions.add(testConnectionBtn, buttonHeight);
        actions.add(btnCard, buttonHeight);

        // 与 Kafka 一致：两行共用同一网格，各列左右边缘严格对齐；最后一列是操作区，密码框跨列与连接按钮右对齐。
        connectionForm = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gapy 4",
                growFieldColumns(PROFILE_GROUP_MIN_WIDTH, PROFILE_GROUP_PREF_WIDTH) + GROUP_GAP
                        + growFieldColumns(HOST_GROUP_MIN_WIDTH, HOST_GROUP_PREF_WIDTH) + GROUP_GAP
                        + "[pref!,right]4[" + PORT_DB_GROUP_MIN_WIDTH + ":" + PORT_DB_GROUP_PREF_WIDTH + ",fill]"
                        + GROUP_GAP + "[pref!,fill]",
                "[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]4[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]"
        ));
        connectionForm.setOpaque(false);

        // 第 1 行：基础连接
        connectionForm.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_PROFILE)));
        connectionForm.add(profileGroup, controlHeight);
        connectionForm.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_HOST)));
        connectionForm.add(hostField, controlHeight);
        connectionForm.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_PORT)));
        connectionForm.add(portDbGroup, controlHeight);
        connectionForm.add(actions, "right, wrap");

        // 第 2 行：认证
        JLabel userLabel = ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_USER));
        JLabel passLabel = ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_PASS));
        connectionForm.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_REDIS_AUTH)));
        connectionForm.add(authModeCombo, controlHeight);
        connectionForm.add(userLabel);
        connectionForm.add(usernameField, controlHeight);
        connectionForm.add(passLabel);
        connectionForm.add(passwordField, "span 2, growx, " + controlHeight);
        authComponents = new JComponent[]{userLabel, usernameField, passLabel, passwordField};

        JPanel wrapper = new JPanel(new MigLayout("insets 0, novisualpadding, fillx", "[grow,fill]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(connectionForm, "growx, wmin 0");
        panel.add(wrapper, BorderLayout.CENTER);
        ConnectionToolbarUi.lockConnectionPanelHeight(panel, true);
        setAuthFieldsEnabled(false);

        ConnectionToolbarUi.registerSaveShortcut(connectionForm, () -> saveCurrentConnectionProfile(true));
        loadSavedConnectionProfiles(null);
        return panel;
    }

    /**
     * 标签列按本列最长标签自适应；字段列窄窗口可压缩到 min，宽窗口均分多余空间，最多拉伸到 2 倍。
     */
    private static String growFieldColumns(int minWidth, int prefWidth) {
        return "[pref!,right]4[" + minWidth + ":" + prefWidth + ":" + (prefWidth * 2) + ",fill,grow,shrink]";
    }

    /**
     * 无认证时禁用（而非隐藏）用户名与密码：布局保持稳定，用户也能看到切换认证方式后可填写哪些项。
     */
    private void setAuthFieldsEnabled(boolean enabled) {
        if (authModeCombo != null) {
            AuthMode targetMode = enabled ? AuthMode.BASIC : AuthMode.NONE;
            if (authModeCombo.getSelectedItem() != targetMode) {
                authModeCombo.setSelectedItem(targetMode);
            }
        }
        if (authComponents != null) {
            for (JComponent component : authComponents) {
                component.setEnabled(enabled);
            }
        }
    }

    private <T extends JComponent> T compactRedisControl(T component) {
        Dimension preferredSize = component.getPreferredSize();
        int width = Math.max(preferredSize == null ? 0 : preferredSize.width, 1);
        component.setPreferredSize(new Dimension(width, ConnectionToolbarUi.FORM_CONTROL_HEIGHT));
        component.setMinimumSize(new Dimension(1, ConnectionToolbarUi.FORM_CONTROL_HEIGHT));
        return component;
    }

    private AuthMode getSelectedAuthMode() {
        Object selected = authModeCombo == null ? null : authModeCombo.getSelectedItem();
        return selected instanceof AuthMode authMode ? authMode : AuthMode.NONE;
    }

    private JComponent buildLeftPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setMinimumSize(new Dimension(120, 0));
        wrapper.setPreferredSize(new Dimension(240, 0));

        JTabbedPane leftTabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        ToolWindowSurfaceStyle.applyTabbedPaneCard(leftTabs);
        leftTabs.addTab(t(MessageKeys.TOOLBOX_REDIS_KEYS_TAB), buildKeyPanel());
        leftTabs.setToolTipTextAt(0, t(MessageKeys.TOOLBOX_REDIS_KEYS_MANAGEMENT));
        leftTabs.addTab(t(MessageKeys.TOOLBOX_REDIS_HISTORY), buildHistoryPanel());
        wrapper.add(leftTabs, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildKeyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        refreshKeysBtn = new RefreshButton();
        refreshKeysBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_KEYS_REFRESH));
        refreshKeysBtn.addActionListener(e -> loadKeysAsync());
        ToolWindowSidebarHeader titleBar = new ToolWindowSidebarHeader(
                t(MessageKeys.TOOLBOX_REDIS_KEYS_MANAGEMENT), refreshKeysBtn);

        keySearchField = new SearchTextField();
        // 过滤仅做不区分大小写的包含匹配，隐藏不生效的大小写/整词选项
        keySearchField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, null);
        keySearchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_REDIS_KEYS_SEARCH_PLACEHOLDER));
        keySearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        keySearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyKeyFilter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyKeyFilter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyKeyFilter();
            }
        });

        keyListModel = new DefaultListModel<>();
        keyFilteredModel = new DefaultListModel<>();
        keyList = new JList<>(keyFilteredModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() == 0) {
                    paintEmptyText(this, g, keyListEmptyText());
                }
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) {
                    return null;
                }
                return getModel().getElementAt(index);
            }
        };
        ToolTipManager.sharedInstance().registerComponent(keyList);
        keyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        keyList.setCellRenderer(new KeyCellRenderer());
        keyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            List<String> selected = keyList.getSelectedValuesList();
            if (selected.size() == 1) {
                String key = selected.get(0);
                keyField.setText(key);
                loadKeyMetaIfAbsent(key);
            } else {
                keyMetaLabel.setText("");
            }
        });
        keyList.addMouseListener(buildKeyListMouseListener());
        registerKeyListShortcuts();

        JScrollPane listScroll = new JScrollPane(keyList);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(listScroll, keyList);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBar, BorderLayout.NORTH);
        top.add(new ToolWindowSidebarToolbar(null, keySearchField), BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(listScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        ClearButton clearHistBtn = new ClearButton();
        clearHistBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_HISTORY_CLEAR));
        clearHistBtn.addActionListener(e -> {
            requestHistory.clear();
            historyListModel.clear();
        });
        ToolWindowSidebarHeader titleBar = new ToolWindowSidebarHeader(
                t(MessageKeys.TOOLBOX_REDIS_HISTORY), clearHistBtn);

        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() == 0) {
                    paintEmptyText(this, g, t(MessageKeys.TOOLBOX_REDIS_HISTORY_EMPTY));
                }
            }
        };
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer(new HistoryCellRenderer());
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int idx = historyList.locationToIndex(e.getPoint());
                    if (idx >= 0) applyHistory(historyListModel.get(idx));
                }
            }
        });
        JScrollPane scroll = new JScrollPane(historyList);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(scroll, historyList);

        panel.add(titleBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(0, 0));
        panel.add(buildActionBar(), BorderLayout.NORTH);

        JSplitPane split = ToolWindowChrome.createVerticalInnerSplitPane(
                buildValuePanel(),
                buildResultPanel(),
                210
        );
        split.setResizeWeight(0.36);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildActionBar() {
        // 与 Kafka 生产表单一致的单网格：模板/命令标签同列右对齐，加载模板与执行按钮同列右对齐。
        JPanel panel = new JPanel(new MigLayout(
                "insets 4 6 4 6, fillx, novisualpadding, gapy 4",
                "[right]8[pref!,fill]8[]8[grow,fill]8[]8[grow,fill]8[right]",
                "[]"
        ));
        ToolWindowSurfaceStyle.applySectionHeader(panel);

        templateCombo = new EasyComboBox<>(EasyComboBox.WidthMode.FIXED_MAX);
        for (String[] template : TEMPLATES) {
            templateCombo.addItem(template[0]);
        }
        templateCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String key) {
                    lbl.setText(t(key));
                }
                return lbl;
            }
        });
        ConnectionToolbarUi.compactControl(templateCombo);

        JButton loadTplBtn = ConnectionToolbarUi.iconButton(
                t(MessageKeys.TOOLBOX_REDIS_LOAD_TEMPLATE),
                "icons/load.svg", e -> loadTemplate(templateCombo.getSelectedIndex()));

        commandCombo = new EasyComboBox<>(COMMANDS, EasyComboBox.WidthMode.FIXED_MAX);
        ConnectionToolbarUi.compactControl(commandCombo);
        commandCombo.addActionListener(e -> updateArgsPlaceholder());

        keyField = new JTextField();
        keyField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_REDIS_KEY_PLACEHOLDER));
        keyField.addActionListener(e -> executeCommand());

        argsField = new JTextField();
        argsField.addActionListener(e -> executeCommand());

        executeBtn = new CompactPrimaryButton(t(MessageKeys.TOOLBOX_REDIS_EXECUTE_SHORT), "icons/send.svg");
        executeBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_EXECUTE));
        executeBtn.addActionListener(e -> executeCommand());

        panel.add(new JLabel(t(MessageKeys.TOOLBOX_REDIS_TEMPLATE)));
        panel.add(templateCombo, "span 5, growx, wmin 0");
        panel.add(loadTplBtn, "alignx left, wrap");

        panel.add(new JLabel(t(MessageKeys.TOOLBOX_REDIS_COMMAND)));
        panel.add(commandCombo);
        panel.add(new JLabel(t(MessageKeys.TOOLBOX_REDIS_KEY)));
        panel.add(keyField, "growx, wmin 0");
        panel.add(new JLabel(t(MessageKeys.TOOLBOX_REDIS_ARGS)));
        panel.add(argsField, "growx, wmin 0");
        panel.add(executeBtn);

        updateArgsPlaceholder();
        return panel;
    }

    private JPanel buildValuePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel header = new JPanel(new MigLayout("insets 4 8 4 8, fillx", "[]push[]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(header);
        JLabel title = new JLabel(t(MessageKeys.TOOLBOX_REDIS_VALUE_TITLE));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));
        header.add(title);

        FormatButton formatBtn = new FormatButton();
        formatBtn.setToolTipText(t(MessageKeys.TOOLBOX_REDIS_FORMAT_JSON));
        formatBtn.addActionListener(e -> formatValueJson());
        header.add(formatBtn);

        valueEditor = createJsonEditor(true);
        searchableValueArea = new SearchableTextArea(valueEditor);

        panel.add(header, BorderLayout.NORTH);
        panel.add(searchableValueArea, BorderLayout.CENTER);
        registerCtrlEnterShortcut();
        return panel;
    }

    private JPanel buildResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(0, 0));
        // title 固定, keyMetaLabel 可伸缩但有最大宽, respStatusLabel 固定右侧
        JPanel header = new JPanel(new MigLayout("insets 4 8 4 8, fillx", "[]8[grow,fill]push[]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(header);
        JLabel title = new JLabel(t(MessageKeys.TOOLBOX_REDIS_RESPONSE_TITLE));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));
        keyMetaLabel = new JLabel("");
        keyMetaLabel.setForeground(ModernColors.getPrimary());
        // 超长 key 名用省略号截断，不撑开面板
        keyMetaLabel.setMinimumSize(new Dimension(0, 0));
        respStatusLabel = new JLabel("");
        respStatusLabel.setForeground(ModernColors.getTextSecondary());
        header.add(title);
        header.add(keyMetaLabel, "growx, wmax 400");
        header.add(respStatusLabel);

        resultArea = createJsonEditor(false);
        resultArea.setLineWrap(false);
        resultArea.setHighlightCurrentLine(false);
        searchableResultArea = new SearchableTextArea(resultArea, false);
        searchableResultArea.setLineNumbersEnabled(false);

        panel.add(header, BorderLayout.NORTH);
        panel.add(searchableResultArea, BorderLayout.CENTER);
        return panel;
    }

    private RSyntaxTextArea createJsonEditor(boolean editable) {
        RSyntaxTextArea area = new FallbackAwareRSyntaxTextArea(10, 60);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        area.setCodeFoldingEnabled(true);
        area.setAntiAliasingEnabled(true);
        area.setEditable(editable);
        EditorThemeUtil.loadTheme(area);
        return area;
    }

    private void registerCtrlEnterShortcut() {
        InputMap inputMap = valueEditor.getInputMap();
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                "execRedisCmd");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.META_DOWN_MASK),
                "execRedisCmd");
        valueEditor.getActionMap().put("execRedisCmd", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (executeBtn.isEnabled()) {
                    executeBtn.doClick();
                }
            }
        });
    }

    @Override
    public void removeNotify() {
        closeJedisQuietly();
        super.removeNotify();
    }

    private java.awt.event.MouseAdapter buildKeyListMouseListener() {
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 2) {
                    int idx = keyList.locationToIndex(evt.getPoint());
                    if (idx >= 0) {
                        keyList.setSelectedIndex(idx);
                        String selectedKey = keyList.getSelectedValue();
                        if (selectedKey != null) {
                            keyField.setText(selectedKey);
                            executeKeyQuickRead(selectedKey);
                        }
                    }
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                if (evt.isPopupTrigger()) showKeyPopup(evt);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (evt.isPopupTrigger()) showKeyPopup(evt);
            }
        };
    }

    private void showKeyPopup(java.awt.event.MouseEvent evt) {
        int idx = keyList.locationToIndex(evt.getPoint());
        if (idx >= 0 && !keyList.isSelectedIndex(idx)) {
            keyList.setSelectedIndex(idx);
        }
        List<String> selectedKeys = new ArrayList<>(keyList.getSelectedValuesList());
        String selected = keyList.getSelectedValue();

        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        JMenuItem copyNameItem = new JMenuItem(t(MessageKeys.TOOLBOX_REDIS_KEY_COPY));
        copyNameItem.setEnabled(selected != null);
        copyNameItem.addActionListener(e -> {
            if (selected != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected), null);
                NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_KEY_COPIED, selected));
            }
        });
        menu.add(copyNameItem);

        JMenuItem quickReadItem = new JMenuItem(t(MessageKeys.TOOLBOX_REDIS_KEY_QUICK_READ));
        quickReadItem.setEnabled(selected != null && connected);
        quickReadItem.addActionListener(e -> {
            if (selected != null) {
                keyField.setText(selected);
                executeKeyQuickRead(selected);
            }
        });
        menu.add(quickReadItem);

        JMenuItem deleteItem = new JMenuItem(t(MessageKeys.TOOLBOX_REDIS_KEY_DELETE));
        deleteItem.setEnabled(!selectedKeys.isEmpty() && connected);
        deleteItem.addActionListener(e -> deleteKeys(selectedKeys));
        menu.add(deleteItem);

        menu.show(keyList, evt.getX(), evt.getY());
    }

    private void registerKeyListShortcuts() {
        InputMap inputMap = keyList.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = keyList.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "redisQuickRead");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "redisDeleteKeys");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, java.awt.event.InputEvent.META_DOWN_MASK),
                "redisDeleteKeys");
        actionMap.put("redisQuickRead", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String selected = keyList.getSelectedValue();
                if (selected != null && connected) {
                    keyField.setText(selected);
                    executeKeyQuickRead(selected);
                }
            }
        });
        actionMap.put("redisDeleteKeys", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteKeys(new ArrayList<>(keyList.getSelectedValuesList()));
            }
        });
    }

    /**
     * 连接/测试连接进行中时互斥，避免重复点击触发并发连接。
     */
    private void setConnectionBusy(boolean busy) {
        connectBtn.setEnabled(!busy);
        testConnectionBtn.setEnabled(!busy);
    }

    private void setLoadingKeys(boolean loading) {
        loadingKeys = loading;
        refreshKeysBtn.setEnabled(connected && !loading);
        keyList.repaint();
    }

    private void updateConnectedState(boolean isConnected) {
        connected = isConnected;
        btnCardLayout.show(btnCard, isConnected ? DISCONNECT_CARD : CONNECT_CARD);
        refreshKeysBtn.setEnabled(isConnected && !loadingKeys);
        executeBtn.setEnabled(isConnected);
        keyList.repaint();
    }

    String keyListEmptyText() {
        if (loadingKeys) {
            return t(MessageKeys.TOOLBOX_REDIS_STATUS_LOADING_KEYS);
        }
        if (!connected) {
            return t(MessageKeys.TOOLBOX_REDIS_KEYS_EMPTY_NOT_CONNECTED);
        }
        if (keyListModel.isEmpty()) {
            return t(MessageKeys.TOOLBOX_REDIS_KEYS_EMPTY_NONE);
        }
        return t(MessageKeys.TOOLBOX_REDIS_KEYS_EMPTY_NO_MATCH);
    }

    private static void paintEmptyText(JComponent component, Graphics g, String text) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            g2.setColor(ModernColors.getTextHint());
            FontMetrics fm = g2.getFontMetrics();
            int y = 16 + fm.getAscent();
            for (String line : text.split("\n")) {
                String trimmed = line.strip();
                g2.drawString(trimmed, Math.max(8, (component.getWidth() - fm.stringWidth(trimmed)) / 2), y);
                y += fm.getHeight();
            }
        } finally {
            g2.dispose();
        }
    }

    private record ConnectionParams(String host, int port, int db, String user, String pass) {
    }

    /**
     * 读取并校验当前表单的连接参数；地址非法时提示并返回 null。
     */
    private ConnectionParams readConnectionParams() {
        String hostInput = getHostText();
        if (hostInput.isBlank()) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_HOST_REQUIRED));
            return null;
        }
        ParsedHostPort parsed = parseHostAndPort(hostInput, portSpinner.getCommittedIntValue());
        String host = parsed.host();
        if (host.isBlank()) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_HOST_REQUIRED));
            return null;
        }
        int port = parsed.port();
        portSpinner.setValue(port);
        int db = dbSpinner.getCommittedIntValue();
        boolean useAuth = getSelectedAuthMode() == AuthMode.BASIC;
        String user = useAuth ? usernameField.getText().trim() : "";
        String pass = useAuth ? new String(passwordField.getPassword()) : "";
        return new ConnectionParams(host, port, db, user, pass);
    }

    /**
     * 创建客户端并 PING 验证；失败时关闭客户端后抛出原异常。
     */
    private static JedisPooled openVerifiedClient(ConnectionParams params) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .database(params.db())
                .connectionTimeoutMillis(10_000)
                .socketTimeoutMillis(10_000);
        if (!params.user().isBlank()) {
            builder.user(params.user());
        }
        if (!params.pass().isBlank()) {
            builder.password(params.pass());
        }
        JedisPooled client = new JedisPooled(new HostAndPort(params.host(), params.port()), builder.build());
        try {
            client.ping();
            return client;
        } catch (Exception e) {
            try {
                client.close();
            } catch (Exception ignore) {
            }
            throw e;
        }
    }

    /**
     * 仅验证当前表单配置能否连通，不加载 Key、不改变已连接状态。
     */
    private void testConnection() {
        ConnectionParams params = readConnectionParams();
        if (params == null) {
            return;
        }
        setConnectionBusy(true);
        testConnectionBtn.setText(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION_RUNNING));
        final long startNanos = System.nanoTime();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                openVerifiedClient(params).close();
                return null;
            }

            @Override
            protected void done() {
                setConnectionBusy(false);
                testConnectionBtn.setText(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION));
                long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
                try {
                    get();
                    NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION_SUCCESS,
                            params.host(), String.valueOf(params.port()), String.valueOf(params.db()), String.valueOf(costMs)));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_TEST_CONNECTION_FAILED, extractErr(ex)));
                    log.warn("Test redis connection failed", ex);
                }
            }
        }.execute();
    }

    private void doConnect() {
        ConnectionParams params = readConnectionParams();
        if (params == null) {
            return;
        }
        String host = params.host();
        int port = params.port();
        int db = params.db();

        setConnectionBusy(true);
        respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_CONNECTING));

        SwingWorker<JedisPooled, Void> worker = new SwingWorker<>() {
            @Override
            protected JedisPooled doInBackground() {
                return openVerifiedClient(params);
            }

            @Override
            protected void done() {
                setConnectionBusy(false);
                try {
                    JedisPooled newClient = get();
                    closeJedisQuietly();
                    jedis = newClient;
                    rememberHostHistory(host);
                    updateConnectedState(true);
                    respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_CONNECTED_SIMPLE));
                    NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_CONNECT_SUCCESS, host, port, db));
                    loadKeysAsync();
                } catch (Exception ex) {
                    updateConnectedState(false);
                    respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_CONNECT_FAILED));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_CONNECT_FAILED, extractErr(ex)));
                    log.warn("Redis connect failed", ex);
                }
            }
        };
        worker.execute();
    }

    private void doDisconnect() {
        closeJedisQuietly();
        updateConnectedState(false);
        keyListModel.clear();
        keyFilteredModel.clear();
        keyTypeMap.clear();
        keyTtlMap.clear();
        keyMetaLabel.setText("");
        respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_NOT_CONNECTED));
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_DISCONNECT_SUCCESS));
    }

    private void closeJedisQuietly() {
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception ignore) {
            }
            jedis = null;
        }
    }

    private void loadKeysAsync() {
        if (!connected || jedis == null || loadingKeys) return;
        respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_LOADING_KEYS));
        setLoadingKeys(true);

        SwingWorker<KeyLoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected KeyLoadResult doInBackground() {
                KeyLoadResult result = new KeyLoadResult();
                String cursor = ScanParams.SCAN_POINTER_START;
                ScanParams params = new ScanParams().count(300);
                Set<String> dedup = new LinkedHashSet<>();
                do {
                    var scanResult = jedis.scan(cursor, params);
                    cursor = scanResult.getCursor();
                    for (String key : scanResult.getResult()) {
                        dedup.add(key);
                        if (dedup.size() >= MAX_KEY_SCAN) break;
                    }
                    if (dedup.size() >= MAX_KEY_SCAN) break;
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

                result.truncated = dedup.size() >= MAX_KEY_SCAN;
                result.keys.addAll(dedup);
                Collections.sort(result.keys);

                int metaLimit = Math.min(result.keys.size(), 500);
                for (int i = 0; i < metaLimit; i++) {
                    String key = result.keys.get(i);
                    try {
                        result.types.put(key, jedis.type(key));
                        result.ttls.put(key, jedis.ttl(key));
                    } catch (Exception ignore) {
                        // ignore single key metadata failures
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                setLoadingKeys(false);
                try {
                    KeyLoadResult result = get();
                    keyListModel.clear();
                    for (String key : result.keys) {
                        keyListModel.addElement(key);
                    }
                    keyTypeMap.clear();
                    keyTypeMap.putAll(result.types);
                    keyTtlMap.clear();
                    keyTtlMap.putAll(result.ttls);
                    applyKeyFilter();
                    respStatusLabel.setText(result.truncated
                            ? t(MessageKeys.TOOLBOX_REDIS_STATUS_KEYS_TRUNCATED, result.keys.size())
                            : t(MessageKeys.TOOLBOX_REDIS_STATUS_KEYS_LOADED, result.keys.size()));
                    Object saved = mainSplit.getClientProperty("savedDividerLocation");
                    if (saved instanceof Integer loc) {
                        SwingUtilities.invokeLater(() -> mainSplit.setDividerLocation(loc));
                    }
                } catch (Exception ex) {
                    respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_ERROR, "-"));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_EXECUTE_FAILED, extractErr(ex)));
                    log.warn("Load Redis keys failed", ex);
                }
            }
        };
        worker.execute();
    }

    private void applyKeyFilter() {
        String kw = keySearchField == null ? "" : keySearchField.getText().trim().toLowerCase();
        keyFilteredModel.clear();
        for (int i = 0; i < keyListModel.size(); i++) {
            String key = keyListModel.get(i);
            if (kw.isEmpty() || key.toLowerCase().contains(kw)) {
                keyFilteredModel.addElement(key);
            }
        }
        keySearchField.setNoResult(!kw.isEmpty() && keyFilteredModel.isEmpty());
    }

    private void deleteKeys(List<String> keys) {
        if (!connected || jedis == null || keys == null || keys.isEmpty()) return;

        String msg = keys.size() == 1
                ? MessageFormat.format(t(MessageKeys.TOOLBOX_REDIS_KEY_DELETE_CONFIRM), keys.get(0))
                : MessageFormat.format(t(MessageKeys.TOOLBOX_REDIS_KEY_DELETE_BATCH_CONFIRM), keys.size());
        int opt = JOptionPane.showConfirmDialog(this, msg,
                t(MessageKeys.TOOLBOX_REDIS_KEY_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) return;

        SwingWorker<Long, Void> worker = new SwingWorker<>() {
            @Override
            protected Long doInBackground() {
                return jedis.del(keys.toArray(String[]::new));
            }

            @Override
            protected void done() {
                try {
                    long deleted = get();
                    NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_KEY_DELETE_SUCCESS, deleted));
                    loadKeysAsync();
                } catch (Exception ex) {
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_EXECUTE_FAILED, extractErr(ex)));
                }
            }
        };
        worker.execute();
    }

    private void executeCommand() {
        if (!connected || jedis == null) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_NOT_CONNECTED));
            return;
        }

        String command = Objects.toString(commandCombo.getSelectedItem(), "").trim().toUpperCase(Locale.ROOT);
        String key = keyField.getText().trim();
        String args = argsField.getText().trim();
        String value = valueEditor.getText();

        if (commandNeedsKey(command) && key.isBlank()) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_KEY_REQUIRED));
            return;
        }

        final long start = System.currentTimeMillis();
        respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_EXECUTING));
        executeBtn.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                Object result = runRedisCommand(command, key, args, value);
                return renderResult(result);
            }

            @Override
            protected void done() {
                executeBtn.setEnabled(connected);
                long cost = System.currentTimeMillis() - start;
                try {
                    String text = get();
                    resultArea.setText(text == null ? "" : text);
                    resultArea.setCaretPosition(0);
                    respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_OK, cost));
                    addHistory(command, key, args, value);
                    loadKeyMeta(key, true);
                    if (isMutatingCommand(command)) {
                        loadKeysAsync();
                    }
                } catch (Exception ex) {
                    respStatusLabel.setText(t(MessageKeys.TOOLBOX_REDIS_STATUS_ERROR, cost));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_EXECUTE_FAILED, extractErr(ex)));
                }
            }
        };
        worker.execute();
    }

    private Object runRedisCommand(String command, String key, String args, String value) {
        List<String> argList = parseArgs(args);
        return switch (command) {
            case CMD_GET -> jedis.get(key);
            case CMD_SET -> {
                String actualValue = value == null ? "" : value.strip();
                if (actualValue.isBlank()) {
                    actualValue = String.join(" ", argList).strip();
                }
                if (actualValue.isBlank()) {
                    throw new IllegalArgumentException(t(MessageKeys.TOOLBOX_REDIS_ERR_SET_VALUE_REQUIRED));
                }
                yield jedis.set(key, actualValue);
            }
            case CMD_DEL -> jedis.del(key);
            case CMD_EXISTS -> jedis.exists(key);
            case CMD_TYPE -> jedis.type(key);
            case CMD_TTL -> jedis.ttl(key);
            case CMD_HGET -> {
                if (argList.isEmpty()) {
                    throw new IllegalArgumentException(t(MessageKeys.TOOLBOX_REDIS_ERR_ARG_REQUIRED));
                }
                yield jedis.hget(key, argList.get(0));
            }
            case CMD_HGETALL -> jedis.hgetAll(key);
            case CMD_LRANGE -> {
                long start = argList.isEmpty() ? 0L : parseLongOrThrow(argList.get(0), "start");
                long end = argList.size() < 2 ? 99L : parseLongOrThrow(argList.get(1), "end");
                yield jedis.lrange(key, start, end);
            }
            case CMD_SMEMBERS -> jedis.smembers(key);
            case CMD_ZRANGE -> {
                long start = argList.isEmpty() ? 0L : parseLongOrThrow(argList.get(0), "start");
                long end = argList.size() < 2 ? 99L : parseLongOrThrow(argList.get(1), "end");
                yield jedis.zrange(key, start, end);
            }
            default ->
                    throw new IllegalArgumentException(t(MessageKeys.TOOLBOX_REDIS_ERR_UNSUPPORTED_COMMAND, command));
        };
    }

    private long parseLongOrThrow(String raw, String name) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": " + raw, e);
        }
    }

    private boolean commandNeedsKey(String command) {
        return !command.isBlank();
    }

    private boolean isMutatingCommand(String command) {
        return CMD_SET.equals(command) || CMD_DEL.equals(command);
    }

    private List<String> parseArgs(String args) {
        if (args == null || args.isBlank()) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        Matcher matcher = ARG_PATTERN.matcher(args);
        while (matcher.find()) {
            if (matcher.group(1) != null) list.add(matcher.group(1));
            else if (matcher.group(2) != null) list.add(matcher.group(2));
            else if (matcher.group(3) != null) list.add(matcher.group(3));
        }
        return list;
    }

    private String renderResult(Object result) {
        if (result == null) return "(nil)";
        if (result instanceof String s) {
            return JsonUtil.isTypeJSON(s) ? JsonUtil.toJsonPrettyStr(s) : s;
        }
        if (result instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (result instanceof Number || result instanceof Boolean) {
            return String.valueOf(result);
        }
        if (result instanceof Map || result instanceof Collection) {
            return JsonUtil.toJsonPrettyStr(result);
        }
        return JsonUtil.toJsonPrettyStr(result);
    }

    private void loadTemplate(int idx) {
        if (idx < 0 || idx >= TEMPLATES.length) return;
        String[] tpl = TEMPLATES[idx];
        commandCombo.setSelectedItem(tpl[1]);
        keyField.setText(tpl[2]);
        argsField.setText(tpl[3]);
        valueEditor.setText(tpl[4]);
        valueEditor.setCaretPosition(0);
    }

    private void updateArgsPlaceholder() {
        String cmd = Objects.toString(commandCombo.getSelectedItem(), "");
        String placeholder = switch (cmd) {
            case CMD_HGET -> "field";
            case CMD_LRANGE, CMD_ZRANGE -> "start end";
            default -> t(MessageKeys.TOOLBOX_REDIS_ARGS_PLACEHOLDER);
        };
        argsField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    }

    private void formatValueJson() {
        String text = valueEditor.getText();
        if (text == null || text.isBlank()) return;
        try {
            valueEditor.setText(JsonUtil.toJsonPrettyStr(text));
            valueEditor.setCaretPosition(0);
        } catch (Exception e) {
            NotificationCenter.showError(t(MessageKeys.TOOLBOX_REDIS_ERR_INVALID_JSON));
        }
    }

    private void addHistory(String command, String key, String args, String value) {
        HistoryEntry entry = new HistoryEntry(command, key, args, value == null ? "" : value);
        Iterator<HistoryEntry> it = requestHistory.iterator();
        while (it.hasNext()) {
            HistoryEntry old = it.next();
            if (Objects.equals(old.command, entry.command)
                    && Objects.equals(old.key, entry.key)
                    && Objects.equals(old.args, entry.args)
                    && Objects.equals(old.value, entry.value)) {
                it.remove();
                break;
            }
        }
        requestHistory.addFirst(entry);
        while (requestHistory.size() > MAX_HISTORY) {
            requestHistory.removeLast();
        }
        historyListModel.clear();
        for (HistoryEntry h : requestHistory) {
            historyListModel.addElement(h);
        }
    }

    private void applyHistory(HistoryEntry entry) {
        if (entry == null) return;
        commandCombo.setSelectedItem(entry.command);
        keyField.setText(entry.key);
        argsField.setText(entry.args);
        valueEditor.setText(entry.value);
        valueEditor.setCaretPosition(0);
    }

    private void loadKeyMetaIfAbsent(String key) {
        loadKeyMeta(key, false);
    }

    private void loadKeyMeta(String key, boolean forceRefresh) {
        if (key == null || key.isBlank() || !connected || jedis == null) return;

        if (!forceRefresh && keyTypeMap.containsKey(key) && keyTtlMap.containsKey(key)) {
            keyMetaLabel.setText(formatKeyMeta(keyTypeMap.get(key), keyTtlMap.get(key)));
            return;
        }

        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Object> doInBackground() {
                Map<String, Object> map = new HashMap<>();
                map.put("type", jedis.type(key));
                map.put("ttl", jedis.ttl(key));
                return map;
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> map = get();
                    String type = Objects.toString(map.get("type"), "-");
                    long ttl = ((Number) map.get("ttl")).longValue();
                    if ("none".equals(type)) {
                        // key 已不存在，清除过期缓存
                        keyTypeMap.remove(key);
                        keyTtlMap.remove(key);
                        keyMetaLabel.setText("");
                    } else {
                        keyTypeMap.put(key, type);
                        keyTtlMap.put(key, ttl);
                        keyMetaLabel.setText(formatKeyMeta(type, ttl));
                    }
                    keyList.repaint();
                } catch (Exception ignore) {
                    // ignore metadata failures
                }
            }
        };
        worker.execute();
    }

    private void executeKeyQuickRead(String key) {
        if (key == null || key.isBlank()) return;
        String cachedType = keyTypeMap.get(key);
        if (cachedType != null) {
            applyQuickReadCommand(cachedType);
            return;
        }
        if (!connected || jedis == null) {
            applyQuickReadCommand("string");
            return;
        }
        // 异步获取 type，避免在 EDT 上阻塞
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return jedis.type(key);
                } catch (Exception ignore) {
                    return "string";
                }
            }

            @Override
            protected void done() {
                try {
                    String resolvedType = get();
                    keyTypeMap.put(key, resolvedType);
                    applyQuickReadCommand(resolvedType);
                } catch (Exception ignore) {
                    applyQuickReadCommand("string");
                }
            }
        }.execute();
    }

    private void applyQuickReadCommand(String type) {
        switch (type) {
            case "hash" -> {
                commandCombo.setSelectedItem(CMD_HGETALL);
                argsField.setText("");
            }
            case "list" -> {
                commandCombo.setSelectedItem(CMD_LRANGE);
                argsField.setText("0 50");
            }
            case "set" -> {
                commandCombo.setSelectedItem(CMD_SMEMBERS);
                argsField.setText("");
            }
            case "zset" -> {
                commandCombo.setSelectedItem(CMD_ZRANGE);
                argsField.setText("0 50");
            }
            default -> {
                commandCombo.setSelectedItem(CMD_GET);
                argsField.setText("");
            }
        }
        executeCommand();
    }

    private String formatKeyMeta(String type, Long ttl) {
        return "[" + type + "]  TTL: " + ttlDisplay(ttl);
    }

    private String ttlDisplay(Long ttl) {
        if (ttl == null) return "-";
        if (ttl == -1L) return t(MessageKeys.TOOLBOX_REDIS_TTL_PERMANENT);
        if (ttl == -2L) return t(MessageKeys.TOOLBOX_REDIS_TTL_EXPIRED);
        long s = ttl;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }

    private String getHostText() {
        return hostField.getText().trim();
    }

    private void rememberHostHistory(String host) {
        if (host == null || host.isBlank()) return;
        hostField.setText(host.trim());
    }

    private void loadSavedConnectionProfiles(String preferredProfileId) {
        loadingConnectionProfiles = true;
        connectionProfilesByName.clear();
        DefaultComboBoxModel<String> profileModel = new DefaultComboBoxModel<>();
        List<RedisConnectionProfile> profiles = connectionProfileStore.loadProfiles();
        for (RedisConnectionProfile profile : profiles) {
            connectionProfilesByName.put(profile.getName(), profile);
            profileModel.addElement(profile.getName());
        }
        profileCombo.setModel(profileModel);
        profileCombo.setEditable(false);
        loadingConnectionProfiles = false;

        RedisConnectionProfile selectedProfile = selectProfile(profiles, preferredProfileId);
        updateDeleteProfileButton(selectedProfile);
        if (selectedProfile != null) {
            profileCombo.setSelectedItem(selectedProfile.getName());
            applyConnectionProfile(selectedProfile);
        } else {
            hostField.setText("localhost");
        }
    }

    private RedisConnectionProfile selectProfile(List<RedisConnectionProfile> profiles, String preferredProfileId) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        String preferredId = defaultString(preferredProfileId).trim();
        if (!preferredId.isBlank()) {
            for (RedisConnectionProfile profile : profiles) {
                if (preferredId.equals(profile.getId())) {
                    return profile;
                }
            }
        }
        return connectionProfileStore.loadActiveProfile().orElse(profiles.get(0));
    }

    private void applySelectedConnectionProfile() {
        if (loadingConnectionProfiles) {
            return;
        }
        RedisConnectionProfile profile = connectionProfilesByName.get(getProfileNameText());
        if (profile != null) {
            applyConnectionProfile(profile);
        }
        updateDeleteProfileButton(profile);
    }

    private void applyConnectionProfile(RedisConnectionProfile profile) {
        hostField.setText(defaultString(profile.getHost()).isBlank() ? "localhost" : profile.getHost().trim());
        portSpinner.setValue(profile.getPort());
        dbSpinner.setValue(profile.getDatabase());
        usernameField.setText(defaultString(profile.getUsername()));
        passwordField.setText(defaultString(profile.getPassword()));
        setAuthFieldsEnabled(hasAuthOptions(profile));
    }

    private boolean hasAuthOptions(RedisConnectionProfile profile) {
        return !defaultString(profile.getUsername()).isBlank()
                || !defaultString(profile.getPassword()).isBlank();
    }

    private void createNewConnectionProfile() {
        RedisConnectionProfile defaultProfile = RedisConnectionProfileStore.defaultProfile();
        Optional<String> profileName = promptNewProfileName(uniqueProfileName(t(MessageKeys.TOOLBOX_REDIS_PROFILE_NEW_DEFAULT)));
        if (profileName.isEmpty()) {
            return;
        }
        RedisConnectionProfile newProfile = RedisConnectionProfile.builder()
                .id("redis-" + UUID.randomUUID())
                .name(profileName.get())
                .host(defaultProfile.getHost())
                .port(defaultProfile.getPort())
                .database(defaultProfile.getDatabase())
                .username(defaultString(defaultProfile.getUsername()))
                .password(defaultString(defaultProfile.getPassword()))
                .hostHistory(defaultProfile.getHostHistory())
                .build();
        connectionProfileStore.upsertProfile(newProfile);
        loadSavedConnectionProfiles(newProfile.getId());
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVED, newProfile.getName()));
        hostField.requestFocusInWindow();
    }

    private void saveCurrentConnectionProfile(boolean notify) {
        String hostInput = getHostText();
        ParsedHostPort parsed = parseHostAndPort(hostInput, portSpinner.getCommittedIntValue());
        String host = parsed.host();
        if (host.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_REDIS_ERR_HOST_REQUIRED));
            return;
        }
        int port = parsed.port();
        portSpinner.setValue(port);
        saveConnectionProfile(
                host,
                port,
                dbSpinner.getCommittedIntValue(),
                getSelectedAuthMode() == AuthMode.BASIC ? usernameField.getText().trim() : "",
                getSelectedAuthMode() == AuthMode.BASIC ? new String(passwordField.getPassword()) : "",
                notify
        );
    }

    private void saveCurrentConnectionProfileAs() {
        String hostInput = getHostText();
        ParsedHostPort parsed = parseHostAndPort(hostInput, portSpinner.getCommittedIntValue());
        String host = parsed.host();
        if (host.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_REDIS_ERR_HOST_REQUIRED));
            return;
        }
        int port = parsed.port();
        int database = dbSpinner.getCommittedIntValue();
        String suggestedName = uniqueProfileName(connectionProfileNameSuggestion(host, port, database));
        Optional<String> profileName = promptNewProfileName(suggestedName);
        if (profileName.isEmpty()) {
            return;
        }
        portSpinner.setValue(port);
        saveConnectionProfile(
                host,
                port,
                database,
                getSelectedAuthMode() == AuthMode.BASIC ? usernameField.getText().trim() : "",
                getSelectedAuthMode() == AuthMode.BASIC ? new String(passwordField.getPassword()) : "",
                profileName.get(),
                true
        );
    }

    private void saveConnectionProfile(String host, int port, int database, String username, String password, boolean notify) {
        saveConnectionProfile(host, port, database, username, password, defaultProfileName(host, port, database), notify);
    }

    private void saveConnectionProfile(String host, int port, int database, String username, String password,
                                       String profileName, boolean notify) {
        RedisConnectionProfile existingProfile = connectionProfilesByName.get(profileName);
        RedisConnectionProfile savedProfile = RedisConnectionProfile.builder()
                .id(existingProfile == null ? "redis-" + UUID.randomUUID() : existingProfile.getId())
                .name(profileName)
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                .hostHistory(currentHostHistoryWith(host))
                .build();
        connectionProfileStore.upsertProfile(savedProfile);
        loadSavedConnectionProfiles(savedProfile.getId());
        if (notify) {
            NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVED, savedProfile.getName()));
        }
    }

    private void deleteSelectedConnectionProfile() {
        RedisConnectionProfile profile = connectionProfilesByName.get(getProfileNameText());
        if (profile == null) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_REDIS_PROFILE_NOT_SELECTED));
            return;
        }
        if (isDefaultProfile(profile)) {
            updateDeleteProfileButton(profile);
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_REDIS_PROFILE_DEFAULT_NOT_DELETABLE));
            return;
        }
        int option = JOptionPane.showConfirmDialog(
                this,
                t(MessageKeys.TOOLBOX_REDIS_PROFILE_DELETE_CONFIRM, profile.getName()),
                t(MessageKeys.TOOLBOX_REDIS_PROFILE_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        connectionProfileStore.deleteProfile(profile.getId());
        loadSavedConnectionProfiles(null);
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_REDIS_PROFILE_DELETED, profile.getName()));
    }

    private String getProfileNameText() {
        Object editor = profileCombo.getEditor().getEditorComponent();
        if (editor instanceof JTextField textField) {
            return textField.getText().trim();
        }
        Object item = profileCombo.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private Optional<String> promptNewProfileName(String initialName) {
        Optional<String> input = TextInputDialog.showRequiredName(
                this,
                t(MessageKeys.TOOLBOX_REDIS_PROFILE_SAVE_AS_TITLE),
                initialName,
                t(MessageKeys.TOOLBOX_REDIS_PROFILE_NAME_REQUIRED)
        );
        if (input.isEmpty()) {
            return Optional.empty();
        }
        String profileName = input.get();
        if (connectionProfilesByName.containsKey(profileName)) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_REDIS_PROFILE_NAME_EXISTS, profileName));
            return Optional.empty();
        }
        return Optional.of(profileName);
    }

    private String defaultProfileName(String host, int port, int database) {
        String selectedName = getProfileNameText();
        return selectedName.isBlank() ? host + ":" + port + "/db" + database : selectedName;
    }

    private String connectionProfileNameSuggestion(String host, int port, int database) {
        return host + ":" + port + "/db" + database;
    }

    private String uniqueProfileName(String baseName) {
        String base = defaultString(baseName).trim();
        if (base.isBlank()) {
            base = t(MessageKeys.TOOLBOX_REDIS_PROFILE_NEW_DEFAULT);
        }
        String candidate = base;
        int suffix = 2;
        while (connectionProfilesByName.containsKey(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private void updateDeleteProfileButton(RedisConnectionProfile profile) {
        deleteProfileBtn.setEnabled(profile != null && !isDefaultProfile(profile));
    }

    private static boolean isDefaultProfile(RedisConnectionProfile profile) {
        return profile != null && RedisConnectionProfileStore.DEFAULT_PROFILE_ID.equals(profile.getId());
    }

    private List<String> currentHostHistoryWith(String activeHost) {
        RedisConnectionProfile profile = connectionProfilesByName.get(getProfileNameText());
        List<String> existingHistory = profile == null ? List.of() : profile.getHostHistory();
        return RedisConnectionProfileStore.normalizeHostHistory(existingHistory, activeHost);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private ParsedHostPort parseHostAndPort(String rawHost, int fallbackPort) {
        String value = rawHost == null ? "" : rawHost.trim();
        if (value.isBlank()) {
            return new ParsedHostPort("", fallbackPort);
        }

        String noScheme = value.replaceFirst("^redis://", "")
                .replaceFirst("^rediss://", "")
                .replaceFirst("^https?://", "");
        int slashIdx = noScheme.indexOf('/');
        if (slashIdx >= 0) {
            noScheme = noScheme.substring(0, slashIdx);
        }

        Matcher matcher = SIMPLE_HOST_PORT_PATTERN.matcher(noScheme);
        if (matcher.matches()) {
            try {
                int port = Integer.parseInt(matcher.group(2));
                if (port >= 1 && port <= 65535) {
                    return new ParsedHostPort(matcher.group(1), port);
                }
            } catch (NumberFormatException ignore) {
            }
        }

        return new ParsedHostPort(noScheme, fallbackPort);
    }

    private record ParsedHostPort(String host, int port) {
    }

    private String extractErr(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.toString() : root.getMessage();
    }

    private static class KeyLoadResult {
        final List<String> keys = new ArrayList<>();
        final Map<String, String> types = new HashMap<>();
        final Map<String, Long> ttls = new HashMap<>();
        boolean truncated;
    }

    private class KeyCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String key) {
                Long ttl = keyTtlMap.get(key);
                if (ttl != null) {
                    String metaColor = ModernColors.toHtmlColor(isSelected
                            ? lbl.getForeground()
                            : ModernColors.getTextHint());
                    lbl.setText("<html>" + escapeHtml(key) + "&nbsp;<font color='" + metaColor + "'>TTL: " + ttlDisplay(ttl) + "</font></html>");
                } else {
                    lbl.setText(key);
                }
            }
            return lbl;
        }
    }

    private static class HistoryCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HistoryEntry h) {
                String key = h.key == null ? "" : h.key;
                if (key.length() > 36) key = key.substring(0, 35) + "…";
                String commandColor = ModernColors.toHtmlColor(ModernColors.getWarning());
                lbl.setText("<html><b><font color='" + commandColor + "'>" + h.command + "</font></b> " + escapeHtml(key) + "</html>");
                lbl.setToolTipText(h.command + " " + h.key + " " + h.args);
            }
            return lbl;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
