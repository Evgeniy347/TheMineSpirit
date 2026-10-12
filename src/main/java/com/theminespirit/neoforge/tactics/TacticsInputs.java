package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import java.util.Optional;
import java.util.UUID;

public record TacticsInputs(CombatMode mode, Optional<OrdersBuilder.Zone> zone, Optional<UUID> ward, int ticksSinceHit,
        int ticksSinceStrike) {
}
