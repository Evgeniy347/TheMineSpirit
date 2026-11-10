package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.VitalNeeds;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SpiritVitals {
    private SpiritVitals() {}

    static Map<String, String> needs(SpiritEntity spirit) {
        Map<String, String> needs = new LinkedHashMap<>();
        VitalNeeds.Need hunger = VitalNeeds.byId("hunger").orElseThrow();
        if (GraphFood.hungry(List.of(spirit))) {
            needs.put(hunger.id(), hunger.blocked());
        }
        VitalNeeds.Need injury = VitalNeeds.byId("injury").orElseThrow();
        if (spirit.getMaxHealth() > 0 && spirit.getHealth() / spirit.getMaxHealth() < (float) injury.healthShare()) {
            needs.put(injury.id(), injury.blocked());
        }
        VitalNeeds.Need perch = VitalNeeds.byId("perch").orElseThrow();
        if (ScaffoldPerch.on(spirit)) {
            needs.put(perch.id(), perch.blocked());
        }
        return Map.copyOf(needs);
    }
}
