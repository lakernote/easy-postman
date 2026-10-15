package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.service.sync.WebDavRemoteSnapshot;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Font;
import java.awt.Window;
import java.util.Objects;
import java.util.Optional;

/** Shared confirmation UX for WebDAV actions opened from settings or the toolbar. */
final class WebDavSyncConfirmationDialog {
    private WebDavSyncConfirmationDialog() {
    }

    static boolean confirmUpload(Component parent, Optional<WebDavRemoteSnapshot> remoteSnapshot) {
        return confirm(
                parent,
                MessageKeys.SETTINGS_WEBDAV_SYNC_UPLOAD_CONFIRM_TITLE,
                MessageKeys.SETTINGS_WEBDAV_SYNC_UPLOAD_CONFIRM_MESSAGE,
                remoteSnapshot
        );
    }

    static boolean confirmRestore(Component parent, Optional<WebDavRemoteSnapshot> remoteSnapshot) {
        return confirm(
                parent,
                MessageKeys.SETTINGS_WEBDAV_SYNC_RESTORE_CONFIRM_TITLE,
                MessageKeys.SETTINGS_WEBDAV_SYNC_RESTORE_CONFIRM_MESSAGE,
                remoteSnapshot
        );
    }

    private static boolean confirm(Component parent,
                                   String titleKey,
                                   String messageKey,
                                   Optional<WebDavRemoteSnapshot> remoteSnapshot) {
        JTextArea message = new JTextArea(
                I18nUtil.getMessage(messageKey, WebDavSyncSettingsPanel.remoteSnapshotText(remoteSnapshot)),
                8,
                48
        );
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);
        message.setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(Font.PLAIN));
        message.setBorder(null);

        Object[] options = {
                I18nUtil.getMessage(MessageKeys.TOP_MENU_WEBDAV_CONFIRM_NO),
                I18nUtil.getMessage(MessageKeys.TOP_MENU_WEBDAV_CONFIRM_YES)
        };
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        Component dialogParent = owner == null ? parent : owner;
        JOptionPane pane = new JOptionPane(
                message,
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                options,
                options[0]
        );
        JDialog dialog = pane.createDialog(dialogParent, I18nUtil.getMessage(titleKey));
        dialog.setLocationRelativeTo(dialogParent);
        dialog.setVisible(true);
        dialog.dispose();
        return Objects.equals(pane.getValue(), options[1]);
    }
}
