package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DropRouteProperties {
    @Provide
    Arbitrary<List<DropRef>> drops() {
        Arbitrary<Integer> coordinate = Arbitraries.integers().between(-8, 8);
        Arbitrary<DropRef> drop = Combinators.combine(Arbitraries.integers().between(0, 30), coordinate, coordinate,
                coordinate, Arbitraries.of(true, true, false))
                .as((n, x, y, z, pickable) -> new DropRef("drop-" + n, "minecraft:stick", 1, x, y, z, pickable));
        return drop.list().ofMaxSize(15).map(list -> {
            Set<String> seen = new HashSet<>();
            List<DropRef> unique = new ArrayList<>();
            for (DropRef item : list) {
                if (seen.add(item.id())) {
                    unique.add(item);
                }
            }
            return unique;
        });
    }

    @Property
    void next_AnyDrops_ResultIsMinimumDistanceAmongAllowed(@ForAll("drops") List<DropRef> drops,
            @ForAll("drops") List<DropRef> skippedSource) {
        Set<String> skipped = new HashSet<>();
        skippedSource.forEach(item -> skipped.add(item.id()));

        Optional<DropRef> chosen = DropRoute.next(0, 0, 0, drops, skipped);

        List<DropRef> allowed = drops.stream().filter(d -> d.pickable() && !skipped.contains(d.id())).toList();
        assertEquals(allowed.isEmpty(), chosen.isEmpty(),
                "Маршрут пуст тогда и только тогда, когда нет допустимых кучек: допустимых " + allowed.size());
        chosen.ifPresent(best -> {
            assertTrue(allowed.contains(best), "Выбрана недопустимая кучка " + best);
            for (DropRef other : allowed) {
                assertTrue(distanceSquared(best) <= distanceSquared(other),
                        "Кучка " + other + " ближе выбранной " + best + ": дух теряет время на лишний путь");
            }
        });
    }

    @Property
    void next_ShuffledDrops_GiveSameResult(@ForAll("drops") List<DropRef> drops, @ForAll long seed) {
        List<DropRef> shuffled = new ArrayList<>(drops);
        Collections.shuffle(shuffled, new Random(seed));

        assertEquals(DropRoute.next(0, 0, 0, drops, Set.of()), DropRoute.next(0, 0, 0, shuffled, Set.of()),
                "Порядок списка кучек не должен влиять на выбор: сущности приходят из мира в произвольном порядке");
    }

    private static double distanceSquared(DropRef drop) {
        return drop.x() * drop.x() + drop.y() * drop.y() + drop.z() * drop.z();
    }
}
