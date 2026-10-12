package com.laker.postman.startup;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class AppLauncherShutdownTest {
    @Test
    public void shouldFlushQueuedLogsEvenWhenHostRuntimeShutdownIsSkipped() throws Exception {
        Path directory = Files.createTempDirectory("easypostman-shutdown-log-");
        Path logFile = directory.resolve("shutdown.log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true",
                "-Duser.home=" + directory,
                "-DCONSOLE_LOG_LEVEL=OFF",
                "-cp", System.getProperty("java.class.path"),
                ShutdownProbe.class.getName(), logFile.toString()
        ).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Shutdown probe did not terminate");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(process.exitValue(), 0, output);
            String logs = Files.readString(logFile);
            for (int index = 0; index < 40; index++) {
                assertTrue(logs.contains("queued-log-" + index + "\n"), logs);
            }
            assertTrue(logs.contains("JVM shutdown hook started"), logs);
            assertTrue(logs.contains("Application shutdown completed"), logs);
        } finally {
            process.destroyForcibly();
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    public static class ShutdownProbe {
        public static void main(String[] args) throws Exception {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.reset();
            context.getStatusManager().clear();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(AppLauncher.class.getResource("/logback.xml"));
            boolean callerConflict = context.getStatusManager().getCopyOfStatusList().stream()
                    .anyMatch(status -> status.getMessage().contains("instruct against caller extraction"));
            if (callerConflict) {
                throw new IllegalStateException("Logback caller instructions conflict");
            }
            context.reset();
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%msg%n");
            encoder.start();

            SlowFileAppender fileAppender = new SlowFileAppender();
            fileAppender.setContext(context);
            fileAppender.setName("slowFile");
            fileAppender.setFile(args[0]);
            fileAppender.setEncoder(encoder);
            fileAppender.start();

            AsyncAppender asyncAppender = new AsyncAppender();
            asyncAppender.setContext(context);
            asyncAppender.setName("asyncFile");
            asyncAppender.setQueueSize(128);
            asyncAppender.setDiscardingThreshold(0);
            asyncAppender.setMaxFlushTime(5_000);
            asyncAppender.addAppender(fileAppender);
            asyncAppender.start();
            var logger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            logger.setLevel(Level.INFO);
            logger.addAppender(asyncAppender);

            Field skipRuntimeShutdown = AppLauncher.class.getDeclaredField("skipHostRuntimeShutdown");
            skipRuntimeShutdown.setAccessible(true);
            skipRuntimeShutdown.setBoolean(null, true);
            Method registerShutdownHook = AppLauncher.class.getDeclaredMethod("registerShutdownHook");
            registerShutdownHook.setAccessible(true);
            registerShutdownHook.invoke(null);

            for (int index = 0; index < 40; index++) {
                logger.info("queued-log-{}", index);
            }
        }
    }

    public static class SlowFileAppender extends FileAppender<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            // 模拟慢磁盘，确保退出时异步队列尚未清空。
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
                // AsyncAppender.stop 会中断worker；剩余队列仍应被刷新。
            }
            super.append(event);
        }
    }
}
