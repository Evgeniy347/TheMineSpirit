package com.theminespirit.core.board.graph;

import java.util.LinkedHashSet;
import java.util.Set;

public final class GraphWorldEvents {
    public static final String DEMOLISH_STRAY = "demolish_stray";
    public static final String BACKFILL = "backfill";
    public static final String CLEAR_FLOATING = "clear_floating";
    public static final String LEVEL_LOT = "level_lot";
    public static final String RESTORE_TRACE = "restore_trace";
    public static final String REMOVE_ROUTE = "remove_route";
    public static final String UNDO_LEASE = "undo_lease";
    public static final String REPAIR = "repair_";

    private GraphWorldEvents() {}

    public static Set<String> of(GraphFacts facts) {
        Set<String> events = new LinkedHashSet<>();
        if (!facts.strays().isEmpty()) {
            events.add(DEMOLISH_STRAY);
        }
        GraphSiteFacts.Terrain terrain = facts.terrain();
        if (terrain.pits() > 0 || !terrain.fill().isEmpty()) {
            events.add(BACKFILL);
        }
        if (terrain.hanging() > 0 || !terrain.floating().isEmpty()) {
            events.add(CLEAR_FLOATING);
        }
        if (terrain.heightDrop() > 0 || !terrain.cut().isEmpty()) {
            events.add(LEVEL_LOT);
        }
        if (terrain.traces() > 0) {
            events.add(RESTORE_TRACE);
        }
        if (!facts.leases().isEmpty()) {
            events.add(UNDO_LEASE);
        }
        facts.buildings().values().stream()
                .filter(GraphWorldEvents::repairNeeded)
                .map(building -> REPAIR + building.id())
                .forEach(events::add);
        return Set.copyOf(events);
    }

    static boolean repairNeeded(GraphFacts.Building building) {
        if (building.unknown()) {
            return false;
        }
        return building.damage() > 0
                || building.wrong() > 0
                || (building.auditPercent() > 0 && building.auditPercent() < 100);
    }
}
