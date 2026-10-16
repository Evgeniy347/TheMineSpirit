package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CoordinatorStateProperties {
    @Provide
    Arbitrary<List<SpiritSnapshot>> spirits() {
        return spirit().list().ofMinSize(0).ofMaxSize(6);
    }

    private Arbitrary<SpiritSnapshot> spirit() {
        return Arbitraries.integers().between(0, 3).flatMap(threats -> Arbitraries.of(true, false)
                .filter(attacked -> !attacked || threats > 0)
                .map(attacked -> new SpiritSnapshot(UUID.randomUUID(), new Point(threats, 64, threats),
                        threats / 4.0, SpiritRole.WORKER, CombatMode.DEFENSIVE, threats, attacked,
                        Optional.empty(), threats, 1.0)));
    }

    @Property
    void byId_EverySpiritInState_IsFound(@ForAll("spirits") List<SpiritSnapshot> spirits) {
        CoordinatorState state = new CoordinatorState(Optional.empty(), 0, spirits, 0);

        for (SpiritSnapshot spirit : state.spirits()) {
            assertEquals(Optional.of(spirit), state.byId(spirit.id()),
                    "Каждый дух состояния находится по своему id: иначе план не сопоставит решения с духами");
        }
    }
}
