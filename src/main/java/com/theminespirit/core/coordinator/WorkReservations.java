package com.theminespirit.core.coordinator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WorkReservations {

    public enum Outcome {
        GRANTED,
        TAKEN_BY_OTHER,
        TAKEN_BY_SELF
    }

    private record Lease(UUID owner, long deadline) {}

    private final Map<WorkTarget, Lease> leases = new LinkedHashMap<>();
    private final Map<UUID, Set<WorkTarget>> byOwner = new HashMap<>();

    public Outcome reserve(WorkTarget target, UUID spirit, long now, long leaseTicks) {
        if (target == null) {
            throw new IllegalArgumentException("target не может быть null: бронь без цели не имеет смысла");
        }
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: бронь без духа не имеет владельца");
        }
        if (now < 0) {
            throw new IllegalArgumentException("now = " + now + ": время брони не может быть отрицательным");
        }
        if (leaseTicks <= 0) {
            throw new IllegalArgumentException("leaseTicks = " + leaseTicks
                    + ": срок брони обязан быть положительным, иначе цель занята навсегда");
        }
        dropExpired(target, now);
        Lease lease = leases.get(target);
        if (lease != null) {
            return lease.owner().equals(spirit) ? Outcome.TAKEN_BY_SELF : Outcome.TAKEN_BY_OTHER;
        }
        leases.put(target, new Lease(spirit, now + leaseTicks));
        byOwner.computeIfAbsent(spirit, key -> new HashSet<>()).add(target);
        return Outcome.GRANTED;
    }

    public boolean release(WorkTarget target, UUID spirit) {
        if (target == null) {
            throw new IllegalArgumentException("target не может быть null: снимать бронь не с чего");
        }
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: снимать бронь не с кого");
        }
        Lease lease = leases.get(target);
        if (lease == null || !lease.owner().equals(spirit)) {
            return false;
        }
        leases.remove(target);
        forget(target, spirit);
        return true;
    }

    public int releaseAll(UUID spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: снимать брони не с кого");
        }
        Set<WorkTarget> owned = byOwner.remove(spirit);
        if (owned == null) {
            return 0;
        }
        for (WorkTarget target : owned) {
            leases.remove(target);
        }
        return owned.size();
    }

    public Set<WorkTarget> ownedBy(UUID spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: у брони всегда есть владелец");
        }
        Set<WorkTarget> owned = byOwner.get(spirit);
        return owned == null ? Set.of() : Set.copyOf(owned);
    }

    public Optional<UUID> owner(WorkTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("target не может быть null: владельца без цели не бывает");
        }
        return Optional.ofNullable(leases.get(target)).map(Lease::owner);
    }

    public int size() {
        return leases.size();
    }

    public int expire(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now = " + now + ": время освобождения не может быть отрицательным");
        }
        List<WorkTarget> expired = leases.entrySet().stream()
                .filter(entry -> entry.getValue().deadline() <= now)
                .map(Map.Entry::getKey)
                .toList();
        for (WorkTarget target : expired) {
            Lease lease = leases.remove(target);
            forget(target, lease.owner());
        }
        return expired.size();
    }

    private void dropExpired(WorkTarget target, long now) {
        Lease lease = leases.get(target);
        if (lease != null && lease.deadline() <= now) {
            leases.remove(target);
            forget(target, lease.owner());
        }
    }

    private void forget(WorkTarget target, UUID spirit) {
        Set<WorkTarget> owned = byOwner.get(spirit);
        owned.remove(target);
        if (owned.isEmpty()) {
            byOwner.remove(spirit);
        }
    }
}
