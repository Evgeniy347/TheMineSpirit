package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TargetHandoffProperties {
    private static final EnemyFact CREEPER = new EnemyFact("minecraft:creeper",
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", new Point(10, 64, 0), 1.0, 0, true, true);

    @Provide
    Arbitrary<List<DefenderCapability>> allies() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(-14, 14).map(x -> new Point(x, 64, -x)),
                Arbitraries.doubles().between(0.0, 1.0), Arbitraries.integers().between(0, 20),
                Arbitraries.of(true, false), Arbitraries.doubles().between(0.0, 12.0),
                Arbitraries.of(true, false))
                .as((id, pos, hp, armor, bow, dps, heal) -> new DefenderCapability(id, pos, hp, armor, bow, dps, heal))
                .list().ofMinSize(0).ofMaxSize(4).map(TargetHandoffProperties::distinctIds);
    }

    @Property
    void handOff_AnyInput_NotSelfNorBusy(@ForAll("allies") List<DefenderCapability> allies,
            @ForAll boolean selfBusy) {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Set<UUID> busy = allies.stream().filter(ally -> selfBusy).map(DefenderCapability::id)
                .collect(Collectors.toSet());

        Optional<UUID> next = TargetHandoff.handOff(self, CREEPER, allies, busy, DefenseProfileTests.PROFILE);

        next.ifPresent(id -> assertTrue(!id.equals(self) && !busy.contains(id),
                "Приёмник — не уходящий и не занятый: иначе цель уйдёт духу, который её не поведёт"));
    }

    @Property
    void handOff_CreeperTarget_ArcherTakesOver(@ForAll("allies") List<DefenderCapability> allies) {
        Optional<UUID> next =
                TargetHandoff.handOff(UUID.fromString("00000000-0000-0000-0000-000000000000"), CREEPER, allies,
                        Set.of(), DefenseProfileTests.PROFILE);

        if (next.isPresent()) {
            DefenderCapability taker = allies.stream().filter(ally -> ally.id().equals(next.orElseThrow()))
                    .findFirst().orElseThrow();
            assertTrue(taker.bow(),
                    "Крипера принимает лучник: иначе подмена подойдёт к взрыву вместо обстрела");
        }
    }

    @Property
    void handOff_AnyInput_Deterministic(@ForAll("allies") List<DefenderCapability> allies) {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000000000");

        assertEquals(TargetHandoff.handOff(self, CREEPER, allies, Set.of(), DefenseProfileTests.PROFILE),
                TargetHandoff.handOff(self, CREEPER, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Передача детерминирована: иначе цель мечется между приёмниками");
    }

    @Property
    void handOff_ShuffledAllies_SameTaker(@ForAll("allies") List<DefenderCapability> allies) {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<DefenderCapability> shuffled = new ArrayList<>(allies);
        Collections.shuffle(shuffled, new Random(19));

        assertEquals(TargetHandoff.handOff(self, CREEPER, allies, Set.of(), DefenseProfileTests.PROFILE),
                TargetHandoff.handOff(self, CREEPER, shuffled, Set.of(), DefenseProfileTests.PROFILE),
                "Приёмник не зависит от порядка входа: иначе подмена мигает при каждом новом снимке");
    }

    private static List<DefenderCapability> distinctIds(List<DefenderCapability> allies) {
        return allies.stream()
                .collect(Collectors.toMap(DefenderCapability::id, item -> item, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(DefenderCapability::id)).toList();
    }
}
