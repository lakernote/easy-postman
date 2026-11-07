package com.laker.postman.plugin.kafka;

import com.laker.postman.common.component.notification.NotificationCenter;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.plugin.api.PluginStorage;
import com.laker.postman.plugin.kafka.connection.ui.KafkaConnectionPanel;
import com.laker.postman.plugin.kafka.consumer.KafkaConsumeStartMode;
import com.laker.postman.plugin.kafka.consumer.KafkaConsumedMessage;
import com.laker.postman.plugin.kafka.consumer.ui.KafkaConsumerPanel;
import com.laker.postman.plugin.kafka.producer.ui.KafkaProducerPanel;
import com.laker.postman.plugin.kafka.shared.KafkaPanelSupport;
import com.laker.postman.plugin.kafka.ui.KafkaTopicItem;
import com.laker.postman.plugin.kafka.ui.KafkaTopicPanel;
import com.laker.postman.common.component.ToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;

/**
 * Kafka 工具面板：Topic 浏览 + 发送消息 + 实时消费。
 */
@Slf4j
public class KafkaPanel extends JPanel {

    private static final String CARD_CONNECT = "connect";
    private static final String CARD_DISCONNECT = "disconnect";
    private static final String TIMEOUT_MS = String.valueOf(KafkaPanelSupport.CLIENT_TIMEOUT_MS);
    private static final long PRODUCER_DELIVERY_TIMEOUT_MS = 15_000L;
    private static final long ADMIN_FUTURE_WAIT_MS = KafkaPanelSupport.futureWaitMs(KafkaPanelSupport.CLIENT_TIMEOUT_MS);
    private static final String LEGACY_USER_SETTING_KAFKA_BOOTSTRAP = "toolbox.kafka.bootstrapServers";

    private KafkaConnectionPanel connectionPanel;

    private KafkaTopicPanel topicPanel;
    private KafkaProducerPanel producerPanel;
    private KafkaConsumerPanel consumerPanel;
    private final Map<String, Integer> topicPartitionCountMap = new HashMap<>();

    private final List<KafkaConsumedMessage> consumedMessages = new ArrayList<>();
    private final AtomicReference<SwingWorker<Void, List<KafkaConsumedMessage>>> consumeWorkerRef = new AtomicReference<>();
    private final AtomicReference<KafkaConsumer<String, String>> runningConsumerRef = new AtomicReference<>();
    /**
     * 缓存可复用的 Producer，避免每次发送都重建连接
     */
    private final AtomicReference<KafkaProducer<String, String>> cachedProducerRef = new AtomicReference<>();
    /**
     * 缓存 Producer 对应的配置签名，配置变更时重建
     */
    private volatile String cachedProducerConfigSignature;
    private final KafkaConnectionProfileStore connectionProfileStore;
    private final Map<String, KafkaConnectionProfile> connectionProfilesByName = new LinkedHashMap<>();
    private boolean loadingConnectionProfiles;

    public KafkaPanel() {
        this(PluginStorage.noop());
    }

