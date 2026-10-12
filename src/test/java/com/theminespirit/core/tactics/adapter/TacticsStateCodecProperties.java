package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DecisionSource;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalState;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TacticsStateCodecProperties {
    @Provide
    Arbitrary<Point> points() {
        return Combinators.combine(Arbitraries.doubles().between(-1000, 1000), Arbitraries.doubles().between(-60, 300),
                Arbitraries.doubles().between(-1000, 1000)).as(Point::new);
    }

    record Head(Optional<TacticalOption> previous, DecisionSource source, long decidedAt, TacticalState state,
            long stateSince) {
    }

    record Tail(CombatMode mode, CombatMode ordered, Optional<Point> anchor, long lastSeen, double hpRef, long hpStart) {
    }

    @Provide
    Arbitrary<TacticsMemory> memories() {
        Arbitrary<Head> head = Combinators.combine(Arbitraries.of(TacticalOption.values()).optional(),
                Arbitraries.of(DecisionSource.values()), Arbitraries.longs().between(0, 1_000_000),
                Arbitraries.of(TacticalState.values()), Arbitraries.longs().between(0, 1_000_000)).as(Head::new);
        Arbitrary<Tail> tail = Combinators.combine(Arbitraries.of(CombatMode.values()),
                Arbitraries.of(CombatMode.values()), points().optional(),
                Arbitraries.longs().between(-1_000_000, 1_000_000), Arbitraries.doubles().between(0, 1),
                Arbitraries.longs().between(0, 1_000_000)).as(Tail::new);
        return Combinators.combine(head, tail).as((h, t) -> new TacticsMemory(h.previous(), h.source(), h.decidedAt(),
                h.state(), h.stateSince(), t.mode(), t.ordered(), t.anchor(), t.lastSeen(), t.hpRef(), t.hpStart()));
    }

    @Provide
    Arbitrary<StoredTactics> stored() {
        Arbitrary<Optional<OrdersBuilder.Zone>> zone = Combinators
                .combine(points(), Arbitraries.doubles().between(1, 256)).as(OrdersBuilder.Zone::new).optional();
        Arbitrary<RetreatTracker> retreat = Combinators
                .combine(Arbitraries.of(RetreatMethod.values()), Arbitraries.longs().between(1, 1_000_000))
                .as((method, since) -> method == RetreatMethod.NONE ? RetreatTracker.IDLE : new RetreatTracker(method, since));
        return Combinators.combine(Arbitraries.of(CombatMode.values()), zone,
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(36).optional(), memories().optional(), retreat)
                .as(StoredTactics::new);
    }

    @Property(tries = 200)
    void decode_AfterEncode_GivesSameState(@ForAll("stored") StoredTactics stored) {
        StoredTactics restored = TacticsStateCodec.decode(TacticsStateCodec.encode(stored));

        assertEquals(stored, restored, "Любое сохранённое состояние тактики восстанавливается равным себе");
    }
}
