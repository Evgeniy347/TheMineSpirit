package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        CraftParams params = CraftParams.from(Map.of("item", "wooden_pickaxe", "count", 2, "radius", 16));

        assertEquals(new CraftParams("wooden_pickaxe", 2, 16), params,
                "craft обязан сохранить предмет, число и радиус: по ним адаптер выбирает рецепт и ищет верстак");
    }

    @Test
    void from_MissingItem_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("count", 1, "radius", 16)),
                "craft без item обязан отказывать: без предмета скрипт не знает, какой рецепт искать");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", " ", "count", 1, "radius", 16)),
                "Пустой предмет обязан отвергаться: пробельный id не совпадёт ни с одним рецептом");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "radius", 16)),
                "craft без count обязан отказывать: число предметов определяет число крафтов и расход материалов");
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "count", 0, "radius", 16)),
                "count меньше 1 обязан отвергаться: ноль крафтов — цель, которая выглядит исполненной");
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "count", 257, "radius", 16)),
                "count больше 256 обязан отвергаться: лимит каталога ограничивает длину работы духа");
    }

    @Test
    void from_RadiusBelowRange_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "count", 1, "radius", 0)),
                "radius меньше 1 обязан отвергаться: нулевой радиус не найдёт даже верстак под ногами");
    }

    @Test
    void from_RadiusAboveRange_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "count", 1, "radius", 33)),
                "radius больше 32 обязан отвергаться: лимит ограничивает поиск станции и нагрузку на серверный тик");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CraftParams.from(Map.of("item", "stick", "count", 1L, "radius", 16)),
                "маппинг обязан принимать только Integer: long означает, что значение не прошло валидатор плана");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> CraftParams.from(null),
                "CraftParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
