package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticsConfig;
import org.junit.jupiter.api.Test;

class DefenseTuningTests {
    @Test
    void sectors_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                8,
                DefenseTuning.SECTORS,
                "Направления делятся на 8 секторов: иначе посты не накроют круг вокруг защищаемой точки");
    }

    @Test
    void coverCorridor_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                1.5,
                DefenseTuning.COVER_CORRIDOR,
                "Коридор укрытия 1.5 блока: уже — укрытий почти нет, шире — укрытием считается моб мимо линии");
    }

    @Test
    void lowHpFraction_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                0.35,
                DefenseTuning.LOW_HP_FRACTION,
                "Низкое здоровье ниже 0.35: раньше защитник рано выходит из боя, позже — гибнет на месте");
    }

    @Test
    void lowHpFraction_HealThreshold_MatchesIntellect() {
        assertEquals(
                DefenseTuning.LOW_HP_FRACTION,
                IntellectTuning.loadDefault().healFraction(),
                "Порог отхода обороны совпадает с порогом лечения интеллекта: иначе один слой лечит, а второй "
                        + "дерётся тем же духом");
    }

    @Test
    void rushMargin_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                8.0,
                DefenseTuning.RUSH_MARGIN,
                "Радиус ближней реакции 8 блоков: в нём скелет рядом для прикрытия и крипер у самой цели защиты");
    }

    @Test
    void frontRadius_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                3.0,
                DefenseTuning.FRONT_RADIUS,
                "Пост фронтовика в 3 блоках от центра: ближе к угрозе, чем посты прикрытия, но внутри радиуса");
    }

    @Test
    void frontRadius_PostRadius_MatchesCoordinator() {
        assertEquals(
                CoordinatorTuning.DEFENSE_POST_RADIUS,
                DefenseTuning.FRONT_RADIUS,
                "Пост фронтовика совпадает с постом координатора: иначе два слоя ставят духа в разные точки");
    }

    @Test
    void maxEnemies_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                16,
                DefenseTuning.MAX_ENEMIES,
                "Предел обзора 16 врагов как у тактики: иначе пересборка обороны тяжелеет сверх бюджета тика");
    }

    @Test
    void maxEnemies_TacticsLimit_MatchesSituation() {
        assertEquals(
                Situation.MAX_ENEMIES,
                DefenseTuning.MAX_ENEMIES,
                "Предел обзора обороны совпадает с тактическим: иначе слои видят разное число врагов");
    }

    @Test
    void minDps_DeclaredValue_MatchesTuningLiteral() {
        assertEquals(
                0.1,
                DefenseTuning.MIN_DPS,
                "Пол урона 0.1 в силе направления: иначе крипер с нулевым dps не войдёт в вес сектора");
    }

    @Test
    void minDps_TacticsFloor_MatchesScales() {
        assertEquals(
                DefenseTuning.MIN_DPS,
                TacticsConfig.loadDefault().weights().scales().minDps(),
                "Пол урона обороны совпадает с тактическим: иначе сила сектора и запас победы считают по-разному");
    }

    @Test
    void everyConstant_AllValues_PositiveAndFinite() {
        assertTrue(DefenseTuning.SECTORS > 0, "Число секторов положительно: иначе круг не делится на направления");
        assertTrue(
                Double.isFinite(DefenseTuning.COVER_CORRIDOR) && DefenseTuning.COVER_CORRIDOR > 0,
                "Коридор укрытия положителен и конечен: иначе укрытие либо везде, либо нигде");
        assertTrue(
                Double.isFinite(DefenseTuning.LOW_HP_FRACTION)
                        && DefenseTuning.LOW_HP_FRACTION > 0
                        && DefenseTuning.LOW_HP_FRACTION < 1,
                "Порог здоровья — доля в 0..1: иначе раненый либо всегда, либо никогда");
        assertTrue(
                Double.isFinite(DefenseTuning.RUSH_MARGIN) && DefenseTuning.RUSH_MARGIN > 0,
                "Радиус реакции положителен и конечен: иначе рядом нет никого или все");
        assertTrue(
                Double.isFinite(DefenseTuning.FRONT_RADIUS) && DefenseTuning.FRONT_RADIUS > 0,
                "Радиус поста положителен и конечен: иначе пост негде ставить");
        assertTrue(DefenseTuning.MAX_ENEMIES > 0, "Предел обзора положителен: иначе оборона слепа");
        assertTrue(
                Double.isFinite(DefenseTuning.MIN_DPS) && DefenseTuning.MIN_DPS > 0,
                "Пол урона положителен и конечен: нулевой пол вычёркивает крипера из веса сектора");
    }
}
