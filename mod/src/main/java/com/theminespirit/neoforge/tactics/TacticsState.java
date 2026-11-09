package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.tactics.adapter.StoredTactics;
import java.util.Optional;
import java.util.UUID;

final class TacticsState {
    private final TacticsService host;

    TacticsState(TacticsService host) {
        this.host = host;
    }

    StoredTactics snapshot() {
        return new StoredTactics(
                host.orders.mode(),
                host.orders.zone(),
                host.orders.ward().map(UUID::toString),
                Optional.ofNullable(host.memory),
                host.retreat);
    }

    void restore(StoredTactics stored) {
        host.orders.restoreMode(stored.ordered());
        host.orders.zone(stored.zone());
        host.orders.ward(stored.ward().map(UUID::fromString).orElse(null));
        host.memory = stored.memory().orElse(null);
        host.retreat = stored.retreat();
        host.orders.directives().resetHeld();
        host.forced = true;
    }
}
