package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CoreFactCollectorTests {
    private static final String ZOMBIE = "minecraft:zombie";

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void collect_Context_ProducesCanonicalSituation(Row row) {
        Situation situation = CoreFactCollector.withDefaultSettings().collect(row.context());
        String first =
                situation.enemies().isEmpty() ? "" : situation.enemies().get(0).type();

        assertEquals(
                row.size(),
                situation.enemies().size(),
                "Ситуация после снятия фактов («" + row.name() + "»): факт-радиус отсекает дальних врагов, "
                        + "а свыше лимита хвост сворачивается — проверьте фильтр CoreFactCollector");
        assertEquals(
                row.firstType(),
                first,
                "Ближайший враг ситуации («" + row.name()
                        + "»): порядок задаёт ядро, факт-радиус только отсекает лишних");
        assertEquals(
                row.mode(),
                situation.orders().mode(),
                "Режим боя из приказов («" + row.name() + "») обязан дойти до ситуации без подмены");
        assertEquals(
                row.night(),
                situation.env().night(),
                "Признак ночи из окружения («" + row.name() + "») обязан дойти до ситуации");
        assertEquals(
                row.sinceHit(),
                situation.ticksSinceHit(),
                "Тики с последнего урона («" + row.name()
                        + "») обязаны дойти до ситуации: на них стоит событийный вызов");
    }

    static Stream<Row> rows() {
        List<EnemyFact> tailTypes = new ArrayList<>(AdapterFixtures.many(ZOMBIE, 16, 1));
        tailTypes.addAll(AdapterFixtures.many(ZOMBIE, 2, 17));
        tailTypes.add(AdapterFixtures.enemy("minecraft:skeleton", "s1", 19, 0));
        List<EnemyFact> unordered = List.of(
                AdapterFixtures.enemy("minecraft:skeleton", "s1", 9, 0),
                AdapterFixtures.enemy(ZOMBIE, "z1", 3, 0),
                AdapterFixtures.enemy("minecraft:creeper", "c1", 5, 0));
        return Stream.of(
                new Row(
                        "нет врагов",
                        context(List.of(), CombatMode.AGGRESSIVE, TimeOfDay.DAY, 0, 0),
                        0,
                        "",
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "один враг в радиусе",
                        context(
                                List.of(AdapterFixtures.enemy(ZOMBIE, "z1", 10, 0)),
                                CombatMode.AGGRESSIVE,
                                TimeOfDay.DAY,
                                0,
                                0),
                        1,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "враг точно на границе радиуса",
                        context(
                                List.of(AdapterFixtures.enemy(ZOMBIE, "z1", AdapterFixtures.RADIUS, 0)),
                                CombatMode.AGGRESSIVE,
                                TimeOfDay.DAY,
                                0,
                                0),
                        1,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "враг за радиусом",
                        context(
                                List.of(AdapterFixtures.enemy(ZOMBIE, "z1", 33, 0)),
                                CombatMode.AGGRESSIVE,
                                TimeOfDay.DAY,
                                0,
                                0),
                        0,
                        "",
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "враг далеко",
                        context(
                                List.of(AdapterFixtures.enemy(ZOMBIE, "z1", 1000, 0)),
                                CombatMode.AGGRESSIVE,
                                TimeOfDay.DAY,
                                0,
                                0),
                        0,
                        "",
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "часть врагов за радиусом",
                        context(
                                List.of(
                                        AdapterFixtures.enemy(ZOMBIE, "z1", 30, 0),
                                        AdapterFixtures.enemy("minecraft:skeleton", "s1", 40, 0)),
                                CombatMode.AGGRESSIVE,
                                TimeOfDay.DAY,
                                0,
                                0),
                        1,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "свыше лимита сворачивается",
                        context(AdapterFixtures.many(ZOMBIE, 20, 1), CombatMode.AGGRESSIVE, TimeOfDay.DAY, 0, 0),
                        Situation.MAX_ENEMIES + 1,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "хвост по типам",
                        context(tailTypes, CombatMode.AGGRESSIVE, TimeOfDay.DAY, 0, 0),
                        Situation.MAX_ENEMIES + 2,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "порядок по дистанции",
                        context(unordered, CombatMode.AGGRESSIVE, TimeOfDay.DAY, 0, 0),
                        3,
                        ZOMBIE,
                        CombatMode.AGGRESSIVE,
                        false,
                        0),
                new Row(
                        "ночь",
                        context(List.of(), CombatMode.AGGRESSIVE, TimeOfDay.NIGHT, 0, 0),
                        0,
                        "",
                        CombatMode.AGGRESSIVE,
                        true,
                        0),
                new Row(
                        "режим protect",
                        context(List.of(), CombatMode.PROTECT, TimeOfDay.DAY, 0, 0),
                        0,
                        "",
                        CombatMode.PROTECT,
                        false,
                        0),
                new Row(
                        "тики с урона и удара",
                        context(List.of(), CombatMode.AGGRESSIVE, TimeOfDay.DAY, 42, 7),
                        0,
                        "",
                        CombatMode.AGGRESSIVE,
                        false,
                        42));
    }

    private static SituationContext context(
            List<EnemyFact> enemies, CombatMode mode, TimeOfDay time, int sinceHit, int sinceStrike) {
        return new SituationContext(
                AdapterFixtures.self(),
                enemies,
                AdapterFixtures.env(time),
                AdapterFixtures.orders(mode),
                sinceHit,
                sinceStrike);
    }

    private record Row(
            String name,
            SituationContext context,
            int size,
            String firstType,
            CombatMode mode,
            boolean night,
            int sinceHit) {
        @Override
        public String toString() {
            return name;
        }
    }
}
