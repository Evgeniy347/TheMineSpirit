package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntellectTuningTests {
    @Test
    void demandInterval_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(100, IntellectTuning.DEMAND_INTERVAL_TICKS,
                "Пересборка спроса раз в 100 тиков: чаще — лишние чтения запасов, реже — поздно видна нужда");
    }

    @Test
    void minSwitch_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(200, IntellectTuning.MIN_SWITCH_TICKS,
                "Окно удержания 200 тиков: дольше — дух застревает на устаревшем занятии, короче — дёрганье");
    }

    @Test
    void hysteresisMargin_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(0.15, IntellectTuning.HYSTERESIS_MARGIN,
                "Порог переключения 0.15 спроса: меньше — задачи дёргаются, больше — дух игнорирует нужду");
    }

    @Test
    void crowdPenalty_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(0.2, IntellectTuning.CROWD_PENALTY,
                "Штраф за толпу 0.2 на духа: мягко разводит духов по деятельностям, не запрещая работу вместе");
    }

    @Test
    void maxDemand_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(10.0, IntellectTuning.MAX_DEMAND,
                "Шкала спроса 0..10: в ней считаются коэффициенты деятельностей и штраф за толпу");
    }

    @Test
    void fleeMargin_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(1.0, IntellectTuning.FLEE_MARGIN,
                "Порог отхода: бой при запасе победы не ниже 1 − FLEE_MARGIN, иначе дух гибнет в проигрышном бою");
    }

    @Test
    void foodNorm_DeclaredValues_MatchTuningLiterals() {
        assertEquals(1, IntellectTuning.FOOD_DAYS,
                "Норма еды считается на сутки: иначе ферма копит или голодает не в срок");
        assertEquals(1.5, IntellectTuning.FOOD_SAFETY,
                "Запас еды с коэффициентом 1.5: иначе неурожай застанет поселение без еды");
    }

    @Test
    void stockTargets_DeclaredValues_MatchTuningLiterals() {
        assertEquals(64, IntellectTuning.ORE_TARGET,
                "Цель руды 64: иначе добыча остановится раньше стаКа");
        assertEquals(32, IntellectTuning.IRON_TARGET,
                "Цель железа 32: иначе не хватит на инструменты и броню");
        assertEquals(2, IntellectTuning.WEAPON_TARGET,
                "Цель оружия 2: иначе экспедиция ходит без охраны");
    }

    @Test
    void teamLimits_DeclaredValues_MatchTuningLiterals() {
        assertEquals(2, IntellectTuning.TEAM_MIN,
                "Экспедиция минимум двое: в одиночку в шахту не ходят");
        assertEquals(3, IntellectTuning.TEAM_MAX,
                "Экспедиция максимум трое: иначе шахта превратится в толпу");
    }

    @Test
    void inviteTimings_DeclaredValues_MatchTuningLiterals() {
        assertEquals(3600, IntellectTuning.INVITE_TTL_TICKS,
                "Вопрос висит 3 минуты: короче — игрок не успеет, дольше — духи стоят без дела");
        assertEquals(600, IntellectTuning.INVITE_REPEAT_TICKS,
                "Повтор каждые 30 секунд: чаще — спам, реже — игрок забудет про вопрос");
        assertEquals(1200, IntellectTuning.INVITE_EXTEND_TICKS,
                "Продление на 60 секунд: иначе собирающийся игрок не успеет");
    }

    @Test
    void strategicTtl_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(6000, IntellectTuning.STRATEGIC_TTL_TICKS,
                "Задание стратегического интеллекта живёт 5 минут: покрывает его цикл 1–5 минут с запасом");
    }

    @Test
    void combatThresholds_DeclaredValues_MatchTuningLiterals() {
        assertEquals(0.35, IntellectTuning.HEAL_FRACTION,
                "Лечение ниже 0.35 здоровья: иначе дух дерётся до смерти вместо отхода за лечением");
        assertEquals(6.0, IntellectTuning.ARMED_DPS,
                "Вооружённый дух бьёт на 6: иначе оценка сил недооценит защитника с мечом");
        assertEquals(3, IntellectTuning.MAX_FIGHTERS,
                "Бойцов не больше трёх: иначе оборона оголяет поселение ради толпы у одного врага");
    }

    @Test
    void everyConstant_AllValues_PositiveAndFinite() {
        assertTrue(IntellectTuning.DEMAND_INTERVAL_TICKS > 0, "Интервал пересборки положителен");
        assertTrue(IntellectTuning.MIN_SWITCH_TICKS > 0, "Окно удержания положительно");
        assertTrue(Double.isFinite(IntellectTuning.HYSTERESIS_MARGIN) && IntellectTuning.HYSTERESIS_MARGIN > 0,
                "Порог переключения положителен и конечен");
        assertTrue(Double.isFinite(IntellectTuning.CROWD_PENALTY) && IntellectTuning.CROWD_PENALTY > 0,
                "Штраф за толпу положителен и конечен");
        assertTrue(Double.isFinite(IntellectTuning.MAX_DEMAND) && IntellectTuning.MAX_DEMAND > 0,
                "Шкала спроса положительна и конечна");
        assertTrue(Double.isFinite(IntellectTuning.FLEE_MARGIN) && IntellectTuning.FLEE_MARGIN > 0,
                "Порог отхода положителен и конечен");
        assertTrue(IntellectTuning.ORE_TARGET > 0 && IntellectTuning.IRON_TARGET > 0
                && IntellectTuning.WEAPON_TARGET > 0, "Цели запасов положительны");
        assertTrue(IntellectTuning.TEAM_MIN > 0 && IntellectTuning.TEAM_MAX >= IntellectTuning.TEAM_MIN,
                "Пределы команды положительны и упорядочены");
        assertTrue(IntellectTuning.INVITE_TTL_TICKS > 0 && IntellectTuning.INVITE_REPEAT_TICKS > 0
                && IntellectTuning.INVITE_EXTEND_TICKS > 0, "Тайминги приглашения положительны");
        assertTrue(IntellectTuning.STRATEGIC_TTL_TICKS > 0, "Срок задания стратега положителен");
        assertTrue(Double.isFinite(IntellectTuning.HEAL_FRACTION) && IntellectTuning.HEAL_FRACTION > 0
                && IntellectTuning.HEAL_FRACTION < 1, "Порог лечения — доля в 0..1");
        assertTrue(Double.isFinite(IntellectTuning.ARMED_DPS) && IntellectTuning.ARMED_DPS > 1,
                "Урон вооружённого выше голой руки: иначе поход за оружием бессмысленен");
    }
}
