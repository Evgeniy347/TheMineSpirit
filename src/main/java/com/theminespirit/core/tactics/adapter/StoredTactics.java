package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.Optional;

public record StoredTactics(CombatMode ordered, Optional<OrdersBuilder.Zone> zone, Optional<String> ward,
        Optional<TacticsMemory> memory, RetreatTracker retreat) {
    public StoredTactics {
        if (ordered == null || zone == null || ward == null || memory == null || retreat == null) {
            throw new IllegalArgumentException("StoredTactics: ordered/zone/ward/memory/retreat = null; отсутствие "
                    + "значения записывается Optional.empty(), простой отхода — RetreatTracker.IDLE");
        }
    }
}
