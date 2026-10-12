package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class MemoryStoreProperties {
    private static final int MAX_OPERATIONS = 16;

    record Operation(boolean forget, MemoryPoint point) {
    }

    @Provide
    Arbitrary<MemoryPoint> points() {
        Arbitrary<String> name = Arbitraries.of("a", "b", "c", "d", "e", "f");
        Arbitrary<BlockCoord> pos = Combinators.combine(Arbitraries.integers().between(-50, 50),
                Arbitraries.integers().between(0, 100), Arbitraries.integers().between(-50, 50)).as(BlockCoord::new);
        return Combinators.combine(name, Arbitraries.of(MemoryType.class),
                Arbitraries.of("minecraft:overworld", "minecraft:the_nether"), pos).as(MemoryPoint::new);
    }

    @Provide
    Arbitrary<List<Operation>> operations() {
        return Combinators.combine(Arbitraries.of(true, false, false), points()).as(Operation::new).list()
                .ofMaxSize(MAX_OPERATIONS);
    }

    private static MemoryStore apply(List<Operation> operations) {
        MemoryStore store = MemoryStore.empty();
        for (Operation operation : operations) {
            store = operation.forget() ? store.forget(operation.point().name()) : store.remember(operation.point());
        }
        return store;
    }

    @Property
    void operations_AnySequence_MatchSimpleMapModel(@ForAll("operations") List<Operation> operations) {
        Map<String, MemoryPoint> model = new LinkedHashMap<>();
        for (Operation operation : operations) {
            if (operation.forget()) {
                model.remove(operation.point().name());
            } else {
                model.put(operation.point().name(), operation.point());
            }
        }

        assertEquals(new ArrayList<>(model.values()), apply(operations).all(),
                "память после remember/forget обязана совпадать с простой моделью «имя -> точка»: повтор имени "
                        + "заменяет точку на месте, forget убирает");
    }

    @Property
    void recallAll_AnyStoreAndType_ReturnsOnlyThatTypeAndTypesPartitionTheMemory(
            @ForAll("operations") List<Operation> operations) {
        MemoryStore store = apply(operations);

        int total = 0;
        for (MemoryType type : MemoryType.values()) {
            List<MemoryPoint> ofType = store.recallAll(type);
            assertTrue(ofType.stream().allMatch(point -> point.type() == type),
                    "recallAll(" + type.id() + ") вернул точку другого типа");
            total += ofType.size();
        }
        assertEquals(store.all().size(), total, "точки трёх типов вместе обязаны давать всю память без потерь и повторов");
    }

    @Property
    void forget_AnyStoreAndKnownName_PointIsGoneAndOthersUntouched(@ForAll("operations") List<Operation> operations,
            @ForAll("points") MemoryPoint victim) {
        MemoryStore store = apply(operations).remember(victim);

        MemoryStore after = store.forget(victim.name());

        assertNull(after.recall(victim.name()), "после forget точка " + victim.name() + " не должна находиться");
        List<MemoryPoint> expected = new ArrayList<>(store.all());
        expected.removeIf(point -> point.name().equals(victim.name()));
        assertEquals(expected, after.all(), "forget не должен менять остальные точки и их порядок");
    }
}
