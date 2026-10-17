package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverflowTuningTests {

    @Test
    void parse_FromClasspath_ReadsStockScaleAndFloor() {
        OverflowTuning tuning = OverflowTuning.load();

        assertEquals(64, tuning.stockScale(),
                "Масштаб запаса из таблицы равен 64: при таком количестве на складе ценность падает "
                        + "вдвое, иначе «чем больше на базе, тем ниже ценность» не наступает");
        assertEquals(0.25, tuning.minFactor(),
                "Нижний коэффициент равен 0.25: склад не обнуляет ценность предмета полностью");
    }

    @Test
    void stockFactor_GrowsStock_FallsAndStopsAtFloor() {
        OverflowTuning tuning = new OverflowTuning("тест", 64, 0.25);

        assertEquals(1.0, tuning.stockFactor(0),
                "Пустой склад не снижает ценность: предмета на базе нет, он редок");
        assertTrue(tuning.stockFactor(64) < tuning.stockFactor(0),
                "При 64 предметах на складе ценность ниже, чем при нуле: дух оставляет в инвентаре "
                        + "то, чего на базе мало");
        assertTrue(tuning.stockFactor(128) < tuning.stockFactor(64),
                "Чем больше на складе, тем ниже ценность: 128 дешевле 64");
        assertEquals(0.25, tuning.stockFactor(1_000_000),
                "Ценность не падает ниже нижнего коэффициента таблицы");
    }

    @Test
    void effectiveUnitValue_LowBaseValueWithBigStock_BecomesZero() {
        OverflowTuning tuning = new OverflowTuning("тест", 8, 0.1);

        assertEquals(0, tuning.effectiveUnitValue(1, 1_000_000),
                "Единичная ценность при огромном складе округляется вниз до нуля: именно так мусор "
                        + "становится самым дешёвым для выброса");
    }

    @Test
    void effectiveStackValue_AnyStack_CountsEveryItem() {
        OverflowTuning tuning = new OverflowTuning("тест", 64, 0.25);
        ValueTable values = ValueTable.load();
        Stack stack = new Stack("iron_ingot", 0, 8, 64);

        assertEquals(tuning.effectiveUnitValue(values.valueOf("iron_ingot"), 64) * 8,
                tuning.effectiveStackValue(values, stack, 64),
                "Ценность стопки — цена штуки с поправкой на склад, умноженная на количество");
    }

    @Test
    void parse_BrokenCoefficients_ThrowsNamingFile() {
        assertThrows(IllegalStateException.class, () -> OverflowTuning.parse(
                "{\"stock_scale\":0,\"min_factor\":0.25}", "overflow.json"),
                "Нулевой масштаб обязан падать: деление на ноль сломало бы ценность любого предмета");
        assertThrows(IllegalStateException.class, () -> OverflowTuning.parse(
                "{\"stock_scale\":64,\"min_factor\":2}", "overflow.json"),
                "Коэффициент больше 1 обязан падать: склад не может повышать ценность");
        assertThrows(IllegalStateException.class, () -> OverflowTuning.parse(
                "{\"stock_scale\":64}", "overflow.json"),
                "Файл без min_factor обязан падать: молчаливое значение по умолчанию скрыло бы "
                        + "потерю коэффициента");
    }
}
