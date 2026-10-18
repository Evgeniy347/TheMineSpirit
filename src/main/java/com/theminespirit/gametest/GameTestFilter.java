package com.theminespirit.gametest;

import com.theminespirit.neoforge.BuildPace;
import com.theminespirit.neoforge.SpiritConfig;
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
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
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
        // Доска и координатор тикают глобально и раздают задачи/приказы любому свободному духу,
        // включая духов чужих сцен: пачка chop_tree_* стояла в RUNNING с чужой задачей (флак 17.10).
        // По умолчанию в GameTest обе системы выключены; включает их только размеченная пачка.
        SpiritConfig.setBoardEnabled(false);
        SpiritConfig.setCoordinatorEnabled(false);
        // Темп стройки (около 2 блоков в секунду) проверяет своя пачка build_pace; остальным сценам он только
        // удлиняет прогон, геометрию постройки он не меняет.
        BuildPace.overrideForGameTest(1);
        installSettlementBatches();
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

    /**
     * Вешает на размеченные пачки включение доски/координатора и выключение после.
     * Хуки пачек лежат в приватных картах реестра (их читает {@link GameTestBatch});
     * добавляем в карты сами, потому что одна пара методов на пачку — это тот же бойлерплейт,
     * от которого уходим. Существующий хук пачки (например, выключение мобов-целей) сохраняется.
     */
    private static void installSettlementBatches() {
        Map<String, Settlement> batches = settlementBatches();
        if (batches.isEmpty()) {
            return;
        }
        Map<String, Consumer<ServerLevel>> before = batchFunctions("BEFORE_BATCH_FUNCTIONS");
        Map<String, Consumer<ServerLevel>> after = batchFunctions("AFTER_BATCH_FUNCTIONS");
        for (Map.Entry<String, Settlement> entry : batches.entrySet()) {
            Settlement settlement = entry.getValue();
            before.merge(entry.getKey(), level -> enableSettlement(settlement), Consumer::andThen);
            after.merge(entry.getKey(), level -> disableSettlement(), (existing, added) -> added.andThen(existing));
        }
        LOGGER.info("GameTest: доска и координатор выключены по умолчанию, включены для пачек {}", batches.keySet());
    }

    private static Map<String, Settlement> settlementBatches() {
        Map<String, Settlement> batches = new HashMap<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(GameTestHolder.class, ElementType.TYPE)
                    .forEach(annotation -> collectSettlement(annotation.clazz().getClassName(), batches));
        }
        return batches;
    }

    private static void collectSettlement(String className, Map<String, Settlement> batches) {
        try {
            Class<?> testClass = Class.forName(className, false, GameTestFilter.class.getClassLoader());
            SettlementSystems classWide = testClass.getAnnotation(SettlementSystems.class);
            for (Method method : testClass.getDeclaredMethods()) {
                GameTest test = method.getAnnotation(GameTest.class);
                if (test == null) {
                    continue;
                }
                SettlementSystems own = method.getAnnotation(SettlementSystems.class);
                SettlementSystems selected = own == null ? classWide : own;
                if (selected == null) {
                    continue;
                }
                String batch = test.batch().isEmpty() ? GameTestBatch.DEFAULT_BATCH_NAME : test.batch();
                batches.merge(batch, new Settlement(selected.board(), selected.coordinator()), Settlement::combined);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("GameTest-класс " + className + " из сканирования не загрузился: "
                    + "размеченная пачка осталась бы без доски/координатора", e);
        }
    }

    private static void enableSettlement(Settlement settlement) {
        SpiritConfig.setBoardEnabled(settlement.board());
        SpiritConfig.setCoordinatorEnabled(settlement.coordinator());
    }

    private static void disableSettlement() {
        SpiritConfig.setBoardEnabled(false);
        SpiritConfig.setCoordinatorEnabled(false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Consumer<ServerLevel>> batchFunctions(String fieldName) {
        try {
            Field field = GameTestRegistry.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Map<String, Consumer<ServerLevel>>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось получить хуки пачек GameTest (" + fieldName
                    + "): размеченные пачки остались бы без доски/координатора", e);
        }
    }

    private record Settlement(boolean board, boolean coordinator) {
        private Settlement combined(Settlement other) {
            return new Settlement(board || other.board, coordinator || other.coordinator);
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
