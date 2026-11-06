package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.OreKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MineVeinParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        MineVeinParams params = MineVeinParams.from(Map.of("ore", "iron", "count", 3, "radius", 8));

        assertEquals(
                new MineVeinParams(OreKind.IRON, 3, 8),
                params,
                "mine_vein обязан сохранить вид руды, число и радиус: по ним адаптер ищет и добывает жилу");
    }

    @Test
    void from_MissingOre_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("count", 1, "radius", 16)),
                "mine_vein без ore обязан отказывать: валидатор плана подставляет умолчание any, "
                        + "а его отсутствие означает, что значение не прошло валидатор");
    }

    @Test
    void from_UnknownOre_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "mithril", "count", 1, "radius", 16)),
                "Неизвестный вид руды обязан отвергаться: иначе скрипт молча ищет несуществующую жилу");

        assertTrue(
                error.getMessage().contains("mithril"),
                "Отказ обязан называть неизвестный вид 'mithril'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "radius", 16)),
                "mine_vein без count обязан отказывать: валидатор плана подставляет умолчание, "
                        + "а его отсутствие означает, что значение не прошло валидатор");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "count", 1L, "radius", 16)),
                "маппинг обязан принимать только Integer: валидатор плана приводит INT к Integer, "
                        + "а long означает, что значение не прошло валидатор");
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "count", 0, "radius", 16)),
                "count меньше 1 обязан отвергаться: нулевая жила — бессмысленная задача");
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "count", 257, "radius", 16)),
                "count больше 256 обязан отвергаться: чрезмерный заказ превращает добычу в долгий цикл");
    }

    @Test
    void from_RadiusBelowRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "count", 1, "radius", 0)),
                "radius меньше 1 обязан отвергаться: нулевой радиус не ищет ни одного блока");
    }

    @Test
    void from_RadiusAboveRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MineVeinParams.from(Map.of("ore", "iron", "count", 1, "radius", 33)),
                "radius больше 32 обязан отвергаться: лимит ограничивает поиск и нагрузку на серверный тик");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> MineVeinParams.from(null),
                "MineVeinParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
