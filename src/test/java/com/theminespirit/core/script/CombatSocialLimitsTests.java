package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatSocialLimitsTests {
    @Test
    void socialLimits_ProductionConstants_MatchAgreedValues() {
        assertEquals(256, CatalogLimits.SAY_LENGTH_MAX,
                "Лимит речи равен 256 символам: длиннее в один пакет чата не влезает, "
                        + "а молчаливая правка лимита изменила бы обрезку сообщений игрокам");
        assertEquals(20, CatalogLimits.SAY_MIN_INTERVAL_TICKS,
                "Анти-спам пропускает речь раз в секунду: чаще — лента реплик забивает чат");
        assertEquals(1200, CatalogLimits.MEET_TIMEOUT_TICKS,
                "meet ждёт прихода ровно 60 секунд: дольше — дух стоит впустую, раньше — не успевает дойти");
        assertEquals(1200, CatalogLimits.DELIVER_TIMEOUT_TICKS,
                "deliver ждёт доставки ровно 60 секунд: дольше — висячая задача, раньше — не доносит груз");
    }

    @Test
    void combatLimits_ProductionConstants_MatchAgreedValues() {
        assertEquals(1200, CatalogLimits.ATTACK_TIMEOUT_TICKS,
                "attack воюет не дольше 60 секунд: дольше — драка без результата висит в плане стратега");
        assertEquals(1200, CatalogLimits.DEFEND_TIMEOUT_TICKS,
                "defend держит оборону ровно 60 секунд: срок согласован с attack, чтобы пара не расходилась");
        assertEquals(60, CatalogLimits.DEFEND_SAFE_TICKS,
                "defend считает подопечного в безопасности 3 секунды без угроз: меньше — ранний выход "
                        + "прерывает защиту, больше — дух стоит без дела после отступления врага");
        assertEquals(600, CatalogLimits.FLEE_TIMEOUT_TICKS,
                "flee пытается спастись ровно 30 секунд: дольше — бегство без пути висит, раньше — не выходит");
        assertEquals(40, CatalogLimits.FLEE_SAFE_TICKS,
                "flee считает себя спасшимся 2 секунды без врагов в радиусе опасности: меньше — ложный успех "
                        + "на полпути, больше — дух не закрывает задачу, уже оторвавшись");
        assertEquals(1200, CatalogLimits.RETREAT_TIMEOUT_TICKS,
                "retreat идёт к точке не дольше 60 секунд: дальше — путь неверен, и стратег должен узнать об этом");
    }
}
