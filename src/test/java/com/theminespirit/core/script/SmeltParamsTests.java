package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SmeltParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        SmeltParams params = SmeltParams.from(Map.of("item", "iron_ingot", "count", 2, "radius", 16));

        assertEquals(
                new SmeltParams("iron_ingot", 2, 16),
                params,
                "smelt обязан сохранить предмет, число и радиус: по ним адаптер выбирает рецепт плавки и ищет печь");
    }

    @Test
    void from_MissingItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("count", 1, "radius", 16)),
                "smelt без item обязан отказывать: без предмета скрипт не знает, какой продукт получать");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", " ", "count", 1, "radius", 16)),
                "Пустой предмет обязан отвергаться: пробельный id не совпадёт ни с одним рецептом");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "radius", 16)),
                "smelt без count обязан отказывать: число предметов определяет расход входа и топлива");
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "count", 0, "radius", 16)),
                "count меньше 1 обязан отвергаться: ноль предметов — цель, которая выглядит исполненной");
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "count", 257, "radius", 16)),
                "count больше 256 обязан отвергаться: лимит каталога ограничивает длину работы духа");
    }

    @Test
    void from_RadiusBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "count", 1, "radius", 0)),
                "radius меньше 1 обязан отвергаться: нулевой радиус не найдёт даже печь под ногами");
    }

    @Test
    void from_RadiusAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "count", 1, "radius", 33)),
                "radius больше 32 обязан отвергаться: лимит ограничивает поиск печи и нагрузку на серверный тик");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SmeltParams.from(Map.of("item", "iron_ingot", "count", 1L, "radius", 16)),
                "маппинг обязан принимать только Integer: long означает, что значение не прошло валидатор плана");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> SmeltParams.from(null),
                "SmeltParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
