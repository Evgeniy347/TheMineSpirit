package com.theminespirit.gametest;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class GameTestClassIndex {
    private static final Logger LOGGER = LogManager.getLogger();

    private GameTestClassIndex() {}

    static Map<String, Set<String>> byClass() {
        List<SceneTestOwners.TestClass> classes = new ArrayList<>();
        Set<String> stepOwners = new HashSet<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(GameTestHolder.class, ElementType.TYPE)
                    .forEach(annotation -> read(annotation.clazz().getClassName(), classes, stepOwners));
        }
        return SceneTestOwners.index(classes, stepOwners);
    }

    private static void read(String className, List<SceneTestOwners.TestClass> classes, Set<String> stepOwners) {
        Class<?> testClass;
        try {
            testClass = Class.forName(className, false, GameTestClassIndex.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.warn("GameTest-класс {} из сканирования не загрузился для фильтра", className, e);
            return;
        }
        Set<String> tests = new HashSet<>();
        boolean sceneSteps = false;
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GameTest.class)) {
                tests.add(method.getName().toLowerCase(Locale.ROOT));
            }
            if (method.isAnnotationPresent(SceneStep.class)) {
                sceneSteps = true;
            }
        }
        if (sceneSteps) {
            stepOwners.add(className.replace('.', '/'));
        }
        if (!tests.isEmpty()) {
            classes.add(new SceneTestOwners.TestClass(testClass.getSimpleName(), tests, ClassRefs.of(testClass)));
        }
    }
}
