package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SituationCanonicalTests {
    @Test
    void enemies_GivenUnordered_AreSortedByDistanceThenType() {
        Situation situation = SituationBuilder.create()
                .enemy("skeleton", 9).enemy("zombie", 3).enemy("creeper", 3).enemy("spider", 1).build();

        List<String> order = situation.enemies().stream().map(EnemyFact::type).toList();

        assertEquals(List.of("minecraft:spider", "minecraft:creeper", "minecraft:zombie", "minecraft:skeleton"), order,
                "Порядок врагов в Situation каноничен (дистанция, потом тип): иначе то же поле боя давало бы разные решения");
    }

    @Test
    void enemies_SameDistanceAndType_AreOrderedByHpFractionThenId() {
        EnemyFact hurt = new EnemyFact("minecraft:zombie", "b", new Point(4, 0, 0), 0.3, 0, true, true);
        EnemyFact healthyA = new EnemyFact("minecraft:zombie", "a", new Point(4, 0, 0), 1, 0, true, true);
        EnemyFact healthyB = new EnemyFact("minecraft:zombie", "c", new Point(4, 0, 0), 1, 0, true, true);

        Situation situation = SituationBuilder.create().enemy(healthyB).enemy(healthyA).enemy(hurt).build();

        assertEquals(List.of("b", "a", "c"), situation.enemies().stream().map(EnemyFact::id).toList(),
                "При равной дистанции и типе раньше идёт раненый, затем по id: тай-брейк обязан быть полным");
    }

    @Test
    void enemies_MoreThanMax_TailIsFoldedIntoOneAggregatePerType() {
        SituationBuilder builder = SituationBuilder.create();
        for (int i = 0; i < Situation.MAX_ENEMIES; i++) {
            builder.enemy("zombie", 1 + i);
        }
        builder.enemies("zombie", 30, 3).enemy("skeleton", 31);

        List<EnemyFact> enemies = builder.build().enemies();

        assertEquals(Situation.MAX_ENEMIES + 2, enemies.size(),
                "Хвост сверх MAX_ENEMIES сворачивается в по одной записи на тип (здесь зомби и скелет)");
        EnemyFact zombieTail = enemies.get(Situation.MAX_ENEMIES);
        assertEquals(List.of("minecraft:zombie", 3), List.of(zombieTail.type(), zombieTail.count()),
                "Агрегат «ещё n зомби» хранит число свёрнутых врагов, иначе суммарный урон потеряется");
    }

    @Test
    void enemies_AggregateOfDifferentHp_KeepsWeightedHpAndFlags() {
        SituationBuilder builder = SituationBuilder.create();
        for (int i = 0; i < Situation.MAX_ENEMIES; i++) {
            builder.enemy("zombie", 1 + i);
        }
        builder.enemy(new EnemyFact("minecraft:husk", "h1", new Point(40, 0, 0), 1, 0, false, false))
                .enemy(new EnemyFact("minecraft:husk", "h2", new Point(41, 0, 0), 0.5, 10, true, false, 3));

        EnemyFact aggregate = builder.build().enemies().get(Situation.MAX_ENEMIES);

        assertEquals(List.of(4, 0.625, 7.5, true, false, "h1"),
                List.of(aggregate.count(), aggregate.hpFraction(), aggregate.armor(), aggregate.visible(),
                        aggregate.targetingSelf(), aggregate.id()),
                "Агрегат: число складывается, HP и броня — средневзвешенные по числу, видимость — «хоть один», "
                        + "id и позиция — ближайшего из свёрнутых");
    }

    @Test
    void enemies_ListIsCopied_LaterChangeOfSourceDoesNotAffectSituation() {
        List<EnemyFact> source = new ArrayList<>();
        source.add(new EnemyFact("minecraft:zombie", "a", new Point(3, 0, 0), 1, 0, true, true));
        SituationBuilder builder = SituationBuilder.create();
        Situation situation = builder.enemy(source.get(0)).build();

        source.clear();

        assertEquals(1, situation.enemies().size(),
                "Situation неизменяема: список врагов копируется, иначе адаптер мог бы поменять факты задним числом");
    }
}
