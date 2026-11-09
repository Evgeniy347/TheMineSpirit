package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GraphDomainEvents {
    private GraphDomainEvents() {}

    public static Set<String> of(GraphFacts facts, int stage, GraphLife life) {
        Set<String> events = new LinkedHashSet<>(GraphWorldEvents.of(facts));
        events.addAll(GearInputs.events(facts, stage));
        events.addAll(LifeEvents.events(Set.of(), facts.threats(), List.of(), facts.players(), life));
        return Set.copyOf(events);
    }
}
