package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.intellect.defense.TargetClass;
import com.theminespirit.core.processing.ProcessingRules;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class TacticsTablesReloadTests {
    private static final List<String> IDS =
            List.of("tactics", "adapter", "intellect", "inventory_norm", "outside", "priorities", "processing");

    @Test
    void brokenReload_KeepsPreviousVersion_ForEveryTacticsTable() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        for (String id : IDS) {
            TableDefinition<?> definition = definition(id);
            TableHolder<?> holder = copyInto(registry, definition);
            Object before = holder.current();
            TableRegistry.Report report = registry.reload(broken(definition)).stream()
                    .filter(entry -> id.equals(entry.id()))
                    .findFirst()
                    .orElseThrow();

            assertTrue(
                    report.failed(),
                    id + ": битый json обязан попасть в отчёт с ошибкой — иначе /reload молча оставит игрока "
                            + "без объяснения, почему число не поменялось");
            assertSame(
                    before,
                    holder.current(),
                    id + ": битый json не подменяет таблицу пустотой — прежняя версия остаётся в силе, иначе дух "
                            + "начнёт работать по умолчанию вместо последней удачной загрузки");
        }
    }

    @Test
    void priorities_ReloadedJson_ConsumerReadsNewClassMapping() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<DefenseProfile> holder = copyTyped(registry, "priorities");
        Supplier<DefenseProfile> original =
                () -> DataTables.INSTANCE.priorities().current();
        DefenseProfile.useSource(holder::current);
        try {
            String changed = "{"
                    + "\"classes\": {\"minecraft:creeper\": \"MELEE\", \"default\": \"MELEE\"},"
                    + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                    + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";
            registry.reload(ContentSource.of(Map.of(
                    DefenseProfile.RESOURCE,
                    new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

            assertEquals(
                    TargetClass.MELEE,
                    DefenseProfile.loadDefault().classify("minecraft:creeper"),
                    "После /reload потребитель обязан видеть новую карту целей: иначе датапак мира не поменяет "
                            + "классификацию врагов без перезапуска");
        } finally {
            DefenseProfile.useSource(original);
        }
    }

    @Test
    void processing_ReloadedJson_ConsumerReadsNewValue() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<ProcessingRules> holder = copyTyped(registry, "processing");
        Supplier<ProcessingRules> original =
                () -> DataTables.INSTANCE.processing().current();
        ProcessingRules.useSource(holder::current);
        try {
            String changed = """
                    {"charcoalTarget": 20, "plankTarget": 32, "stickTarget": 32, "charcoalCount": 8,
                     "cookCount": 4, "craftCount": 8, "baseDemand": 1.0, "demandScale": 8.0, "stationRadius": 5}""";
            registry.reload(ContentSource.of(Map.of(
                    ProcessingRules.RESOURCE,
                    new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

            assertEquals(
                    20,
                    ProcessingRules.loadDefault().charcoalTarget(),
                    "После /reload потребитель обязан видеть новую цель угля: иначе датапак мира не поменяет "
                            + "переработку без перезапуска, а таблица числится горячей ложно");
        } finally {
            ProcessingRules.useSource(original);
        }
    }

    private static TableDefinition<?> definition(String id) {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (id.equals(definition.id())) {
                return definition;
            }
        }
        throw new AssertionError("таблица " + id + " не зарегистрирована в DataTables: /reload её не перечитает");
    }

    private static ContentSource broken(TableDefinition<?> definition) {
        Map<String, LoadedContent> content = new LinkedHashMap<>();
        for (String path : definition.resourcePaths()) {
            content.put(path, new LoadedContent("{ ", "broken datapack", TableStatus.Source.DATAPACK));
        }
        return ContentSource.of(content);
    }

    private static TableHolder<?> copyInto(TableRegistry registry, TableDefinition<?> definition) {
        return copy(registry, definition);
    }

    @SuppressWarnings("unchecked")
    private static <T> TableHolder<T> copyTyped(TableRegistry registry, String id) {
        return (TableHolder<T>) copyInto(registry, definition(id));
    }

    private static <T> TableHolder<T> copy(TableRegistry registry, TableDefinition<T> definition) {
        TableHolder<?> live = DataTables.INSTANCE
                .registry()
                .holder(definition.id())
                .orElseThrow(() -> new AssertionError("у таблицы " + definition.id() + " нет держателя"));
        @SuppressWarnings("unchecked")
        T seed = (T) live.current();
        return registry.register(definition, seed);
    }
}
