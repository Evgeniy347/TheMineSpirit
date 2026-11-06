package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RetreatPlannerTests {
    private static final Point SELF = new Point(0.5, 1, 0.5);
    private static final BlockCoord FEET = new BlockCoord(0, 1, 0);
    private static final EnemyFact ZOMBIE_EAST =
            new EnemyFact("minecraft:zombie", "z", new Point(5.5, 1, 0.5), 1, 0, true, true);
    private static final EnvReading WATER_AND_COVER = new EnvReading(
            EnvFacts.FAR_DISTANCE,
            0,
            Optional.of(new BlockCoord(-4, 0, 3)),
            5.0,
            Optional.of(new BlockCoord(-2, 1, -3)),
            3.6,
            10,
            false);

    static RetreatPlanner.Input input(EnvReading env, Optional<Point> home, int blocks, EnemyFact... enemies) {
        return input(env, home, blocks, false, enemies);
    }

    static RetreatPlanner.Input input(
            EnvReading env, Optional<Point> home, int blocks, boolean bucket, EnemyFact... enemies) {
        return new RetreatPlanner.Input(SELF, FEET, List.of(enemies), env, home, blocks, Optional.empty(), bucket);
    }

    static RetreatPlanner.Input rallyInput(Optional<Point> rally, EnemyFact... enemies) {
        return new RetreatPlanner.Input(
                SELF, FEET, List.of(enemies), EnvReading.NOTHING, Optional.empty(), 0, rally, false);
    }

    record Row(String name, RetreatMethod method, RetreatPlanner.Input input, Class<? extends RetreatPlan> kind) {}

    static Stream<Row> rows() {
        Optional<Point> home = Optional.of(new Point(-8.5, 1, 0.5));
        return Stream.of(
                new Row(
                        "HOME с домом в памяти",
                        RetreatMethod.HOME,
                        input(EnvReading.NOTHING, home, 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "HOME без дома",
                        RetreatMethod.HOME,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        null),
                new Row(
                        "COVER с найденным укрытием",
                        RetreatMethod.COVER,
                        input(WATER_AND_COVER, Optional.empty(), 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "COVER без укрытия",
                        RetreatMethod.COVER,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        null),
                new Row(
                        "WATER с найденной водой",
                        RetreatMethod.WATER,
                        input(WATER_AND_COVER, Optional.empty(), 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "WATER без воды",
                        RetreatMethod.WATER,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        null),
                new Row(
                        "WATER без воды, есть ведро",
                        RetreatMethod.WATER,
                        input(EnvReading.NOTHING, Optional.empty(), 0, true, ZOMBIE_EAST),
                        RetreatPlan.Pour.class),
                new Row(
                        "WATER без воды и ведра, есть дом",
                        RetreatMethod.WATER,
                        input(EnvReading.NOTHING, home, 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "WATER без воды, ведра и дома",
                        RetreatMethod.WATER,
                        input(EnvReading.NOTHING, Optional.empty(), 0, false, ZOMBIE_EAST),
                        null),
                new Row(
                        "KITE: бежать и стрелять",
                        RetreatMethod.KITE,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "AWAY: бежать прочь",
                        RetreatMethod.AWAY,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "RALLY: точка сбора у союзника",
                        RetreatMethod.RALLY,
                        rallyInput(Optional.of(new Point(-4.5, 1, 0.5)), ZOMBIE_EAST),
                        RetreatPlan.Walk.class),
                new Row(
                        "RALLY без союзника",
                        RetreatMethod.RALLY,
                        input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST),
                        null),
                new Row("NONE: плана нет", RetreatMethod.NONE, input(WATER_AND_COVER, home, 64, ZOMBIE_EAST), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void plan_Method_GivesExpectedKindOfPlan(Row row) {
        Optional<RetreatPlan> plan = RetreatPlanner.plan(row.method(), row.input());

        if (row.kind() == null) {
            assertTrue(
                    plan.isEmpty(),
                    "План отхода («" + row.name() + "») без цели или без блоков не строится: "
                            + "дух не должен бежать в никуда, получили " + plan);
        } else {
            assertTrue(plan.isPresent(), "План отхода («" + row.name() + "») обязан строиться по снятым фактам");
            assertInstanceOf(
                    row.kind(),
                    plan.get(),
                    "Вид плана («" + row.name() + "») определяет исполнитель: " + "бег, постройка стены или столб");
        }
    }

    @Test
    void plan_AwayWithEnemyToTheEast_TargetLiesWestAtAwayDistance() {
        RetreatPlan.Walk walk = (RetreatPlan.Walk)
                RetreatPlanner.plan(RetreatMethod.AWAY, input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST))
                        .orElseThrow();

        assertEquals(
                SELF.x() - RetreatPlanner.AWAY_DISTANCE,
                walk.target().x(),
                1e-6,
                "Бег прочь идёт в сторону, противоположную ближнему врагу, на AWAY_DISTANCE блоков");
        assertEquals(SELF.z(), walk.target().z(), 1e-6, "Враг на одной линии по x: поперёк бежать незачем");
    }

    @Test
    void plan_RallyWithAllyPoint_WalksToThatPoint() {
        Point ally = new Point(-4.5, 1, 3.5);
        RetreatPlan.Walk walk =
                (RetreatPlan.Walk) RetreatPlanner.plan(RetreatMethod.RALLY, rallyInput(Optional.of(ally), ZOMBIE_EAST))
                        .orElseThrow();

        assertEquals(
                ally,
                walk.target(),
                "Точка сбора — сам союзник (ближайший дух или игрок), а не точка «прочь от врага»: "
                        + "иначе дух бежит в пустое поле вместо своих");
        assertEquals(
                RetreatPlanner.RALLY_ARRIVAL,
                walk.arrival(),
                1e-6,
                "К союзнику подходят на дистанцию сбора, а не встают внутрь него");
    }

    @Test
    void plan_RallyWithoutAllyPoint_NoPlan() {
        assertTrue(
                RetreatPlanner.plan(RetreatMethod.RALLY, input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST))
                        .isEmpty(),
                "Без союзника или игрока в радиусе RALLY не строит план: способ не должен подменяться бегом прочь");
    }

    @Test
    void plan_Kite_WalksAwayAndShoots() {
        RetreatPlan.Walk walk = (RetreatPlan.Walk)
                RetreatPlanner.plan(RetreatMethod.KITE, input(EnvReading.NOTHING, Optional.empty(), 0, ZOMBIE_EAST))
                        .orElseThrow();

        assertTrue(walk.shooting(), "KITE отличается от AWAY тем, что дух стреляет на бегу");
    }
}
