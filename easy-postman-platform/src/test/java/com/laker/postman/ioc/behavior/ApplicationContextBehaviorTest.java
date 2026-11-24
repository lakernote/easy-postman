package com.laker.postman.ioc.behavior;

import com.laker.postman.ioc.ApplicationContext;
import com.laker.postman.ioc.BeanCreationException;
import com.laker.postman.ioc.BeanException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ApplicationContextBehaviorTest {
    private ApplicationContext context;

    @BeforeMethod
    public void setUp() {
        context = ApplicationContext.getInstance();
        context.clear();
    }

    @AfterMethod
    public void tearDown() {
        context.clear();
    }

    @Test
    public void shouldInjectAutowiredMethods() {
        context.scan("com.laker.postman.ioc.behavior");

        MethodInjectedService service = context.getBean(MethodInjectedService.class);

        assertSame(service.getDependency(), context.getBean(MethodInjectedDependency.class));
    }

    @Test
    public void shouldNotDuplicateTypeIndexesWhenScanningSamePackageTwice() {
        context.scan("com.laker.postman.ioc.behavior");
        context.scan("com.laker.postman.ioc.behavior");

        assertSame(context.getBean(MethodInjectedDependency.class), context.getBean("methodInjectedDependency"));
    }

    @Test
    public void shouldScanWithFallbackClassLoaderWhenContextClassLoaderIsNull() {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(null);

            context.scan("com.laker.postman.ioc.behavior");

            assertSame(context.getBean(MethodInjectedDependency.class), context.getBean("methodInjectedDependency"));
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    public void shouldRegisterExplicitComponentsWithoutScanning() {
        context.register(MethodInjectedService.class, MethodInjectedDependency.class);

        MethodInjectedService service = context.getBean(MethodInjectedService.class);

        assertSame(service.getDependency(), context.getBean("methodInjectedDependency"));
        assertThrows(BeanException.class, () -> context.register(String.class));
    }

    @Test
    public void shouldFailFastForConstructorCircularDependency() {
        context.scan("com.laker.postman.ioc.behavior");

        BeanCreationException exception = expectThrows(BeanCreationException.class,
                () -> context.getBean(ConstructorCycleA.class));

        assertTrue(hasCauseMessageContaining(exception, "Circular constructor dependency"));
    }

    private boolean hasCauseMessageContaining(Throwable throwable, String expectedMessage) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessage)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
