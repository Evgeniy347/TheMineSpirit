package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.Situation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CoreFactCollectorProperties {
    private static final Point ORIGIN = new Point(0, 0, 0);

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        Arbitrary<EnemyFact> enemy = Combinators.combine(
                Arbitraries.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider"),
                Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 50),
                Arbitraries.integers().between(0, 4), Arbitraries.integers().between(1, 3), Arbitraries.of(true, false))
                .as((type, id, distance, hpStep, count, visible) -> new EnemyFact(type, "id" + id,
                        new Point(distance, 0, 0), hpStep / 4.0, 0, visible, !visible, count));
        return enemy.list().ofMinSize(0).ofMaxSize(40);
    }

    @Property(tries = 200)
    void collect_AnyEnemies_KeepsOnlyThoseInFactRadius(@ForAll("enemies") List<EnemyFact> supplied) {
        SituationContext context = AdapterFixtures.context(supplied);
        Situation expected = new Situation(context.self(),
                supplied.stream().filter(enemy -> enemy.pos().distanceTo(ORIGIN) <= AdapterFixtures.RADIUS).toList(),
                context.env(), context.orders(), context.ticksSinceHit(), context.ticksSinceStrike());

        Situation actual = CoreFactCollector.withDefaultSettings().collect(context);

        assertEquals(expected, actual,
                "Снятие фактов обязано оставить ровно врагов в факт-радиусе " + AdapterFixtures.RADIUS
                        + " и отдать их ядру: иначе решение принимается по невидимым врагам или теряет видимых");
    }

    @Property(tries = 200)
    void collect_AnyEnemies_ReturnsCanonicalOrder(@ForAll("enemies") List<EnemyFact> supplied) {
        List<EnemyFact> actual = CoreFactCollector.withDefaultSettings().collect(AdapterFixtures.context(supplied))
                .enemies();
        List<EnemyFact> sorted = new ArrayList<>(actual);
        sorted.sort(canonicalOrder(ORIGIN));

        assertEquals(sorted, actual,
                "Список врагов ситуации обязан идти по (дистанция, тип, HP-доля, id): порядок находки сущностей "
                        + "не должен протекать в решение");
    }

    @Property(tries = 200)
    void collect_MoreThanMax_PreservesCountWithinBounds(@ForAll("enemies") List<EnemyFact> supplied) {
        Situation situation = CoreFactCollector.withDefaultSettings().collect(AdapterFixtures.context(supplied));
        List<EnemyFact> inRange = supplied.stream()
                .filter(enemy -> enemy.pos().distanceTo(ORIGIN) <= AdapterFixtures.RADIUS).toList();
        int expectedCount = inRange.stream().mapToInt(EnemyFact::count).sum();
        long distinctTypes = inRange.stream().map(EnemyFact::type).distinct().count();

        assertEquals(expectedCount, situation.enemies().stream().mapToInt(EnemyFact::count).sum(),
                "Сворачивание хвоста сверх " + Situation.MAX_ENEMIES + " сохраняет число врагов: орда не должна "
                        + "пропадать из вида");
        assertTrue(situation.enemies().size() <= Situation.MAX_ENEMIES + distinctTypes,
                "Сверх лимита хвост сворачивается в одну запись на тип, значит записей не больше "
                        + Situation.MAX_ENEMIES + " + число типов = " + (Situation.MAX_ENEMIES + distinctTypes));
    }

    private static Comparator<EnemyFact> canonicalOrder(Point origin) {
        return Comparator.<EnemyFact>comparingDouble(enemy -> enemy.pos().distanceTo(origin))
                .thenComparing(EnemyFact::type).thenComparingDouble(EnemyFact::hpFraction).thenComparing(EnemyFact::id)
                .thenComparingDouble(enemy -> enemy.pos().x()).thenComparingDouble(enemy -> enemy.pos().y())
                .thenComparingDouble(enemy -> enemy.pos().z()).thenComparingDouble(EnemyFact::armor)
                .thenComparing(EnemyFact::visible).thenComparing(EnemyFact::targetingSelf)
                .thenComparingInt(EnemyFact::count);
    }
}
