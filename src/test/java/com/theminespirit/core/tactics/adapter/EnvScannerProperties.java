package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class EnvScannerProperties {
    private static final Point SELF = new Point(0.5, 1, 0.5);
    private static final BlockCoord FEET = new BlockCoord(0, 1, 0);

    record Feature(int dx, int dz, int kind, int height) {}

    @Provide
    Arbitrary<List<Feature>> features() {
        Arbitrary<Feature> feature = Combinators.combine(
                        Arbitraries.integers().between(-12, 12),
                        Arbitraries.integers().between(-12, 12),
                        Arbitraries.integers().between(0, 3),
                        Arbitraries.integers().between(1, 3))
                .as(Feature::new);
        return feature.list().ofMaxSize(40);
    }

    private static FakeTerrain build(List<Feature> features) {
        FakeTerrain terrain = FakeTerrain.flat();
        for (Feature f : features) {
            switch (f.kind()) {
                case 0 -> terrain.set(f.dx(), 0, f.dz(), Terrain.WATER);
                case 1 -> terrain.set(f.dx(), 0, f.dz(), Terrain.LAVA);
                case 2 -> terrain.wall(f.dx(), f.dz(), f.height());
                default -> terrain.unloaded(f.dx(), f.dz());
            }
        }
        return terrain;
    }

    @Property(tries = 200)
    void scan_AnyTerrain_ReadsNeverExceedBudget(
            @ForAll("features") List<Feature> features, @ForAll @IntRange(min = 0, max = 200) int budget) {
        FakeTerrain terrain = build(features);
        ReadBudget readBudget = new ReadBudget(budget);

        EnvReading reading =
                new EnvScanner().scan(SELF, FEET, Optional.of(new Point(0.5, 1, -9.5)), terrain, readBudget, 0);

        assertTrue(
                terrain.reads() <= budget,
                "Реальных чтений мира " + terrain.reads() + " при бюджете " + budget
                        + ": предел чтений за цикл держит стоимость тика, превышение множится на число духов");
        assertEquals(readBudget.used(), reading.reads(), "Отчёт о чтениях обязан совпадать со списанным бюджетом");
    }

    @Property(tries = 200)
    void scan_AnyTerrain_FactsStayInEnvFactsRange(@ForAll("features") List<Feature> features) {
        EnvReading reading = new EnvScanner()
                .scan(SELF, FEET, Optional.of(new Point(0.5, 1, -9.5)), build(features), new ReadBudget(5000), 0);

        EnvFacts facts = EnvFactsBuilder.build(reading, SELF, 15, TimeOfDay.DAY, Optional.empty());

        assertTrue(
                facts.waterDistance() <= EnvFacts.FAR_DISTANCE
                        && facts.coverDistance() <= EnvFacts.FAR_DISTANCE
                        && facts.lavaNear() <= EnvFacts.FAR_DISTANCE,
                "Факты окружения обязаны лежать в пределах EnvFacts, иначе ядро бросит исключение в тике духа");
    }

    @Property(tries = 200)
    void scan_SameTerrainTwice_GivesSameFacts(@ForAll("features") List<Feature> features) {
        FakeTerrain terrain = build(features);
        EnvScanner scanner = new EnvScanner();
        Optional<Point> threat = Optional.of(new Point(0.5, 1, -9.5));
        EnvReading first = scanner.scan(SELF, FEET, threat, terrain, new ReadBudget(5000), 0);

        EnvReading second = scanner.scan(SELF, FEET, threat, terrain, new ReadBudget(5000), 10);

        assertEquals(first.water(), second.water(), "Повторный скан той же местности не должен менять цель воды");
        assertEquals(first.cover(), second.cover(), "Повторный скан той же местности не должен менять цель укрытия");
    }
}
