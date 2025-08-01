package com.laker.postman.common.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

/**
 * 通用开始按钮，带图标和统一样式。
 */
public class StartButton extends JButton {
    public StartButton() {
        super("Start");
        setIcon(new FlatSVGIcon("icons/start.svg"));
        setPreferredSize(new Dimension(90, 28));
    }
}

