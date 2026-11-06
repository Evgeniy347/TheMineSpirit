package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModeGeometryTests {
    private static final Modes MODES = TacticsConfig.loadDefault().modes();

    private static EnemyFact at(double x, double z) {
        return new EnemyFact("minecraft:zombie", "z" + x, new Point(x, 0, z), 1, 0, true, true);
    }

    @Test
    void of_DefensiveWithFightStartAnchor_MeasuresFromAnchorNotFromSpirit() {
        Situation situation = SituationBuilder.create()
                .mode(CombatMode.DEFENSIVE)
                .pos(10, 0)
                .enemy(at(14, 0))
                .build();

        ModeGeometry geometry =
                ModeGeometry.of(situation, MODES.of(CombatMode.DEFENSIVE), Optional.of(new Point(0, 0, 0)));

        assertEquals(
                List.of(new Point(0, 0, 0), -6.0, -8.0),
                List.of(geometry.anchor(), geometry.leashLeft(), geometry.engageLeft()),
                "Расстояния меряются от точки начала боя (0,0), а не от самого духа: цель в 14 блоках от якоря "
                        + "за поводком 8 на 6, за радиусом реакции 6 на 8");
    }

    @Test
    void of_GuardAreaWithZone_UsesZoneCenterAndZoneRadius() {
        Situation situation = SituationBuilder.create()
                .mode(CombatMode.GUARD_AREA)
                .pos(15, 0)
                .zone(20, 0, 10)
                .enemy(at(28, 0))
                .build();

        ModeGeometry geometry = ModeGeometry.of(situation, MODES.of(CombatMode.GUARD_AREA), Optional.empty());

        assertEquals(
                List.of(new Point(20, 0, 0), 5.0, 6.0, 2.0),
                List.of(geometry.anchor(), geometry.zoneDistance(), geometry.leashLeft(), geometry.engageLeft()),
                "Охрана зоны: якорь — центр, расстояние до зоны 5, враг в 8 блоках от центра внутри поводка 14 и зоны 10");
    }

    @Test
    void of_ProtectWithWard_AnchorsToWard() {
        Situation situation = SituationBuilder.create()
                .mode(CombatMode.PROTECT)
                .ward(3, 0, true)
                .enemy(at(20, 0))
                .build();

        ModeGeometry geometry = ModeGeometry.of(situation, MODES.of(CombatMode.PROTECT), Optional.empty());

        assertEquals(
                List.of(new Point(3, 0, 0), -5.0, -1.0),
                List.of(geometry.anchor(), geometry.leashLeft(), geometry.engageLeft()),
                "Защита: якорь — подопечный, враг в 17 блоках от него вне реакции 16 и поводка 12");
    }

    @Test
    void of_NoEnemies_LeashAndEngageAreUnbounded() {
        Situation situation =
                SituationBuilder.create().mode(CombatMode.DEFENSIVE).build();

        ModeGeometry geometry = ModeGeometry.of(situation, MODES.of(CombatMode.DEFENSIVE), Optional.empty());

        assertEquals(
                List.of(Assessment.UNBOUNDED, Assessment.UNBOUNDED),
                List.of(geometry.leashLeft(), geometry.engageLeft()),
                "Без врагов ограничивать нечего: поводок и реакция не ограничены, иначе правило поводка сработало бы вхолостую");
    }

    @Test
    void of_SeveralEnemies_UsesTheBestReachableOne() {
        Situation situation = SituationBuilder.create()
                .mode(CombatMode.DEFENSIVE)
                .enemy(at(3, 0))
                .enemy(at(30, 0))
                .build();

        ModeGeometry geometry = ModeGeometry.of(situation, MODES.of(CombatMode.DEFENSIVE), Optional.empty());

        assertEquals(
                List.of(5.0, 3.0),
                List.of(geometry.leashLeft(), geometry.engageLeft()),
                "Достаточно одного врага внутри поводка: дальний не отменяет бой с ближним");
    }

    @Test
    void of_ZoneNotSet_ZoneDistanceIsZero() {
        Situation situation =
                SituationBuilder.create().mode(CombatMode.GUARD_AREA).build();

        assertEquals(
                0.0,
                ModeGeometry.of(situation, MODES.of(CombatMode.GUARD_AREA), Optional.empty())
                        .zoneDistance(),
                0.0,
                "Без зоны расстояние до неё 0, а режим сам вернётся в defensive по таблице переходов");
    }
}
