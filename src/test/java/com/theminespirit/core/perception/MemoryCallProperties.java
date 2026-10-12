package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MemoryCallProperties {
    private static final String OVERWORLD = "minecraft:overworld";

    @Provide
    Arbitrary<MemoryStore> stores() {
        Arbitrary<MemoryPoint> points = Combinators.combine(
                Arbitraries.integers().between(0, 11).map(index -> "point_" + index),
                Arbitraries.of(MemoryType.class),
                Arbitraries.integers().between(-50, 50),
                Arbitraries.integers().between(-60, 100),
                Arbitraries.integers().between(-50, 50))
                .as((name, type, x, y, z) -> new MemoryPoint(name, type, OVERWORLD, new BlockCoord(x, y, z)));
        return points.list().ofMaxSize(10).map(list -> {
            MemoryStore store = MemoryStore.empty();
            for (MemoryPoint point : list) {
                store = store.remember(point);
            }
            return store;
        });
    }

    @Property
    void recallAll_AnyStore_ReturnsOnlyPointsOfRequestedType(@ForAll("stores") MemoryStore store,
            @ForAll MemoryType type) {
        Finding finding = new MemoryCall.RecallAll(type).apply(store, OVERWORLD).finding();

        assertTrue(finding.sightings().stream().allMatch(sighting -> sighting.kind().equals(type.id())),
                "recall_all по типу " + type.id() + " вернул точку другого типа: " + finding.sightings());
        assertEquals(store.recallAll(type).size(), finding.sightings().size(),
                "recall_all обязан вернуть все точки типа " + type.id() + " без потерь");
    }

    @Property
    void forget_AnyStoreAndName_LeavesNoPointWithThatName(@ForAll("stores") MemoryStore store,
            @ForAll("names") String name) {
        MemoryStore after = new MemoryCall.Forget(name).apply(store, OVERWORLD).store();

        assertNull(after.recall(name), "после forget точка '" + name + "' не должна находиться");
    }

    @Property
    void remember_SameNameTwice_KeepsOnlyLastPoint(@ForAll("stores") MemoryStore store, @ForAll("names") String name,
            @ForAll MemoryType first, @ForAll MemoryType second) {
        BlockCoord a = new BlockCoord(1, 2, 3);
        BlockCoord b = new BlockCoord(4, 5, 6);
        MemoryStore afterFirst = new MemoryCall.Remember(name, first, a).apply(store, OVERWORLD).store();
        MemoryStore afterSecond = new MemoryCall.Remember(name, second, b).apply(afterFirst, OVERWORLD).store();

        assertEquals(new MemoryPoint(name, second, OVERWORLD, b), afterSecond.recall(name),
                "повторный remember с тем же именем обязан заменить точку: осталась " + afterSecond.recall(name));
        assertEquals(afterFirst.all().size(), afterSecond.all().size(),
                "замена не меняет число точек: имя однозначно определяет точку");
    }

    @Provide
    Arbitrary<String> names() {
        return Arbitraries.integers().between(0, 11).map(index -> "point_" + index);
    }

    @Property
    void recall_UnknownName_IsEmptyAndStoreUnchanged(@ForAll("stores") MemoryStore store) {
        MemoryOutcome outcome = new MemoryCall.Recall("never_remembered").apply(store, OVERWORLD);

        assertEquals(List.of(), outcome.finding().sightings(), "recall несуществующего имени обязан быть пустым");
        assertEquals(store, outcome.store(), "recall не имеет права менять память");
    }
}
