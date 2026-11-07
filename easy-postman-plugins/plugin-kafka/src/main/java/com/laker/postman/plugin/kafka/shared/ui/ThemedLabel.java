package com.laker.postman.plugin.kafka.shared.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * 前景色按语义色动态取值的标签：亮/暗主题切换后无需重新 setForeground，绘制时即取当前主题颜色。
 */
public class ThemedLabel extends JLabel {

    private transient Supplier<Color> foregroundSupplier;

    public ThemedLabel(String text, Supplier<Color> foregroundSupplier) {
        super(text);
        this.foregroundSupplier = foregroundSupplier;
    }

    public void setForegroundSupplier(Supplier<Color> foregroundSupplier) {
        this.foregroundSupplier = foregroundSupplier;
        repaint();
    }

    @Override
    public Color getForeground() {
        Supplier<Color> supplier = foregroundSupplier;
        Color color = supplier == null ? null : supplier.get();
        return color != null ? color : super.getForeground();
    }
}
