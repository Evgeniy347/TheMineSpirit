package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.TownPlan;
import java.util.LinkedHashMap;
import java.util.Map;

final class PlotLots {
    private PlotLots() {}

    static Map<String, Boolean> of(SettlementScan.Strays found, TownPlan plan) {
        Map<String, Boolean> lots = new LinkedHashMap<>();
        plan.placements()
                .forEach(place -> StageCatalog.graphId(place.id()).ifPresent(id -> {
                    boolean ready = found.pits().keySet().stream().noneMatch(at -> place.covers(at.x(), at.z()))
                            && found.mounds().stream().noneMatch(at -> place.covers(at.x(), at.z()));
                    lots.merge(id, ready, (first, second) -> first && second);
                }));
        return Map.copyOf(lots);
    }

    static Map<String, GraphFacts.Building> ready(
            Map<String, GraphFacts.Building> buildings, Map<String, Boolean> lots) {
        Map<String, GraphFacts.Building> ready = new LinkedHashMap<>();
        buildings.forEach((id, building) -> ready.put(
                id,
                new GraphFacts.Building(
                        id,
                        building.level(),
                        building.auditPercent(),
                        building.damage(),
                        building.missing(),
                        building.extra(),
                        building.wrong(),
                        building.position(),
                        building.unknown(),
                        lots.getOrDefault(id, building.lotReady()))));
        return Map.copyOf(ready);
    }
}
