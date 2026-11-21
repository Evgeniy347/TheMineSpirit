package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryNormTableTests {
    private static final String VALID = """
            {"demand":{"occupied_curve":[{"at":0.0,"value":0.0},{"at":1.0,"value":2.0}],
            "extra_curve":[{"at":0.0,"value":0.0},{"at":1.0,"value":4.0}]},
            "release_free_slots":{"CHOP":0,"FARM":2,"FORAGE":2,"MINE":0,"REST":0,"DEFEND":0,"UNLOAD":0,"SMELT":2,"CRAFT":2}}
            """;

    @Test
    void parse_ValidJson_BuildsCurvesAndThresholds() {
        InventoryNormTable table = InventoryNormTable.parse(VALID, "тест");

        assertEquals(
                List.of(new InventoryNormTable.CurvePoint(0.0, 0.0), new InventoryNormTable.CurvePoint(1.0, 2.0)),
                table.occupiedCurve(),
                "Кривые занятости обязаны разобраться из файла: иначе данные не управляют спросом"
                        + " При смене parse_ValidJson_BuildsCurvesAndThresholds проверь это утверждение.");
        assertEquals(
                0,
                table.releaseFreeSlots(ActivityId.UNLOAD),
                "Порог освобождения читается по деятельностям: иначе дух не знает, когда бросить задачу"
                        + " При смене parse_ValidJson_BuildsCurvesAndThresholds проверь это утверждение.");
        assertEquals(
                2,
                table.releaseFreeSlots(ActivityId.FARM),
                "Ферма из файла бросает задачу при двух свободных ячейках: порог — данные, а не константа"
                        + " Иначе тест parse_ValidJson_BuildsCurvesAndThresholds перестанет ловить эту поломку. При смене parse_ValidJson_BuildsCurvesAndThresholds проверь это утверждение.");
        assertEquals(
                0,
                table.releaseFreeSlots(ActivityId.MINE),
                "Шахтёр из файла держит задачу до последней ячейки (порог 0): иначе он бросает жилу раньше времени"
                        + " При смене parse_ValidJson_BuildsCurvesAndThresholds проверь это утверждение.");
    }

    @Test
    void load_ShippedFile_MatchesOwnerDecision() {
        InventoryNormTable table = InventoryNormTable.load();

        assertEquals(
                0,
                table.releaseFreeSlots(ActivityId.MINE),
                "Шахтёр держит задачу до последней ячейки"
                        + " Иначе тест load_ShippedFile_MatchesOwnerDecision перестанет ловить эту поломку. При смене load_ShippedFile_MatchesOwnerDecision проверь это утверждение.");
        assertEquals(
                0,
                table.releaseFreeSlots(ActivityId.CHOP),
                "Рубка держится так же, как шахта"
                        + " Иначе тест load_ShippedFile_MatchesOwnerDecision перестанет ловить эту поломку. При смене load_ShippedFile_MatchesOwnerDecision проверь это утверждение.");
        assertEquals(
                2,
                table.releaseFreeSlots(ActivityId.FARM),
                "Ферма бросает задачу раньше: порог 2"
                        + " Иначе тест load_ShippedFile_MatchesOwnerDecision перестанет ловить эту поломку. При смене load_ShippedFile_MatchesOwnerDecision проверь это утверждение.");
        assertEquals(
                2,
                table.releaseFreeSlots(ActivityId.FORAGE),
                "Сбор бросает задачу раньше: порог 2"
                        + " Иначе тест load_ShippedFile_MatchesOwnerDecision перестанет ловить эту поломку. При смене load_ShippedFile_MatchesOwnerDecision проверь это утверждение.");
    }

    @Test
    void parse_BrokenJson_ThrowsWithOrigin() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> InventoryNormTable.parse("{ это не json", "битый.json"),
                "Битый json обязан падать: иначе таблица молча подменится пустой"
                        + " При смене parse_BrokenJson_ThrowsWithOrigin проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("битый.json"),
                "Сообщение называет файл: при падении видно, что править"
                        + " Иначе тест parse_BrokenJson_ThrowsWithOrigin перестанет ловить эту поломку. При смене parse_BrokenJson_ThrowsWithOrigin проверь это утверждение.");
    }

    @Test
    void parse_FallingDemand_Throws() {
        String falling = VALID.replace(
                "[{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":4.0}]",
                "[{\"at\":0.0,\"value\":0.0},{\"at\":0.5,\"value\":3.0},{\"at\":1.0,\"value\":2.0}]");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> InventoryNormTable.parse(falling, "падение.json"),
                "Кривая с падающим спросом недопустима: чем полнее инвентарь, тем сильнее нужда его освободить"
                        + " Иначе тест parse_FallingDemand_Throws перестанет ловить эту поломку. При смене parse_FallingDemand_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("extra_curve"),
                "Сообщение называет кривую: при падении видно, какую точку чинить"
                        + " Иначе тест parse_FallingDemand_Throws перестанет ловить эту поломку. При смене parse_FallingDemand_Throws проверь это утверждение.");
    }

    @Test
    void parse_UnorderedPoints_Throws() {
        String unordered = VALID.replace(
                "[{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":2.0}]",
                "[{\"at\":0.0,\"value\":0.0},{\"at\":0.5,\"value\":1.0},{\"at\":0.5,\"value\":2.0}]");

        assertThrows(
                IllegalStateException.class,
                () -> InventoryNormTable.parse(unordered, "порядок.json"),
                "Немонотонные доли точек недопустимы: интерполяция кривой требует роста at"
                        + " Иначе тест parse_UnorderedPoints_Throws перестанет ловить эту поломку. При смене parse_UnorderedPoints_Throws проверь это утверждение.");
    }

    @Test
    void parse_MissingActivity_Throws() {
        String missing = VALID.replace("\"FARM\":2,", "");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> InventoryNormTable.parse(missing, "нет фермы.json"),
                "Без порога деятельности таблица неполна: дух не узнает, до какой ячейки держать задачу"
                        + " Иначе тест parse_MissingActivity_Throws перестанет ловить эту поломку. При смене parse_MissingActivity_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("FARM"),
                "Сообщение называет деятельность: при падении видно, чего не хватает"
                        + " Иначе тест parse_MissingActivity_Throws перестанет ловить эту поломку. При смене parse_MissingActivity_Throws проверь это утверждение.");
    }

    @Test
    void parse_EmptyCurve_Throws() {
        String empty = VALID.replace("{\"at\":0.0,\"value\":0.0},{\"at\":1.0,\"value\":2.0}", "");

        assertThrows(
                IllegalStateException.class,
                () -> InventoryNormTable.parse(empty, "пусто.json"),
                "Пустая кривая недопустима: без точек спрос не посчитать"
                        + " Иначе тест parse_EmptyCurve_Throws перестанет ловить эту поломку. При смене parse_EmptyCurve_Throws проверь это утверждение.");
    }
}
