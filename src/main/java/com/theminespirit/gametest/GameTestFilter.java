package com.theminespirit.gametest;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EventBusSubscriber(modid = "theminespirit", value = Dist.DEDICATED_SERVER)
public final class GameTestFilter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String FILTER_PROPERTY = "theminespirit.gametest.filter";

    private GameTestFilter() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        List<String> filters = parseFilters(System.getProperty(FILTER_PROPERTY, ""));
        if (filters.isEmpty()) {
            return;
        }
        try {
            applyFilter(server, filters);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось применить фильтр GameTest " + filters, e);
        }
    }

    private static List<String> parseFilters(String raw) {
        List<String> filters = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                filters.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return filters;
    }

    private static void applyFilter(GameTestServer server, List<String> filters) throws ReflectiveOperationException {
        Field field = GameTestServer.class.getDeclaredField("testFunctions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<TestFunction> functions = (List<TestFunction>) field.get(server);
        Map<String, Set<String>> classTests = classToTests();
        int before = functions.size();
        functions.removeIf(function -> filters.stream().noneMatch(filter -> matches(function, filter, classTests)));
        int removed = before - functions.size();
        if (removed == before) {
            throw new IllegalStateException("Фильтр GameTest " + filters + " не совпал ни с одной пачкой, "
                    + "классом или тестом — проверьте имя: прогон не состоится");
        }
        LOGGER.info("GameTest filter {}: удалено {} тестов из {}, осталось {}", filters, removed, before, functions.size());
    }

    private static boolean matches(TestFunction function, String filter, Map<String, Set<String>> classTests) {
        String batch = function.batchName().toLowerCase(Locale.ROOT);
        String test = function.testName().toLowerCase(Locale.ROOT);
        if (filter.equals(batch) || filter.equals(test)) {
            return true;
        }
        int dot = filter.indexOf('.');
        if (dot >= 0) {
            return filter.substring(dot + 1).equals(test)
                    && classTests.getOrDefault(filter.substring(0, dot), Set.of()).contains(test);
        }
        return classTests.getOrDefault(filter, Set.of()).contains(test);
    }

    private static Map<String, Set<String>> classToTests() {
        Map<String, Set<String>> classTests = new HashMap<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(GameTestHolder.class, ElementType.TYPE)
                    .forEach(annotation -> collectTests(annotation.clazz().getClassName(), classTests));
        }
        return classTests;
    }

    private static void collectTests(String className, Map<String, Set<String>> classTests) {
        try {
            Class<?> testClass = Class.forName(className, false, GameTestFilter.class.getClassLoader());
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GameTest.class)) {
                    String simpleName = testClass.getSimpleName().toLowerCase(Locale.ROOT);
                    classTests.computeIfAbsent(simpleName, key -> new HashSet<>())
                            .add(method.getName().toLowerCase(Locale.ROOT));
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.warn("GameTest-класс {} из сканирования не загрузился для фильтра", className, e);
        }
    }
}
