package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class IdleRelease {
    private final CasePauseBook pauses;
    private final BlockedBook blocked;
    private final Map<UUID, Long> idleSince = new LinkedHashMap<>();

    IdleRelease(CasePauseBook pauses, BlockedBook blocked) {
        this.pauses = Objects.requireNonNull(pauses, "Паузы кейсов не могут быть null: простой их снимает");
        this.blocked = Objects.requireNonNull(blocked, "Блокировки не могут быть null: простой их снимает");
    }

    int tick(Set<UUID> present, Set<UUID> idle, long now) {
        Objects.requireNonNull(present, "Список духов мира не может быть null: пустой набор — пустое множество");
        Objects.requireNonNull(idle, "Список простаивающих духов не может быть null: пустой набор — пустое множество");
        idleSince.keySet().removeIf(spirit -> !present.contains(spirit) || !idle.contains(spirit));
        int released = 0;
        for (UUID spirit : idle) {
            long since = idleSince.computeIfAbsent(spirit, ignored -> now);
            if (now - since < BoardTuning.idleReleaseTicks()) {
                continue;
            }
            released += pauses.releaseAll(spirit);
            released += blocked.releaseAll(now);
            idleSince.put(spirit, now);
        }
        return released;
    }

    void clear() {
        idleSince.clear();
    }
}