    public KafkaPanel(PluginStorage storage) {
        this.connectionProfileStore = new KafkaConnectionProfileStore(storage);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        ToolWindowSurfaceStyle.applyCard(this);

        connectionPanel = new KafkaConnectionPanel(this::loadTopics, this::doDisconnect);
        connectionPanel.profileCombo.addActionListener(e -> applySelectedConnectionProfile());
        connectionPanel.newProfileBtn.addActionListener(e -> createNewConnectionProfile());
        connectionPanel.saveProfileBtn.addActionListener(e -> saveCurrentConnectionProfile(true));
        connectionPanel.saveAsProfileBtn.addActionListener(e -> saveCurrentConnectionProfileAs());
        connectionPanel.deleteProfileBtn.addActionListener(e -> deleteSelectedConnectionProfile());
        connectionPanel.securityProtocolCombo.addActionListener(e -> updateSecurityFieldsEnabledState());
        registerConnectionProfileShortcuts(connectionPanel);
        loadSavedConnectionProfiles(null);
        producerPanel = new KafkaProducerPanel(this::sendMessage);
        consumerPanel = new KafkaConsumerPanel(this::startConsuming, this::stopConsuming, this::clearConsumedMessages, this::updateDetailBySelection);
        consumerPanel.messageTablePanel.setViewDataChangedListener(this::updateDetailBySelection);
        consumerPanel.autoOffsetCombo.addActionListener(e -> updateConsumeStartValueEnabledState());
        topicPanel = new KafkaTopicPanel(this::loadTopics, this::applyTopicSelection);
        consumerPanel.topicField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                syncPartitionSelector();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                syncPartitionSelector();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                syncPartitionSelector();
            }
        });
        updateSecurityFieldsEnabledState();
        updateConsumeStartValueEnabledState();
        syncPartitionSelector();
        updateEditorFont();
        topicPanel.topicSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyTopicFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyTopicFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyTopicFilter();
            }
        });

        add(connectionPanel, BorderLayout.NORTH);

        JSplitPane mainSplit = ToolWindowChrome.createHorizontalInnerSplitPane(
                buildTopicSidePanel(),
                buildWorkPanel(),
                240
        );
        mainSplit.setDividerLocation(240);
        mainSplit.setResizeWeight(0.23);
        add(mainSplit, BorderLayout.CENTER);
        ToolWindowSurfaceStyle.applyPanelTreeCard(this);
    }

    private JComponent buildTopicSidePanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setMinimumSize(new Dimension(120, 0));
        wrapper.setPreferredSize(new Dimension(240, 0));

        JTabbedPane sideTabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        ToolWindowSurfaceStyle.applyTabbedPaneCard(sideTabs);
        sideTabs.addTab(t(MessageKeys.TOOLBOX_KAFKA_TOPIC_TAB), topicPanel);
        sideTabs.setToolTipTextAt(0, t(MessageKeys.TOOLBOX_KAFKA_TOPIC_MANAGEMENT));
        wrapper.add(sideTabs, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildWorkPanel() {
        JTabbedPane workTabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        ToolWindowSurfaceStyle.applyTabbedPaneCard(workTabs);
        workTabs.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, +1));
        workTabs.addTab(
                t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_TAB),
                IconUtil.createThemed("icons/send.svg", 16, 16),
                producerPanel,
                t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_TITLE));
        workTabs.addTab(
                t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_TAB),
                IconUtil.createThemed("icons/start.svg", 16, 16),
                consumerPanel,
                t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_TITLE));
        return workTabs;
    }

    private void applyTopicSelection(String selected) {
        if (selected == null || selected.isBlank()) {
            return;
        }
        producerPanel.topicField.setText(selected);
        consumerPanel.topicField.setText(selected);
        syncPartitionSelector();
    }


    private void updateSecurityFieldsEnabledState() {
        boolean saslEnabled = selectedSecurityProtocol().startsWith("SASL");
        connectionPanel.saslMechanismCombo.setEnabled(saslEnabled);
        connectionPanel.usernameField.setEnabled(saslEnabled);
        connectionPanel.passwordField.setEnabled(saslEnabled);
        connectionPanel.setOptionsVisible(true);
    }

    private void registerConnectionProfileShortcuts(JComponent component) {
        KeyStroke saveKey = KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_S,
                menuShortcutMask()
        );
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(saveKey, "saveKafkaConnectionProfile");
        component.getActionMap().put("saveKafkaConnectionProfile", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveCurrentConnectionProfile(true);
            }
        });
    }

    private static int menuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException e) {
            return java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
    }

    private void loadSavedConnectionProfiles(String preferredProfileId) {
        loadingConnectionProfiles = true;
        connectionProfilesByName.clear();
        DefaultComboBoxModel<String> profileModel = new DefaultComboBoxModel<>();
        List<KafkaConnectionProfile> profiles = connectionProfileStore.loadProfiles();
        for (KafkaConnectionProfile profile : profiles) {
            connectionProfilesByName.put(profile.getName(), profile);
            profileModel.addElement(profile.getName());
        }
        connectionPanel.profileCombo.setModel(profileModel);
        connectionPanel.profileCombo.setEditable(false);
        loadingConnectionProfiles = false;

        KafkaConnectionProfile selectedProfile = selectProfile(profiles, preferredProfileId);
        updateDeleteProfileButton(selectedProfile);
        if (selectedProfile != null) {
            connectionPanel.profileCombo.setSelectedItem(selectedProfile.getName());
            applyConnectionProfile(selectedProfile);
            return;
        }
        String legacyBootstrap = UserPreferencesStore.getString(LEGACY_USER_SETTING_KAFKA_BOOTSTRAP);
        if (legacyBootstrap != null && !legacyBootstrap.isBlank()) {
            connectionPanel.bootstrapField.setText(legacyBootstrap);
        }
        connectionPanel.setOptionsVisible(true);
    }

    private KafkaConnectionProfile selectProfile(List<KafkaConnectionProfile> profiles, String preferredProfileId) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        String preferredId = defaultString(preferredProfileId).trim();
        if (!preferredId.isBlank()) {
            for (KafkaConnectionProfile profile : profiles) {
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
        KafkaConnectionProfile profile = connectionProfilesByName.get(getProfileNameText());
        if (profile != null) {
            applyConnectionProfile(profile);
        }
        updateDeleteProfileButton(profile);
    }

    private void applyConnectionProfile(KafkaConnectionProfile profile) {
        connectionPanel.bootstrapField.setText(defaultIfBlank(profile.getBootstrapServers(), "localhost:9092"));
        connectionPanel.clientIdField.setText(defaultIfBlank(profile.getClientId(), "easy-postman-toolbox"));
        selectComboItem(connectionPanel.securityProtocolCombo, defaultIfBlank(profile.getSecurityProtocol(), "PLAINTEXT"));
        selectComboItem(connectionPanel.saslMechanismCombo, defaultIfBlank(profile.getSaslMechanism(), "PLAIN"));
        connectionPanel.usernameField.setText(defaultString(profile.getUsername()));
        connectionPanel.passwordField.setText(defaultString(profile.getPassword()));
        updateSecurityFieldsEnabledState();
        connectionPanel.setOptionsVisible(true);
    }

    private void createNewConnectionProfile() {
        KafkaConnectionProfile defaultProfile = KafkaConnectionProfileStore.defaultProfile();
        Optional<String> profileName = promptNewProfileName(uniqueProfileName(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NEW_DEFAULT)));
        if (profileName.isEmpty()) {
            return;
        }
        KafkaConnectionProfile newProfile = KafkaConnectionProfile.builder()
                .id("kafka-" + UUID.randomUUID())
                .name(profileName.get())
                .bootstrapServers(defaultProfile.getBootstrapServers())
                .clientId(defaultProfile.getClientId())
                .securityProtocol(defaultProfile.getSecurityProtocol())
                .saslMechanism(defaultProfile.getSaslMechanism())
                .username(defaultString(defaultProfile.getUsername()))
                .password(defaultString(defaultProfile.getPassword()))
                .build();
        connectionProfileStore.upsertProfile(newProfile);
        loadSavedConnectionProfiles(newProfile.getId());
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVED, newProfile.getName()));
        connectionPanel.bootstrapField.requestFocusInWindow();
    }

    private KafkaConnectionProfile currentConnectionProfile() {
        String bootstrapServers = connectionPanel.bootstrapField.getText().trim();
        String profileName = defaultProfileName(bootstrapServers);
        KafkaConnectionProfile existingProfile = connectionProfilesByName.get(profileName);
        String profileId = existingProfile == null ? "kafka-" + UUID.randomUUID() : existingProfile.getId();
        return currentConnectionProfileWithName(profileName, profileId);
    }

    private void saveCurrentConnectionProfile(boolean notify) {
        String bootstrapServers = connectionPanel.bootstrapField.getText().trim();
        if (bootstrapServers.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_ERR_HOST_REQUIRED));
            return;
        }
        KafkaConnectionProfile profile = currentConnectionProfile();
        connectionProfileStore.upsertProfile(profile);
        loadSavedConnectionProfiles(profile.getId());
        if (notify) {
            NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVED, profile.getName()));
        }
    }

    private void saveCurrentConnectionProfileAs() {
        String bootstrapServers = connectionPanel.bootstrapField.getText().trim();
        if (bootstrapServers.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_ERR_HOST_REQUIRED));
            return;
        }
        Optional<String> profileName = promptNewProfileName(uniqueProfileName(connectionProfileNameSuggestion(bootstrapServers)));
        if (profileName.isEmpty()) {
            return;
        }
        KafkaConnectionProfile profile = currentConnectionProfileWithName(profileName.get(), "kafka-" + UUID.randomUUID());
        connectionProfileStore.upsertProfile(profile);
        loadSavedConnectionProfiles(profile.getId());
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVED, profile.getName()));
    }

    private void deleteSelectedConnectionProfile() {
        KafkaConnectionProfile profile = connectionProfilesByName.get(getProfileNameText());
        if (profile == null) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NOT_SELECTED));
            return;
        }
        if (isDefaultProfile(profile)) {
            updateDeleteProfileButton(profile);
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_DEFAULT_NOT_DELETABLE));
            return;
        }
        int option = JOptionPane.showConfirmDialog(
                this,
                t(MessageKeys.TOOLBOX_KAFKA_PROFILE_DELETE_CONFIRM, profile.getName()),
                t(MessageKeys.TOOLBOX_KAFKA_PROFILE_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        connectionProfileStore.deleteProfile(profile.getId());
        loadSavedConnectionProfiles(null);
        NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_DELETED, profile.getName()));
    }

    private String getProfileNameText() {
        Object editor = connectionPanel.profileCombo.getEditor().getEditorComponent();
        if (editor instanceof JTextField textField) {
            return textField.getText().trim();
        }
        Object item = connectionPanel.profileCombo.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private Optional<String> promptNewProfileName(String initialName) {
        Optional<String> input = TextInputDialog.showRequiredName(
                this,
                t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVE_AS_TITLE),
                initialName,
                t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NAME_REQUIRED)
        );
        if (input.isEmpty()) {
            return Optional.empty();
        }
        String profileName = input.get();
        if (connectionProfilesByName.containsKey(profileName)) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NAME_EXISTS, profileName));
            return Optional.empty();
        }
        return Optional.of(profileName);
    }

    private String defaultProfileName(String bootstrapServers) {
        String selectedName = getProfileNameText();
        if (!selectedName.isBlank()) {
            return selectedName;
        }
        return defaultIfBlank(bootstrapServers, "localhost:9092");
    }

    private String connectionProfileNameSuggestion(String bootstrapServers) {
        return defaultIfBlank(bootstrapServers, "localhost:9092");
    }

    private KafkaConnectionProfile currentConnectionProfileWithName(String profileName, String profileId) {
        return KafkaConnectionProfile.builder()
                .id(profileId)
                .name(profileName)
                .bootstrapServers(connectionPanel.bootstrapField.getText().trim())
                .clientId(connectionPanel.clientIdField.getText().trim())
                .securityProtocol(selectedSecurityProtocol())
                .saslMechanism(selectedSaslMechanism())
                .username(connectionPanel.usernameField.getText().trim())
                .password(new String(connectionPanel.passwordField.getPassword()))
                .build();
    }

    private String uniqueProfileName(String baseName) {
        String base = defaultString(baseName).trim();
        if (base.isBlank()) {
            base = t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NEW_DEFAULT);
        }
        String candidate = base;
        int suffix = 2;
        while (connectionProfilesByName.containsKey(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private void updateDeleteProfileButton(KafkaConnectionProfile profile) {
        connectionPanel.deleteProfileBtn.setEnabled(profile != null && !isDefaultProfile(profile));
    }

    private static boolean isDefaultProfile(KafkaConnectionProfile profile) {
        return profile != null && KafkaConnectionProfileStore.DEFAULT_PROFILE_ID.equals(profile.getId());
    }

    private void updateConsumeStartValueEnabledState() {
        KafkaConsumeStartMode consumeStartMode = selectedConsumeStartMode();
        boolean enabled = consumeStartMode.requiresStartValue();
        consumerPanel.setConsumeStartValueVisible(enabled);
        consumerPanel.consumeStartValueField.setEnabled(enabled);
        if (consumeStartMode == KafkaConsumeStartMode.TIMESTAMP) {
            consumerPanel.consumeStartValueField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                    t(MessageKeys.TOOLBOX_KAFKA_OFFSET_VALUE_PLACEHOLDER) + " (e.g. 2024-01-01 00:00:00 or ms)");
        } else if (consumeStartMode == KafkaConsumeStartMode.OFFSET) {
            consumerPanel.consumeStartValueField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                    t(MessageKeys.TOOLBOX_KAFKA_OFFSET_VALUE_PLACEHOLDER) + " (e.g. 100)");
        } else {
            consumerPanel.consumeStartValueField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                    t(MessageKeys.TOOLBOX_KAFKA_OFFSET_VALUE_PLACEHOLDER));
        }
        consumerPanel.consumeStartValueField.repaint();
    }

    private void loadTopics() {
        String bootstrap = connectionPanel.bootstrapField.getText().trim();
        if (bootstrap.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_ERR_HOST_REQUIRED));
            return;
        }
        // 在 EDT 预先读取所有 UI 字段（含 passwordField），避免非 EDT 线程读取 Swing 组件
        final Properties baseProps;
        try {
            baseProps = buildCommonClientProperties();
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }

        connectionPanel.connectBtn.setEnabled(false);
        setConnectionStatus(ModernColors.getInfo(), t(MessageKeys.TOOLBOX_KAFKA_STATUS_LOADING_TOPICS));

        SwingWorker<List<KafkaTopicItem>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<KafkaTopicItem> doInBackground() throws Exception {
                return KafkaClassLoaderSupport.withPluginContextClassLoader(() -> {
                    Properties props = new Properties();
                    props.putAll(baseProps);
                    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
                    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
                    try (AdminClient adminClient = AdminClient.create(props)) {
                        ListTopicsOptions options = new ListTopicsOptions().listInternal(false).timeoutMs(Integer.parseInt(TIMEOUT_MS));
                        List<String> topics = new ArrayList<>(adminClient.listTopics(options).names().get(ADMIN_FUTURE_WAIT_MS, TimeUnit.MILLISECONDS));
                        Collections.sort(topics);
                        Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions =
                                adminClient.describeTopics(topics).allTopicNames().get(ADMIN_FUTURE_WAIT_MS, TimeUnit.MILLISECONDS);
                        List<KafkaTopicItem> topicItems = new ArrayList<>(topics.size());
                        for (String topic : topics) {
                            int partitionCount = descriptions.get(topic).partitions().size();
                            topicItems.add(new KafkaTopicItem(topic, partitionCount));
                        }
                        return topicItems;
                    }
                });
            }

            @Override
            protected void done() {
                connectionPanel.connectBtn.setEnabled(true);
                try {
                    List<KafkaTopicItem> topics = get();
                    topicPartitionCountMap.clear();
                    topicPanel.topicListModel.clear();
                    for (KafkaTopicItem topic : topics) {
                        topicPartitionCountMap.put(topic.name(), topic.partitionCount());
                        topicPanel.topicListModel.addElement(topic);
                    }
                    applyTopicFilter();
                    if (!topics.isEmpty() && producerPanel.topicField.getText().isBlank() && consumerPanel.topicField.getText().isBlank()) {
                        producerPanel.topicField.setText(topics.get(0).name());
                        consumerPanel.topicField.setText(topics.get(0).name());
                    }
                    syncPartitionSelector();
                    String status = t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONNECTED, bootstrap);
                    setConnectionStatus(ModernColors.getSuccess(), status);
                    connectionPanel.btnCardLayout.show(connectionPanel.btnCard, CARD_DISCONNECT);
                    producerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_TOPICS_LOADED, topics.size()));
                    NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_STATUS_TOPICS_LOADED, topics.size()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Load kafka topics interrupted", ex);
                } catch (Exception ex) {
                    Throwable cause = unwrapException(ex);
                    setConnectionStatus(ModernColors.getError(), t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONNECT_FAILED, describeFailure(cause)));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONNECT_FAILED, describeFailure(cause)));
                    log.warn("Load kafka topics failed", cause);
                }
            }
        };
        worker.execute();
    }

    private void applyTopicFilter() {
        String keyword = topicPanel.topicSearchField.getText().trim().toLowerCase(Locale.ROOT);
        KafkaTopicItem selectedItem = topicPanel.topicList.getSelectedValue();
        String previousSelection = selectedItem == null ? null : selectedItem.name();
        topicPanel.topicFilteredModel.clear();
        for (int i = 0; i < topicPanel.topicListModel.getSize(); i++) {
            KafkaTopicItem topic = topicPanel.topicListModel.getElementAt(i);
            if (keyword.isBlank() || topic.name().toLowerCase(Locale.ROOT).contains(keyword)) {
                topicPanel.topicFilteredModel.addElement(topic);
            }
        }
        if (previousSelection != null && !previousSelection.isBlank()) {
            for (int i = 0; i < topicPanel.topicFilteredModel.getSize(); i++) {
                if (previousSelection.equals(topicPanel.topicFilteredModel.getElementAt(i).name())) {
                    topicPanel.topicList.setSelectedIndex(i);
                    topicPanel.topicList.ensureIndexIsVisible(i);
                    break;
                }
            }
        }
        if (topicPanel.topicList.getSelectedIndex() < 0 && keyword.isBlank() && !topicPanel.topicFilteredModel.isEmpty()) {
            topicPanel.topicList.setSelectedIndex(0);
        }
        topicPanel.topicSearchField.setNoResult(!keyword.isBlank() && topicPanel.topicFilteredModel.isEmpty());
    }

    private void sendMessage() {
        String topic = producerPanel.topicField.getText().trim();
        if (topic.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_ERR_TOPIC_REQUIRED));
            return;
        }
        String payload = producerPanel.payloadArea.getText();
        String key = producerPanel.keyField.getText().trim();
        Integer partition = (Integer) producerPanel.partitionSpinner.getValue();
        List<Header> headers;
        try {
            headers = KafkaPanelSupport.parseHeaders(producerPanel.headersArea.getText());
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }
        final Properties producerCustomProps;
        try {
            producerCustomProps = KafkaPanelSupport.parseCustomKafkaProperties(producerPanel.customPropsPanel.getValue());
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }

        // 在 EDT 预先读取所有 UI 字段（含 passwordField），避免非 EDT 线程读取 Swing 组件
        final Properties baseProps;
        try {
            baseProps = buildCommonClientProperties();
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }
        final Properties producerProps = buildProducerProperties(baseProps, producerCustomProps);
        final String producerConfigSignature = buildPropertiesSignature(producerProps);
        final long sendWaitMs = KafkaPanelSupport.futureWaitMs(parseLongOrDefault(
                producerProps.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG), PRODUCER_DELIVERY_TIMEOUT_MS));

        producerPanel.sendBtn.setEnabled(false);
        producerPanel.statusLabel.setForeground(ModernColors.getTextSecondary());
        producerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_SENDING));

        SwingWorker<RecordMetadata, Void> worker = new SwingWorker<>() {
            @Override
            protected RecordMetadata doInBackground() throws Exception {
                return KafkaClassLoaderSupport.withPluginContextClassLoader(() -> {
                    KafkaProducer<String, String> producer = getOrCreateProducer(producerProps, producerConfigSignature);
                    ProducerRecord<String, String> producerRecord;
                    String finalKey = key.isBlank() ? null : key;
                    if (partition != null && partition >= 0) {
                        producerRecord = new ProducerRecord<>(topic, partition, finalKey, payload);
                    } else {
                        producerRecord = new ProducerRecord<>(topic, finalKey, payload);
                    }
                    for (Header header : headers) {
                        producerRecord.headers().add(header);
                    }
                    return producer.send(producerRecord).get(sendWaitMs, TimeUnit.MILLISECONDS);
                });
            }

            @Override
            protected void done() {
                producerPanel.sendBtn.setEnabled(true);
                try {
                    RecordMetadata metadata = get();
                    String msg = t(MessageKeys.TOOLBOX_KAFKA_STATUS_SENT, metadata.partition(), metadata.offset());
                    producerPanel.statusLabel.setForeground(ModernColors.getSuccess());
                    producerPanel.statusLabel.setText(msg);
                    NotificationCenter.showSuccess(msg);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Send kafka message interrupted", ex);
                } catch (Exception ex) {
                    Throwable cause = unwrapException(ex);
                    // 发送失败时关闭缓存的 Producer，下次重建
                    closeAndClearCachedProducer();
                    producerPanel.statusLabel.setForeground(ModernColors.getError());
                    producerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_SEND_FAILED, describeFailure(cause)));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_KAFKA_STATUS_SEND_FAILED, describeFailure(cause)));
                    log.warn("Send kafka message failed", cause);
                }
            }
        };
        worker.execute();
    }

    /**
     * 获取或创建可复用的 KafkaProducer。
     * 配置变更时自动关闭旧 Producer 并重建。
     */
    private synchronized KafkaProducer<String, String> getOrCreateProducer(Properties producerProps, String producerConfigSignature) {
        KafkaProducer<String, String> existing = cachedProducerRef.get();
        if (existing != null && !producerConfigSignature.equals(cachedProducerConfigSignature)) {
            try {
                existing.close(Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Close old producer failed", e);
            }
            cachedProducerRef.set(null);
            cachedProducerConfigSignature = null;
            existing = null;
        }
        if (existing == null) {
            KafkaProducer<String, String> newProducer = KafkaClassLoaderSupport.withPluginContextClassLoader(
                    () -> new KafkaProducer<>(producerProps));
            cachedProducerRef.set(newProducer);
            cachedProducerConfigSignature = producerConfigSignature;
            return newProducer;
        }
        return existing;
    }

    /**
     * 发送失败或断开连接时，关闭并清理缓存的 Producer
     */
    private synchronized void closeAndClearCachedProducer() {
        KafkaProducer<String, String> producer = cachedProducerRef.getAndSet(null);
        if (producer != null) {
            try {
                producer.close(Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Close cached producer failed", e);
            }
        }
        cachedProducerConfigSignature = null;
    }

    private void startConsuming() {
        SwingWorker<Void, List<KafkaConsumedMessage>> existing = consumeWorkerRef.get();
        if (existing != null && !existing.isDone()) {
            return;
        }

        String topic = consumerPanel.topicField.getText().trim();
        if (topic.isBlank()) {
            NotificationCenter.showWarning(t(MessageKeys.TOOLBOX_KAFKA_ERR_TOPIC_REQUIRED));
            return;
        }
        final Set<Integer> selectedPartitions = consumerPanel.partitionSelector.getSelectedPartitions();

        int pollTimeoutMs = (Integer) consumerPanel.pollTimeoutSpinner.getValue();
        int maxPollRecords = (Integer) consumerPanel.batchSizeSpinner.getValue();
        KafkaConsumeStartMode consumeStartMode = selectedConsumeStartMode();
        Long consumeStartValue;
        try {
            consumeStartValue = KafkaPanelSupport.parseConsumeStartValue(consumeStartMode, consumerPanel.consumeStartValueField.getText());
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }
        final Properties consumerCustomProps;
        try {
            consumerCustomProps = KafkaPanelSupport.parseCustomKafkaProperties(consumerPanel.customPropsPanel.getValue());
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }

        // 在 EDT 预先读取所有 UI 字段（含 passwordField），避免非 EDT 线程读取 Swing 组件
        final Properties baseProps;
        try {
            baseProps = buildCommonClientProperties();
        } catch (IllegalArgumentException ex) {
            NotificationCenter.showWarning(rootMessage(ex));
            return;
        }

        // groupId 为空时生成临时唯一 groupId，避免干扰生产环境消费组
        String configuredGroupId = consumerPanel.groupIdField.getText().trim();
        final String finalGroupId = configuredGroupId.isBlank()
                ? "easy-postman-consumer-" + System.currentTimeMillis()
                : configuredGroupId;

        clearConsumedMessages();
        setConsuming(true);
        consumerPanel.statusLabel.setForeground(ModernColors.getTextSecondary());
        consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONSUMING, topic));

        SwingWorker<Void, List<KafkaConsumedMessage>> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                return KafkaClassLoaderSupport.withPluginContextClassLoader(() -> {
                    Properties props = buildConsumerProperties(baseProps, consumerCustomProps, finalGroupId, consumeStartMode, maxPollRecords);

                    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                        runningConsumerRef.set(consumer);
                        if (selectedPartitions.isEmpty()) {
                            AtomicBoolean startPositionApplied = new AtomicBoolean(false);
                            consumer.subscribe(Collections.singletonList(topic), new org.apache.kafka.clients.consumer.ConsumerRebalanceListener() {
                                @Override
                                public void onPartitionsRevoked(Collection<org.apache.kafka.common.TopicPartition> partitions) {
                                    // no-op
                                }

                                @Override
                                public void onPartitionsAssigned(Collection<org.apache.kafka.common.TopicPartition> partitions) {
                                    if (startPositionApplied.getAndSet(true)) {
                                        return;
                                    }
                                    KafkaPanelSupport.applyConsumeStartPosition(consumer, partitions, consumeStartMode, consumeStartValue);
                                }
                            });
                        } else {
                            List<org.apache.kafka.common.TopicPartition> topicPartitions =
                                    KafkaPanelSupport.resolveTopicPartitions(consumer, topic, selectedPartitions);
                            consumer.assign(topicPartitions);
                            KafkaPanelSupport.applyConsumeStartPosition(consumer, topicPartitions, consumeStartMode, consumeStartValue);
                        }
                        while (!isCancelled()) {
                            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
                            if (records.isEmpty()) {
                                continue;
                            }
                            List<KafkaConsumedMessage> batch = new ArrayList<>(records.count());
                            for (ConsumerRecord<String, String> rec : records) {
                                batch.add(new KafkaConsumedMessage(
                                        KafkaPanelSupport.formatRecordTimestamp(System.currentTimeMillis()),
                                        KafkaPanelSupport.formatRecordTimestamp(rec.timestamp()),
                                        rec.topic(),
                                        rec.partition(),
                                        rec.offset(),
                                        rec.key() == null ? "" : rec.key(),
                                        KafkaPanelSupport.formatHeaders(rec.headers()),
                                        rec.value() == null ? "" : rec.value()));
                            }
                            publish(batch);
                        }
                    } catch (WakeupException wakeupException) {
                        if (!isCancelled()) {
                            throw wakeupException;
                        }
                    } finally {
                        runningConsumerRef.compareAndSet(runningConsumerRef.get(), null);
                    }
                    return null;
                });
            }

            @Override
            protected void process(List<List<KafkaConsumedMessage>> chunks) {
                for (List<KafkaConsumedMessage> batch : chunks) {
                    consumedMessages.addAll(batch);
                }
                trimConsumedMessages();
                refreshMessageTable();
                consumerPanel.statusLabel.setForeground(ModernColors.getInfo());
                consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_RECORDS, consumedMessages.size()));
            }

            @Override
            protected void done() {
                setConsuming(false);
                if (isCancelled()) {
                    consumerPanel.statusLabel.setForeground(ModernColors.getTextSecondary());
                    consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_STOPPED));
                    return;
                }
                try {
                    get();
                    consumerPanel.statusLabel.setForeground(ModernColors.getTextSecondary());
                    consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_STOPPED));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("Consume kafka interrupted", ex);
                } catch (Exception ex) {
                    Throwable cause = unwrapException(ex);
                    consumerPanel.statusLabel.setForeground(ModernColors.getError());
                    consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONSUME_FAILED, describeFailure(cause)));
                    NotificationCenter.showError(t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONSUME_FAILED, describeFailure(cause)));
                    log.warn("Consume kafka message failed", cause);
                }
            }
        };
        consumeWorkerRef.set(worker);
        worker.execute();
    }

    private void stopConsuming() {
        SwingWorker<Void, List<KafkaConsumedMessage>> worker = consumeWorkerRef.get();
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        KafkaConsumer<String, String> consumer = runningConsumerRef.get();
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    private void clearConsumedMessages() {
        consumedMessages.clear();
        consumerPanel.messageTablePanel.clearData();
        consumerPanel.clearDetail();
        SwingWorker<Void, List<KafkaConsumedMessage>> worker = consumeWorkerRef.get();
        boolean consuming = worker != null && !worker.isDone();
        if (consuming) {
            String topic = consumerPanel.topicField.getText().trim();
            consumerPanel.statusLabel.setForeground(ModernColors.getInfo());
            consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_STATUS_CONSUMING, topic.isBlank() ? "-" : topic));
        } else {
            consumerPanel.statusLabel.setForeground(ModernColors.getTextSecondary());
            consumerPanel.statusLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_READY));
        }
    }

    private void setConsuming(boolean consuming) {
        consumerPanel.setConsuming(consuming);
    }

    private void trimConsumedMessages() {
        int maxView = (Integer) consumerPanel.maxViewSpinner.getValue();
        if (consumedMessages.size() <= maxView) {
            return;
        }
        int removeCount = consumedMessages.size() - maxView;
        consumedMessages.subList(0, removeCount).clear();
        // 超出上限后全量重建表格（保证表格与 consumedMessages 同步）
        rebuildMessageTable();
    }

    /**
     * 全量重建消息表格（用于 trim 后或 clear 后）
     */
    private void rebuildMessageTable() {
        MessageRowKey selectedKey = selectedMessageRowKey();
        List<Object[]> rows = consumedMessages.stream()
                .map(item -> new Object[]{
                        item.recordTime(),
                        item.topic(),
                        item.partition(),
                        item.offset(),
                        item.key(),
                        item.headers(),
                        item.value()
                }).toList();
        consumerPanel.messageTablePanel.setDataPreserveView(rows);
        restoreMessageSelection(selectedKey);
    }

    private void refreshMessageTable() {
        int tableRowCount = consumerPanel.messageTablePanel.getTotalRowCount();
        // 若 consumedMessages 因 trim 缩减，或用户已排序，则全量重建以保持表格视图顺序正确
        if (consumedMessages.size() < tableRowCount || consumerPanel.messageTablePanel.hasActiveSort()) {
            rebuildMessageTable();
        } else {
            // 增量追加新行，避免无排序场景下持续消费时频繁重建表格
            for (int i = tableRowCount; i < consumedMessages.size(); i++) {
                KafkaConsumedMessage item = consumedMessages.get(i);
                consumerPanel.messageTablePanel.addRow(new Object[]{
                        item.recordTime(),
                        item.topic(),
                        item.partition(),
                        item.offset(),
                        item.key(),
                        item.headers(),
                        item.value()
                });
            }
        }
        // 自动滚动到最新行
        if (consumerPanel.messageTablePanel.getTable().getRowCount() > 0) {
            int last = consumerPanel.messageTablePanel.getTable().getRowCount() - 1;
            consumerPanel.messageTablePanel.getTable().scrollRectToVisible(
                    consumerPanel.messageTablePanel.getTable().getCellRect(last, 0, true));
        }
    }

    private MessageRowKey selectedMessageRowKey() {
        Object[] row = consumerPanel.messageTablePanel.getSelectedRowData();
        if (row == null) {
            return null;
        }
        return messageRowKey(row);
    }

    private void restoreMessageSelection(MessageRowKey selectedKey) {
        if (selectedKey == null) {
            return;
        }
        JTable table = consumerPanel.messageTablePanel.getTable();
        for (int row = 0; row < table.getRowCount(); row++) {
            Object[] rowData = consumerPanel.messageTablePanel.getVisibleRowData(row);
            MessageRowKey rowKey = messageRowKey(rowData);
            if (selectedKey.equals(rowKey)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private void updateDetailBySelection() {
        Object[] row = consumerPanel.messageTablePanel.getSelectedRowData();
        if (row == null) {
            consumerPanel.clearDetail();
            return;
        }
        consumerPanel.updateDetail(messageFromTableRow(row));
    }

    private KafkaConsumedMessage messageFromTableRow(Object[] row) {
        MessageRowKey key = messageRowKey(row);
        KafkaConsumedMessage message = findConsumedMessage(key);
        if (message != null) {
            return message;
        }
        return new KafkaConsumedMessage(
                "",
                cellString(row, 0),
                cellString(row, 1),
                cellInt(row, 2),
                cellLong(row, 3),
                cellString(row, 4),
                cellString(row, 5),
                cellString(row, 6));
    }

    private KafkaConsumedMessage findConsumedMessage(MessageRowKey key) {
        if (key == null) {
            return null;
        }
        for (int i = consumedMessages.size() - 1; i >= 0; i--) {
            KafkaConsumedMessage item = consumedMessages.get(i);
            if (key.equals(messageRowKey(item))) {
                return item;
            }
        }
        return null;
    }

    private static MessageRowKey messageRowKey(Object[] row) {
        if (row == null) {
            return null;
        }
        return new MessageRowKey(
                cellString(row, 1),
                cellString(row, 2),
                cellString(row, 3));
    }

    private static MessageRowKey messageRowKey(KafkaConsumedMessage item) {
        if (item == null) {
            return null;
        }
        return new MessageRowKey(
                item.topic(),
                String.valueOf(item.partition()),
                String.valueOf(item.offset()));
    }

    private static String cellString(Object[] row, int index) {
        Object value = cell(row, index);
        return value == null ? "" : value.toString();
    }

    private static int cellInt(Object[] row, int index) {
        Object value = cell(row, index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value == null ? "0" : value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static long cellLong(Object[] row, int index) {
        Object value = cell(row, index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value == null ? "0" : value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static Object cell(Object[] row, int index) {
        return row == null || index < 0 || index >= row.length ? null : row[index];
    }

    private record MessageRowKey(String topic, String partition, String offset) {
    }

    private void updateEditorFont() {
        producerPanel.updateEditorFont();
        consumerPanel.updateEditorFont();
    }

    private Properties buildCommonClientProperties() {
        String bootstrap = connectionPanel.bootstrapField.getText().trim();
        if (bootstrap.isBlank()) {
            throw new IllegalArgumentException(t(MessageKeys.TOOLBOX_KAFKA_ERR_HOST_REQUIRED));
        }

        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

        String clientId = connectionPanel.clientIdField.getText().trim();
        if (!clientId.isBlank()) {
            props.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId);
        }

        String securityProtocol = selectedSecurityProtocol();
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);

        if (securityProtocol.startsWith("SASL")) {
            String username = connectionPanel.usernameField.getText().trim();
            String password = new String(connectionPanel.passwordField.getPassword());
            if (username.isBlank() || password.isBlank()) {
                throw new IllegalArgumentException(t(MessageKeys.TOOLBOX_KAFKA_ERR_SASL_CREDENTIAL_REQUIRED));
            }

            String mechanism = selectedSaslMechanism();
            props.put(SaslConfigs.SASL_MECHANISM, mechanism);
            String loginModule = mechanism.startsWith("SCRAM")
                    ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                    : "org.apache.kafka.common.security.plain.PlainLoginModule";
            props.put(SaslConfigs.SASL_JAAS_CONFIG,
                    loginModule + " required username=\"" + KafkaPanelSupport.escapeJaasValue(username)
                            + "\" password=\"" + KafkaPanelSupport.escapeJaasValue(password) + "\";");
        }

        return props;
    }

    private Properties buildProducerProperties(Properties baseProps, Properties producerCustomProps) {
        Properties props = new Properties();
        props.putAll(baseProps);
        props.putAll(producerCustomProps);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        props.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, String.valueOf(PRODUCER_DELIVERY_TIMEOUT_MS));
        return props;
    }

    private Properties buildConsumerProperties(
            Properties baseProps,
            Properties consumerCustomProps,
            String groupId,
            KafkaConsumeStartMode consumeStartMode,
            int maxPollRecords) {
        Properties props = new Properties();
        props.putAll(baseProps);
        props.putAll(consumerCustomProps);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumeStartMode.autoOffsetReset());
        // 禁用自动提交：工具类消费不应修改服务端 group offset
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));
        return props;
    }

    private String selectedSecurityProtocol() {
        Object selected = connectionPanel.securityProtocolCombo.getSelectedItem();
        return selected == null ? "PLAINTEXT" : selected.toString();
    }

    private String selectedSaslMechanism() {
        Object selected = connectionPanel.saslMechanismCombo.getSelectedItem();
        return selected == null ? "PLAIN" : selected.toString();
    }

    private static void selectComboItem(JComboBox<String> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            String item = combo.getItemAt(i);
            if (Objects.equals(item, value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = defaultString(value).trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private KafkaConsumeStartMode selectedConsumeStartMode() {
        int idx = consumerPanel.autoOffsetCombo.getSelectedIndex();
        return switch (idx) {
            case 1 -> KafkaConsumeStartMode.EARLIEST;
            case 2 -> KafkaConsumeStartMode.NONE;
            case 3 -> KafkaConsumeStartMode.TIMESTAMP;
            case 4 -> KafkaConsumeStartMode.OFFSET;
            default -> KafkaConsumeStartMode.LATEST;
        };
    }

    private static String buildPropertiesSignature(Properties properties) {
        return KafkaPanelSupport.buildPropertiesSignature(properties);
    }

    private void doDisconnect() {
        stopConsuming();
        closeAndClearCachedProducer();
        topicPanel.topicListModel.clear();
        topicPanel.topicFilteredModel.clear();
        topicPartitionCountMap.clear();
        consumerPanel.partitionSelector.setAvailablePartitions(Collections.emptyList());
        connectionPanel.btnCardLayout.show(connectionPanel.btnCard, CARD_CONNECT);
        setConnectionStatus(ModernColors.getTextSecondary(), t(MessageKeys.TOOLBOX_KAFKA_STATUS_NOT_CONNECTED));
        NotificationCenter.showInfo(t(MessageKeys.TOOLBOX_KAFKA_DISCONNECT_SUCCESS));
    }

    private void syncPartitionSelector() {
        if (consumerPanel == null) {
            return;
        }
        String topic = consumerPanel.topicField.getText().trim();
        Integer partitionCount = topicPartitionCountMap.get(topic);
        if (partitionCount == null || partitionCount <= 0) {
            consumerPanel.partitionSelector.setAvailablePartitions(Collections.emptyList());
            return;
        }
        List<Integer> partitions = new ArrayList<>(partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(i);
        }
        consumerPanel.partitionSelector.setAvailablePartitions(partitions);
    }

    private void setConnectionStatus(Color color, String message) {
        if (producerPanel != null) {
            producerPanel.statusLabel.setForeground(color);
            producerPanel.statusLabel.setText(message);
        }
    }

    private static Throwable unwrapException(Exception ex) {
        if (ex instanceof ExecutionException ee && ee.getCause() != null) {
            return ee.getCause();
        }
        return ex;
    }

    private static long parseLongOrDefault(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static String describeFailure(Throwable throwable) {
        String message = rootMessage(throwable);
        return KafkaPanelSupport.isTimeout(throwable)
                ? message + " " + t(MessageKeys.TOOLBOX_KAFKA_ERR_TIMEOUT_HINT)
                : message;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return (msg == null || msg.isBlank()) ? current.toString() : msg;
    }
}
