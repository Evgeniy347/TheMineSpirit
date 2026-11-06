package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DropTable;
import java.time.Clock;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MiningTablesReloadTests {
    @Test
    void descent_ReloadedJson_ConsumerReadsNewTargetY() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<DescentData> holder =
                registry.registerLazy(descentDefinition(DataTables.INSTANCE.registry()), DescentData::load);
        Supplier<DescentData> original = () -> DataTables.INSTANCE.descent().current();
        DescentData.useSource(holder::current);
        try {
            String changed = """
                    {"targetY": -40, "torchInterval": 4}""";
            registry.reload(ContentSource.of(Map.of(
                    DescentData.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

            assertEquals(
                    -40,
                    DescentData.loadDefault().targetY(),
                    "После /reload потребитель обязан видеть новый целевой этаж: иначе датапак мира не поменяет "
                            + "спуск без перезапуска, а таблица числится горячей ложно");
        } finally {
            DescentData.useSource(original);
        }
    }

    @Test
    void descentLevels_ReloadedJson_ConsumerReadsNewWidenWidth() {
        TableRegistry registry = new TableRegistry(Clock.systemUTC());
        TableHolder<DescentLevels> holder =
                registry.registerLazy(descentLevelsDefinition(DataTables.INSTANCE.registry()), DescentLevels::load);
        Supplier<DescentLevels> original =
                () -> DataTables.INSTANCE.descentLevels().current();
        DescentLevels.useSource(holder::current);
        try {
            String changed = """
                    {"widenWidth": 5, "widenHeight": 6, "stairWidth": 2}""";
            registry.reload(ContentSource.of(Map.of(
                    DescentLevels.RESOURCE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

            assertEquals(
                    5,
                    DescentLevels.loadDefault().widenWidth(),
                    "После /reload потребитель обязан видеть новую ширину расширения: иначе датапак мира не "
                            + "поменяет шахту без перезапуска, а таблица числится горячей ложно");
        } finally {
            DescentLevels.useSource(original);
        }
    }

    @Test
    void inventoryAndLootTables_LiveReload_ReadBoundHolders() {
        assertSame(
                DataTables.INSTANCE.warehouseCategories().current(),
                WarehouseCategories.current(),
                "Раскладка склада обязана читать держатель: иначе /reload не поменяет категории без перезапуска");
        assertSame(
                DataTables.INSTANCE.kitFor().current(),
                KitFor.load(),
                "Наборы по деятельностям обязаны читать держатель: иначе /reload не поменяет их без перезапуска");
        assertSame(
                DataTables.INSTANCE.drops().current(),
                DropTable.shared(),
                "Таблица дропов обязана читать держатель: иначе /reload не поменяет добычу без перезапуска");
        assertSame(
                DataTables.INSTANCE.descent().current(),
                DescentData.loadDefault(),
                "Спуск обязан читать держатель: иначе /reload не поменяет целей этаж без перезапуска");
        assertSame(
                DataTables.INSTANCE.descentLevels().current(),
                DescentLevels.loadDefault(),
                "Уровни спуска обязаны читать держатель: иначе /reload не поменяет ширину шахты без перезапуска");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<DescentData> descentDefinition(TableRegistry registry) {
        for (TableDefinition<?> definition : registry.definitions()) {
            if ("descent".equals(definition.id())) {
                return (TableDefinition<DescentData>) definition;
            }
        }
        throw new AssertionError("таблица descent не зарегистрирована в DataTables: /reload её не перечитает");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<DescentLevels> descentLevelsDefinition(TableRegistry registry) {
        for (TableDefinition<?> definition : registry.definitions()) {
            if ("descent_levels".equals(definition.id())) {
                return (TableDefinition<DescentLevels>) definition;
            }
        }
        throw new AssertionError("таблица descent_levels не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
