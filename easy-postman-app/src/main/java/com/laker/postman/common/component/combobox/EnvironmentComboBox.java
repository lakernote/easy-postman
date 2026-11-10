package com.laker.postman.common.component.combobox;

import com.laker.postman.model.Environment;
import com.laker.postman.environment.EnvironmentItem;
import com.laker.postman.service.EnvironmentService;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

public class EnvironmentComboBox extends MenuBarComboBox<EnvironmentItem> {
    private boolean isUpdating = false;
    @Setter
    private Consumer<Environment> onEnvironmentChange;

    public EnvironmentComboBox() {
        setRenderer(new EnvironmentItemRenderer());
        addActionListener(e -> {
            if (isUpdating) return;
            EnvironmentItem item = (EnvironmentItem) getSelectedItem();
            if (item != null && item.getEnvironment() != null) {
                String envId = item.getEnvironment().getId();
                if (envId != null) {
                    EnvironmentService.setActiveEnvironment(envId);
                    if (onEnvironmentChange != null) {
                        onEnvironmentChange.accept(item.getEnvironment());
                    }
                }
            }
        });
        reload();
    }

    public void reload() {
        isUpdating = true;
        removeAllItems();
        List<Environment> envs = EnvironmentService.getAllEnvironments();
        Environment activeEnv = null;
        for (Environment env : envs) {
            addItem(new EnvironmentItem(env));
            if (env.isActive()) {
                activeEnv = env;
            }
        }
        if (activeEnv != null) {
            String activeId = activeEnv.getId();
            if (activeId != null) {
                for (int i = 0; i < getItemCount(); i++) {
                    EnvironmentItem item = getItemAt(i);
                    Environment itemEnv = item.getEnvironment();
                    if (itemEnv != null && itemEnv.getId() != null && itemEnv.getId().equals(activeId)) {
                        setSelectedIndex(i);
                        break;
                    }
                }
            }
        } else if (getItemCount() > 0) {
            setSelectedIndex(0);
        }
        isUpdating = false;
    }

    /**
     * 选中指定环境
     */
    public void setSelectedEnvironment(Environment env) {
        if (env == null || env.getId() == null) return;
        isUpdating = true;
        String envId = env.getId();
        for (int i = 0; i < getItemCount(); i++) {
            EnvironmentItem item = getItemAt(i);
            Environment itemEnv = item.getEnvironment();
            if (itemEnv != null && itemEnv.getId() != null && itemEnv.getId().equals(envId)) {
                setSelectedIndex(i);
                break;
            }
        }
        isUpdating = false;
    }
}