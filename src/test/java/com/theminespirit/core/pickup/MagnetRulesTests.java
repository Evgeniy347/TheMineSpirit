package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MagnetRulesTests {
    private static final MagnetRules RULES = new MagnetRules(5.0, 10, 100);

    private static MagnetRules.Drop free(int age) {
        return new MagnetRules.Drop(age, false, false, false, false, false);
    }

    @Test
    void table_OwnerDecision_RadiusFiveEveryHalfSecond() {
        MagnetRules shipped = MagnetRules.load();

        assertEquals(
                new MagnetRules(5.0, 10, 100),
                shipped,
                "Магнит согласован владельцем 18.10: радиус ~5, «всегда, с таймингами» — раз в 10 тиков; предмет, "
                        + "брошенный игроком, не трогается 100 тиков. Меняются числа в pickup/magnet.json");
    }

    @Test
    void attracts_FreeDrop_IsPulled() {
        assertTrue(RULES.attracts(free(0)), "Свободный дроп (руда, бревно, лут моба) притягивается сразу");
    }

    @Test
    void attracts_YoungPlayerDrop_IsLeftUntilDelay() {
        assertFalse(
                RULES.attracts(new MagnetRules.Drop(99, false, true, false, false, false)),
                "Предмет, брошенный игроком 99 тиков назад, не притягивается: игрок мог бросить его другому");
        assertTrue(
                RULES.attracts(new MagnetRules.Drop(100, false, true, false, false, false)),
                "Через 100 тиков (playerDropDelayTicks) брошенное игроком считается свободным дропом");
    }

    @Test
    void attracts_ForeignOrReservedOrDelayed_IsNeverPulled() {
        assertFalse(
                RULES.attracts(new MagnetRules.Drop(5000, false, false, false, true, false)),
                "Предмет, адресованный другому (give, drop_to_player), чужой: магнит его не берёт");
        assertFalse(
                RULES.attracts(new MagnetRules.Drop(5000, false, false, false, false, true)),
                "Дроп, забронированный другим духом, — его работа: «кто выполняет работу, тот и собирает лут»");
        assertFalse(
                RULES.attracts(new MagnetRules.Drop(5000, true, false, false, false, false)),
                "Предмет с ванильной задержкой подбора не берётся: её ставит игра (только что выпал из сундука)");
    }

    @Test
    void attracts_OwnThrownDrop_IsLeftForLong() {
        assertFalse(
                RULES.attracts(
                        new MagnetRules.Drop(PickupTuning.OWN_DROP_SKIP_TICKS - 1, false, false, true, false, false)),
                "Свой выброшенный при переполнении предмет не притягивается обратно: иначе дух выбрасывал и "
                        + "подбирал бы его по кругу");
        assertTrue(
                RULES.attracts(
                        new MagnetRules.Drop(PickupTuning.OWN_DROP_SKIP_TICKS, false, false, true, false, false)),
                "Свой давно выброшенный предмет снова свободен: задача доски «забрать выкинутое» его подберёт");
    }

    @Test
    void parse_ZeroInterval_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> MagnetRules.parse("{\"radius\":5.0,\"intervalTicks\":0,\"playerDropDelayTicks\":100}", "тест"),
                "Нулевой период магнита обязан отвергаться: проверка каждый тик без смысла дорога");

        assertTrue(error.getMessage().contains("intervalTicks"), "Ошибка называет поле: " + error.getMessage());
    }
}
