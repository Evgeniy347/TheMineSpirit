package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.ContentSource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableDefinition;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableRegistry;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryNormTableTests {
    private static final String VALID = """
            {"demand":{"occupied_curve":[{"at":0.0,"value":0.0},{"at":1.0,"value":2.0}],
            "extra_curve":[{"at":0.0,"value":0.0},{"at":1.0,"value":4.0}]},
            "release_free_slots":{"CHOP":0,"FARM":2,"FORAGE":2,"MINE":0,"REST":0,"DEFEND":0,"UNLOAD":0}}
            """;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void parse_ValidJson_BuildsCurvesAndThresholds() {
        InventoryNormTable table = InventoryNormTable.parse(VALID, "тест");

        assertEquals(List.of(new InventoryNormTable.CurvePoint(0.0, 0.0),
                        new InventoryNormTable.CurvePoint(1.0, 2.0)), table.occupiedCurve(),
                "Кривые занятости обязаны разобраться из файла: иначе данные не управляют спросом");
        assertEquals(0, table.releaseFreeSlots(ActivityId.UNLOAD),
                "Порог освобождения читается по деятельностям: иначе дух не знает, когда бросить задачу");
        assertEquals(2, table.releaseFreeSlots(ActivityId.FARM),
                "Ферма из файла бросает задачу при двух свободных ячейках: порог — данные, а не константа");
        assertEquals(0, table.releaseFreeSlots(ActivityId.MINE),
                "Шахтёр из файла держит задачу до последней ячейки (порог 0): иначе он бросает жилу раньше времени");
    }

    @Test
    void load_ShippedFile_MatchesOwnerDecision() {
        InventoryNormTable table = InventoryNormTable.load();

        assertEquals(0, table.releaseFreeSlots(ActivityId.MINE), "Шахтёр держит задачу до последней ячейки");
        assertEquals(0, table.releaseFreeSlots(ActivityId.CHOP), "Рубка держится так же, как шахта");
        assertEquals(2, table.releaseFreeSlots(ActivityId.FARM), "Ферма бросает задачу раньше: порог 2");
        assertEquals(2, table.releaseFreeSlots(ActivityId.FORAGE), "Сбор бросает задачу раньше: порог 2");
    }

    @Test
    void parse_BrokenJson_ThrowsWithOrigin() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> InventoryNormTable.parse("{ это не json", "битый.json"),
                "Битый json обязан падать: иначе таблица молча подменится пустой");
        assertTrue(error.getMessage().contains("битый.json"),
                "Сообщение называет файл: при падении видно, что править");
    }

    @Test
    void parse_FallingDemand_Throws() {
        String falling = VALID.replace("[{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":4.0}]",
                "[{\"at\":0.0,\"value\":0.0},{\"at\":0.5,\"value\":3.0},{\"at\":1.0,\"value\":2.0}]");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> InventoryNormTable.parse(falling, "падение.json"),
                "Кривая с падающим спросом недопустима: чем полнее инвентарь, тем сильнее нужда его освободить");

        assertTrue(error.getMessage().contains("extra_curve"),
                "Сообщение называет кривую: при падении видно, какую точку чинить");
    }

    @Test
    void parse_UnorderedPoints_Throws() {
        String unordered = VALID.replace("[{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":2.0}]",
                "[{\"at\":0.0,\"value\":0.0},{\"at\":0.5,\"value\":1.0},{\"at\":0.5,\"value\":2.0}]");

        assertThrows(IllegalStateException.class, () -> InventoryNormTable.parse(unordered, "порядок.json"),
                "Немонотонные доли точек недопустимы: интерполяция кривой требует роста at");
    }

    @Test
    void parse_MissingActivity_Throws() {
        String missing = VALID.replace("\"FARM\":2,", "");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> InventoryNormTable.parse(missing, "нет фермы.json"),
                "Без порога деятельности таблица неполна: дух не узнает, до какой ячейки держать задачу");

        assertTrue(error.getMessage().contains("FARM"),
                "Сообщение называет деятельность: при падении видно, чего не хватает");
    }

    @Test
    void parse_EmptyCurve_Throws() {
        String empty = VALID.replace("{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":2.0}", "");

        assertThrows(IllegalStateException.class, () -> InventoryNormTable.parse(empty, "пусто.json"),
                "Пустая кривая недопустима: без точек спрос не посчитать");
    }

    @Test
    void reload_BrokenContent_KeepsOldTableAndReportsError() {
        TableRegistry registry = new TableRegistry(CLOCK);
        TableDefinition<InventoryNormTable> definition = registeredDefinition("inventory_norm");
        TableHolder<InventoryNormTable> holder = registry.register(definition, InventoryNormTable.load());
        InventoryNormTable before = holder.current();
        ContentSource source = ContentSource.of(Map.of(InventoryNormTable.RESOURCE,
                new LoadedContent("{ сломано", InventoryNormTable.RESOURCE, TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertSame(before, holder.current(),
                "Битый json при /reload обязан оставить прежнюю таблицу в силе: иначе духи теряют пороги "
                        + "освобождения и бросают задачи по пустым числам");
        assertTrue(holder.status().failed(), "Ошибка загрузки обязана быть видна в статусе таблицы");
    }

    @Test
    void loadDefault_ConsumerCall_ReturnsLiveHolder() {
        assertSame(DataTables.INSTANCE.inventoryNorm().current(), InventoryNormTable.loadDefault(),
                "Потребитель обязан брать версию из держателя: иначе /reload не поменяет ему числа");
    }

    @SuppressWarnings("unchecked")
    private static TableDefinition<InventoryNormTable> registeredDefinition(String id) {
        for (TableDefinition<?> definition : DataTables.INSTANCE.registry().definitions()) {
            if (definition.id().equals(id)) {
                return (TableDefinition<InventoryNormTable>) definition;
            }
        }
        throw new AssertionError("таблица " + id + " не зарегистрирована в DataTables: /reload её не перечитает");
    }
}
