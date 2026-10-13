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

        assertEquals(2, data.get("placed"),
                "Итог обязан накапливать поставленные: иначе REST отдаёт неверное число блоков");
        assertEquals(1, data.get("removed"),
                "Итог обязан накапливать убранные: иначе clear_area теряет счёт");
        assertEquals(1, data.get("skipped"),
                "Итог обязан накапливать пропуски: иначе стратег не видит дырки");
        assertEquals(1, data.get("failed"),
                "Итог обязан накапливать ошибки: иначе провал выглядит успехом");
        assertEquals(5, data.get("missing"),
                "Нехватка обязана сохраняться: иначе стратег не узнает, сколько нести");
        assertEquals("cobblestone", data.get("block"),
                "Итог обязан называть блок: иначе стратег не знает, чем строили");
    }

    @Test
    void toData_EmptyResult_GivesZerosNotNull() {
        Map<String, Object> data = new BuildResult().toData();

        assertEquals(0, data.get("placed"),
                "Пустой итог обязан давать нули, а не null: иначе REST падает на отсутствующем поле");
        assertEquals(0, data.get("missing"),
                "Пустой итог обязан давать нулевую нехватку: иначе отказ выглядит нехваткой");
        assertEquals("", data.get("block"),
                "Пустой итог обязан давать пустой блок: иначе сериализация пишет null");
    }
}
