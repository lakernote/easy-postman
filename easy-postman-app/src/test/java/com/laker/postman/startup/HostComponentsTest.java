package com.laker.postman.startup;

import com.laker.postman.ioc.Component;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

public class HostComponentsTest {

    @Test
    public void componentListMatchesAnnotatedHostClasses() throws Exception {
        Path classesRoot = Path.of(HostComponents.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassLoader loader = HostComponents.class.getClassLoader();
        Set<String> annotated = new TreeSet<>();
        try (Stream<Path> files = Files.walk(classesRoot)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                String relative = classesRoot.relativize(file).toString().replace('\\', '/');
                String className = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                try {
                    if (Class.forName(className, false, loader).isAnnotationPresent(Component.class)) {
                        annotated.add(className);
                    }
                } catch (LinkageError ignored) {
                    // 缺少可选依赖的类不可能是宿主组件
                }
            }
        }

        Set<String> listed = Arrays.stream(HostComponents.classes())
                .map(Class::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(listed, annotated, "HostComponents must list every @Component class in easy-postman-app");
    }
}
