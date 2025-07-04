package com.laker.postman.common.panel;

import javax.swing.*;

/**
 * 面板基础抽象类，统一UI初始化和监听注册结构
 * 提供了initUI和registerListeners两个抽象方法，
 * 子类需要实现这两个方法来完成具体的UI组件初始化和事件监听注册。
 * SingletonPanelFactory 可以用来获取面板的单例实例。
 */
public abstract class BasePanel extends JPanel {


    public void safeInit() {
        initUI();
        registerListeners();
    }

    /**
     * 初始化UI组件
     */
    protected abstract void initUI();

    /**
     * 注册事件监听
     */
    protected abstract void registerListeners();
}

