package com.theminespirit.core.chunk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SpiritTicketRetention {
    private SpiritTicketRetention() {
    }

    public static Set<UUID> keepAtLoad(Set<UUID> ticketOwners, Set<UUID> knownLiveSpirits) {
        Objects.requireNonNull(ticketOwners,
                "ticketOwners не может быть null: владельцев билетов перечисляет загрузчик мира");
        Objects.requireNonNull(knownLiveSpirits,
                "knownLiveSpirits не может быть null: без реестра живых духов решение не определить");
        Set<UUID> keep = new LinkedHashSet<>(ticketOwners);
        keep.retainAll(knownLiveSpirits);
        return Collections.unmodifiableSet(keep);
    }
}
