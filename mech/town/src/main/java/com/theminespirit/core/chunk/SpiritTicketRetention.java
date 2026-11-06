package com.theminespirit.core.chunk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpiritTicketRetention {
    private SpiritTicketRetention() {}

    public static Set<UUID> keepAtLoad(Set<UUID> ticketOwners, Optional<Set<UUID>> knownLiveSpirits) {
        Objects.requireNonNull(
                ticketOwners, "ticketOwners не может быть null: владельцев билетов перечисляет загрузчик мира");
        Objects.requireNonNull(
                knownLiveSpirits, "knownLiveSpirits не может быть null: отсутствие реестра задают пустым Optional");
        if (knownLiveSpirits.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(ticketOwners));
        }
        Set<UUID> keep = new LinkedHashSet<>(ticketOwners);
        keep.retainAll(knownLiveSpirits.get());
        return Collections.unmodifiableSet(keep);
    }
}
