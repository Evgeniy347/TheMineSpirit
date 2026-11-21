package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.api.CatalogLimits;
import org.junit.jupiter.api.Test;

class CombatSocialLimitsTests {
    @Test
    void socialLimits_ProductionConstants_MatchAgreedValues() {
        assertEquals(
                256,
                CatalogLimits.SAY_LENGTH_MAX,
                "Лимит речи равен 256 символам: длиннее в один пакет чата не влезает, "
                        + "а молчаливая правка лимита изменила бы обрезку сообщений игрокам"
                        + " Иначе тест socialLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене socialLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                20,
                CatalogLimits.SAY_MIN_INTERVAL_TICKS,
                "Анти-спам пропускает речь раз в секунду: чаще — лента реплик забивает чат"
                        + " Иначе тест socialLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене socialLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                64,
                CatalogLimits.SAY_HEARING_RADIUS,
                "Духа слышат в64 блоках: шире — речь слышит весь сервер в отсутствие игрока рядом, "
                        + "уже — игрок в двух блоках рядом теряет реплику"
                        + " Иначе тест socialLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене socialLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
    }

    @Test
    void combatLimits_ProductionConstants_MatchAgreedValues() {
        assertEquals(
                1200,
                CatalogLimits.ATTACK_TIMEOUT_TICKS,
                "attack воюет не дольше 60 секунд: дольше — драка без результата висит в плане стратега"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                1200,
                CatalogLimits.DEFEND_TIMEOUT_TICKS,
                "defend держит оборону ровно 60 секунд: срок согласован с attack, чтобы пара не расходилась"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                60,
                CatalogLimits.DEFEND_SAFE_TICKS,
                "defend считает подопечного в безопасности 3 секунды без угроз: меньше — ранний выход "
                        + "прерывает защиту, больше — дух стоит без дела после отступления врага"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                600,
                CatalogLimits.FLEE_TIMEOUT_TICKS,
                "flee пытается спастись ровно 30 секунд: дольше — бегство без пути висит, раньше — не выходит"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                40,
                CatalogLimits.FLEE_SAFE_TICKS,
                "flee считает себя спасшимся 2 секунды без врагов в радиусе опасности: меньше — ложный успех "
                        + "на полпути, больше — дух не закрывает задачу, уже оторвавшись"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
        assertEquals(
                1200,
                CatalogLimits.RETREAT_TIMEOUT_TICKS,
                "retreat идёт к точке не дольше 60 секунд: дальше — путь неверен, и стратег должен узнать об этом"
                        + " Иначе тест combatLimits_ProductionConstants_MatchAgreedValues перестанет ловить эту поломку. При смене combatLimits_ProductionConstants_MatchAgreedValues проверь это утверждение.");
    }
}
