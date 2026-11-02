package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Set;

public final class GraphDomainEvents {
    private GraphDomainEvents() {}

    public static Set<String> of(GraphFacts facts, int stage) {
        Set<String> events = new LinkedHashSet<>(GraphWorldEvents.of(facts));
        events.addAll(StoreInputs.events(facts));
        events.addAll(GearInputs.events(facts, stage));
        return Set.copyOf(events);
    }
}
