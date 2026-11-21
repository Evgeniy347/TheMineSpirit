package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildResultTests {
    @Test
    void toData_CountersAccumulated_ContainsAllFields() {
        BuildResult result = new BuildResult();
        result.placed("cobblestone");
        result.placed("cobblestone");
        result.removed();
        result.skipped();
        result.failed();
        result.supplies(3, 5);

        Map<String, Object> data = result.toData();

        assertEquals(
                2,
                data.get("placed"),
                "Итог обязан накапливать поставленные: иначе REST отдаёт неверное число блоков"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
        assertEquals(
                1,
                data.get("removed"),
                "Итог обязан накапливать убранные: иначе clear_area теряет счёт"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
        assertEquals(
                1,
                data.get("skipped"),
                "Итог обязан накапливать пропуски: иначе стратег не видит дырки"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
        assertEquals(
                1,
                data.get("failed"),
                "Итог обязан накапливать ошибки: иначе провал выглядит успехом"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
        assertEquals(
                5,
                data.get("missing"),
                "Нехватка обязана сохраняться: иначе стратег не узнает, сколько нести"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
        assertEquals(
                "cobblestone",
                data.get("block"),
                "Итог обязан называть блок: иначе стратег не знает, чем строили"
                        + " При смене toData_CountersAccumulated_ContainsAllFields проверь это утверждение.");
    }

    @Test
    void toData_EmptyResult_GivesZerosNotNull() {
        Map<String, Object> data = new BuildResult().toData();

        assertEquals(
                0,
                data.get("placed"),
                "Пустой итог обязан давать нули, а не null: иначе REST падает на отсутствующем поле"
                        + " При смене toData_EmptyResult_GivesZerosNotNull проверь это утверждение.");
        assertEquals(
                0,
                data.get("missing"),
                "Пустой итог обязан давать нулевую нехватку: иначе отказ выглядит нехваткой"
                        + " При смене toData_EmptyResult_GivesZerosNotNull проверь это утверждение.");
        assertEquals(
                "",
                data.get("block"),
                "Пустой итог обязан давать пустой блок: иначе сериализация пишет null"
                        + " При смене toData_EmptyResult_GivesZerosNotNull проверь это утверждение.");
    }
}
