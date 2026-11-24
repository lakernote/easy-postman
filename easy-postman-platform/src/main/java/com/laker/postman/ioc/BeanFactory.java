package com.laker.postman.ioc;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * IOC容器工具类，提供便捷的访问方法
 */
@Slf4j
@UtilityClass
public class BeanFactory {

    private final ApplicationContext context = ApplicationContext.getInstance();

    /**
     * 初始化IOC容器，扫描指定包
     */
    public void init(String... basePackages) {
        context.scan(basePackages);
    }

    /**
     * 显式注册组件类，启动时无需扫描类路径
     */
    public void register(Class<?>... componentClasses) {
        context.register(componentClasses);
    }

    /**
     * 根据名称获取Bean
     */
    public <T> T getBean(String beanName) {
        return context.getBean(beanName);
    }

    /**
     * 根据类型获取Bean
     */
    public <T> T getBean(Class<T> requiredType) {
        return context.getBean(requiredType);
    }

    /**
     * 销毁所有Bean，调用@PreDestroy方法
     * 建议在应用程序关闭时调用此方法
     */
    public void destroy() {
        log.info("Destroying IOC container...");
        context.destroy();
    }
}
