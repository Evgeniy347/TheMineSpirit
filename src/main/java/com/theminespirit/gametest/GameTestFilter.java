package com.theminespirit.gametest;

import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EventBusSubscriber(modid = "theminespirit", value = Dist.DEDICATED_SERVER)
public final class GameTestFilter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String FILTER_PROPERTY = "theminespirit.gametest.filter";
    private static final String EXCLUDE_PROPERTY = "theminespirit.gametest.exclude";
    private static final String SHARD_PROPERTY = "theminespirit.gametest.shard";
    // Свой у каждого шарда gameDirectory, поэтому общий файл аргументов JVM здесь не годится.
    private static final String RUN_OPTIONS_FILE = "theminespirit-gametest.properties";
    private static final String WEIGHTS_FILE = "theminespirit-gametest-weights.txt";

    private GameTestFilter() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        // Доска и координатор тикают глобально и раздают задачи/приказы любому свободному духу,
        // включая духов чужих сцен: пачка chop_tree_* стояла в RUNNING с чужой задачей (флак 17.10).
        // По умолчанию в GameTest обе системы выключены; включает их только размеченная пачка.
        setBoardEnabled(false);
        setCoordinatorEnabled(false);
        installSettlementBatches();
        List<String> filters = parseFilters(System.getProperty(FILTER_PROPERTY, ""));
        Map<String, String> runOptions = readRunOptions();
        String excludeRaw = runOptions.getOrDefault("exclude", System.getProperty(EXCLUDE_PROPERTY, ""));
        String shardRaw = runOptions.getOrDefault("shard", System.getProperty(SHARD_PROPERTY, ""));
        List<String> excludes = parseFilters(excludeRaw);
        Shard shard = Shard.parse(shardRaw);
        if (filters.isEmpty() && excludes.isEmpty() && shard == null) {
            return;
        }
        try {
            applyFilter(server, filters, excludes, shard, readWeights());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось применить фильтр GameTest " + filters, e);
        }
    }

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
            throw new IllegalStateException(
                    "GameTest-класс " + className + " из сканирования не загрузился: "
                            + "размеченная пачка осталась бы без доски/координатора",
                    e);
        }
    }

    private static void enableSettlement(Settlement settlement) {
        setBoardEnabled(settlement.board());
        setCoordinatorEnabled(settlement.coordinator());
    }

    private static void disableSettlement() {
        setBoardEnabled(false);
        setCoordinatorEnabled(false);
    }

    public static void setBoardEnabled(boolean value) {
        setSpiritValue("spirit.boardEnabled", value);
    }

    public static void setCoordinatorEnabled(boolean value) {
        setSpiritValue("spirit.coordinatorEnabled", value);
    }

    public static void setMobsTargetSpirits(boolean value) {
        setSpiritValue("spirit.mobsTargetSpirits", value);
    }

    public static void setProtectedZones(List<String> lines) {
        ModConfigSpec.ConfigValue<List<String>> value =
                PolicyConfig.spec().getValues().get(List.of("policy", "protectedZones"));
        value.set(List.copyOf(lines));
    }

    private static void setSpiritValue(String path, boolean value) {
        ModConfigSpec.ConfigValue<Boolean> configValue =
                SpiritConfig.spec().getValues().get(path);
        configValue.set(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Consumer<ServerLevel>> batchFunctions(String fieldName) {
        try {
            Field field = GameTestRegistry.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Map<String, Consumer<ServerLevel>>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Не удалось получить хуки пачек GameTest (" + fieldName
                            + "): размеченные пачки остались бы без доски/координатора",
                    e);
        }
    }

    private record Settlement(boolean board, boolean coordinator) {
        private Settlement combined(Settlement other) {
            return new Settlement(board || other.board, coordinator || other.coordinator);
        }
    }

    private static Map<String, String> readRunOptions() {
        Path path = Path.of(RUN_OPTIONS_FILE);
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Map<String, String> options = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                int equals = trimmed.indexOf('=');
                if (equals <= 0 || trimmed.startsWith("#")) {
                    continue;
                }
                options.put(
                        trimmed.substring(0, equals).trim(),
                        trimmed.substring(equals + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не прочитан " + path.toAbsolutePath() + ": настройки шарда и исключений неизвестны", e);
        }
        return options;
    }

    private static List<String> parseFilters(String raw) {
        List<String> filters = new ArrayList<>();
        for (String part : raw.split(",", -1)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                filters.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return filters;
    }

    private static void applyFilter(
            GameTestServer server,
            List<String> filters,
            List<String> excludes,
            Shard shard,
            Map<String, Long> measuredWeights)
            throws ReflectiveOperationException {
        Field field = GameTestServer.class.getDeclaredField("testFunctions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<TestFunction> functions = (List<TestFunction>) field.get(server);
        Map<String, Set<String>> classTests = classToTests();
        int before = functions.size();
        if (!filters.isEmpty()) {
            functions.removeIf(function -> filters.stream().noneMatch(filter -> matches(function, filter, classTests)));
            if (functions.size() == before) {
                throw new IllegalStateException("Фильтр GameTest " + filters + " не совпал ни с одной пачкой, "
                        + "классом или тестом — проверьте имя: прогон не состоится");
            }
        } else if (!excludes.isEmpty()) {
            // Быстрый набор: тяжёлые пачки (демо-аудиты построек) остаются ночному --full.
            functions.removeIf(function -> excluded(function.batchName(), excludes));
        }
        if (shard != null) {
            Map<String, Integer> assignment = shardAssignment(functions, shard.count(), measuredWeights);
            functions.removeIf(function -> assignment.get(function.batchName()) != shard.index());
        }
        int removed = before - functions.size();
        if (functions.isEmpty()) {
            throw new IllegalStateException("Фильтр GameTest " + filters + ", исключения " + excludes + ", шард "
                    + shard + " не оставили ни одного теста — прогон не состоится");
        }
        LOGGER.info(
                "GameTest filter {}: удалено {} тестов из {}, осталось {} (исключения {}, шард {})",
                filters,
                removed,
                before,
                functions.size(),
                excludes,
                shard);
    }

    private static boolean excluded(String batch, List<String> excludes) {
        String lower = batch.toLowerCase(Locale.ROOT);
        return excludes.stream().anyMatch(lower::startsWith);
    }

    // Раскладка по шардам — жадный LPT: тяжёлые пачки первыми на самый свободный шард. Вес пачки —
    // замер прошлого прогона (мс, пишет scripts/test.sh), а без замера — сумма timeoutTicks с потолком 100:
    // без потолка тест с окном 24000 весил как сотни коротких, хотя идёт 30 с, а поиск пути на 400 — 12 с.
    // Все серверы считают одинаково, и целая пачка (со своими @BeforeBatch/@AfterBatch) остаётся в одном шарде.
    private static Map<String, Integer> shardAssignment(
            List<TestFunction> functions, int shards, Map<String, Long> measuredWeights) {
        Map<String, Long> fallback = new HashMap<>();
        for (TestFunction function : functions) {
            fallback.merge(function.batchName(), (long) Math.max(1, Math.min(100, function.maxTicks())), Long::sum);
        }
        Map<String, Long> weights = new HashMap<>();
        for (Map.Entry<String, Long> entry : fallback.entrySet()) {
            weights.put(entry.getKey(), Math.max(1, measuredWeights.getOrDefault(entry.getKey(), entry.getValue())));
        }
        List<String> batches = new ArrayList<>(weights.keySet());
        batches.sort((left, right) -> {
            int byWeight = Long.compare(weights.get(right), weights.get(left));
            return byWeight != 0 ? byWeight : left.compareTo(right);
        });
        long[] loads = new long[shards];
        Map<String, Integer> assignment = new HashMap<>();
        for (String batch : batches) {
            int lightest = 0;
            for (int i = 1; i < shards; i++) {
                if (loads[i] < loads[lightest]) {
                    lightest = i;
                }
            }
            assignment.put(batch, lightest);
            loads[lightest] += weights.get(batch);
        }
        return assignment;
    }

    private static Map<String, Long> readWeights() {
        Path path = Path.of(WEIGHTS_FILE);
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Map<String, Long> weights = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                weights.put(
                        line.substring(0, equals).trim(),
                        Long.parseLong(line.substring(equals + 1).trim()));
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException(
                    "Не прочитан " + path.toAbsolutePath() + ": замер пачек для шардов испорчен", e);
        }
        return weights;
    }

    private record Shard(int index, int count) {
        private static Shard parse(String raw) {
            String value = raw.trim();
            if (value.isEmpty()) {
                return null;
            }
            int slash = value.indexOf('/');
            if (slash <= 0) {
                throw new IllegalStateException("Шард GameTest " + value + ": ожидается i/n, например 0/3");
            }
            int index = Integer.parseInt(value.substring(0, slash));
            int count = Integer.parseInt(value.substring(slash + 1));
            if (count < 1 || index < 0 || index >= count) {
                throw new IllegalStateException("Шард GameTest " + value + ": i в 0..n-1, n ≥ 1");
            }
            return new Shard(index, count);
        }
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
                    && classTests
                            .getOrDefault(filter.substring(0, dot), Set.of())
                            .contains(test);
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
                    classTests
                            .computeIfAbsent(simpleName, key -> new HashSet<>())
                            .add(method.getName().toLowerCase(Locale.ROOT));
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.warn("GameTest-класс {} из сканирования не загрузился для фильтра", className, e);
        }
    }
}
