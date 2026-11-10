package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class LeaseBook {
    public static final String LEGACY_ID = "legacy";
    public static final String LEGACY_PURPOSE = "legacy";

    private final Map<String, Lease> leases = new LinkedHashMap<>();
    private final Map<BlockCoord, String> activeCells = new HashMap<>();
    private final Map<BlockCoord, String> orphanedCells = new HashMap<>();

    public Lease open(@Nullable UUID spirit, @Nullable String ticket, String purpose, long since) {
        String id = UUID.randomUUID().toString();
        Lease lease = Lease.opened(id, spirit, ticket, purpose, since);
        leases.put(id, lease);
        return lease;
    }

    public Lease legacy(List<BlockCoord> cells, long since) {
        Objects.requireNonNull(cells, "Клетки старых временных меток не могут быть null: мигрировать нечего");
        Lease existing = leases.get(LEGACY_ID);
        if (existing != null) {
            if (!LEGACY_PURPOSE.equals(existing.purpose())) {
                throw new IllegalStateException("id '" + LEGACY_ID + "' уже занят арендой '" + existing.purpose()
                        + "': старые метки нельзя слить в чужую аренду");
            }
            return existing;
        }
        List<LeaseCell> legacyCells = cells.stream().map(LeaseCell::placed).toList();
        Lease lease = new Lease(LEGACY_ID, null, null, LEGACY_PURPOSE, LeaseStatus.ORPHANED, since, legacyCells);
        restore(lease);
        return lease;
    }

    public Optional<Lease> find(String id) {
        Objects.requireNonNull(id, "id аренды не может быть null: искать нечего");
        return Optional.ofNullable(leases.get(id));
    }

    public Lease require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Аренда '" + id
                + "' не найдена: правку мира записывают только в открытую аренду, иначе откат её не увидит"));
    }

    public List<Lease> all() {
        return List.copyOf(leases.values());
    }

    public List<Lease> active() {
        return leases.values().stream()
                .filter(lease -> lease.status() == LeaseStatus.ACTIVE)
                .toList();
    }

    public List<Lease> orphaned() {
        List<Lease> values = new ArrayList<>(leases.values());
        Collections.reverse(values);
        return values.stream()
                .filter(lease -> lease.status() == LeaseStatus.ORPHANED)
                .toList();
    }

    public List<LeaseCell> orphanedCells() {
        List<LeaseCell> cells = new ArrayList<>();
        for (Lease lease : orphaned()) {
            cells.addAll(lease.rollbackOrder());
        }
        return List.copyOf(cells);
    }

    public Lease record(String id, LeaseCell cell) {
        return record(id, List.of(cell));
    }

    public Lease record(String id, List<LeaseCell> cells) {
        Objects.requireNonNull(cells, "Клетки записи не могут быть null: записывать нечего");
        Lease lease = require(id);
        if (lease.status() != LeaseStatus.ACTIVE) {
            throw new IllegalStateException("Аренда '" + id + "' в статусе " + lease.status()
                    + ": правку записывают только в действующую аренду");
        }
        Set<BlockCoord> seen = new HashSet<>();
        for (LeaseCell cell : lease.cells()) {
            seen.add(cell.pos());
        }
        for (LeaseCell cell : cells) {
            Objects.requireNonNull(cell, "Клетка записи не может быть null: записывать нечего");
            if (!seen.add(cell.pos())) {
                throw new IllegalStateException("Клетка " + cell.pos() + " уже записана в аренде '" + id
                        + "': повторная запись сломала бы обратный откат");
            }
            String active = activeCells.get(cell.pos());
            if (active != null) {
                throw new IllegalStateException("Клетка " + cell.pos() + " уже в действующей аренде '" + active
                        + "': две механики не могут владеть одной клеткой одновременно");
            }
            String orphaned = orphanedCells.get(cell.pos());
            if (orphaned != null) {
                throw new IllegalStateException("Клетка " + cell.pos() + " уже в брошенной аренде '" + orphaned
                        + "': сначала откатите брошенную правку, иначе её откат сломает новую");
            }
        }
        Lease next = lease.withCells(concat(lease.cells(), cells));
        leases.put(id, next);
        for (LeaseCell cell : cells) {
            activeCells.put(cell.pos(), id);
        }
        return next;
    }

    public Lease forget(String id, BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: забывать нечего");
        Lease lease = require(id);
        if (lease.status() != LeaseStatus.ACTIVE) {
            throw new IllegalStateException("Аренда '" + id + "' в статусе " + lease.status()
                    + ": клетки забывают только у действующей аренды вовремя своего отката");
        }
        List<LeaseCell> next = new ArrayList<>(lease.cells());
        boolean removed = next.removeIf(recorded -> recorded.pos().equals(cell));
        if (!removed) {
            throw new IllegalArgumentException("Клетка " + cell + " не записана в аренде '" + id
                    + "': забывают только то, что аренда помнит, иначе откат потеряет правку");
        }
        Lease updated = lease.withCells(next);
        leases.put(id, updated);
        activeCells.remove(cell, id);
        return updated;
    }

    public Lease commit(String id) {
        Lease lease = require(id);
        if (lease.status() == LeaseStatus.DONE) {
            return lease;
        }
        if (lease.status() != LeaseStatus.ACTIVE) {
            throw new IllegalStateException("Аренда '" + id + "' в статусе " + lease.status()
                    + ": брошенную правку закрывают откатом, а не отметкой DONE");
        }
        release(lease, activeCells);
        Lease done = lease.withStatus(LeaseStatus.DONE);
        leases.put(id, done);
        return done;
    }

    public Lease orphan(String id) {
        Lease lease = require(id);
        if (lease.status() == LeaseStatus.ORPHANED) {
            return lease;
        }
        if (lease.status() != LeaseStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Аренда '" + id + "' в статусе " + lease.status() + ": закрытую правку нельзя бросить");
        }
        release(lease, activeCells);
        Lease orphaned = lease.withStatus(LeaseStatus.ORPHANED);
        leases.put(id, orphaned);
        for (LeaseCell cell : lease.cells()) {
            orphanedCells.put(cell.pos(), id);
        }
        return orphaned;
    }

    public int orphanSpirit(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух аренды не может быть null: бросать нечего");
        int count = 0;
        for (Lease lease : active()) {
            if (lease.ownedBy(spirit)) {
                orphan(lease.id());
                count++;
            }
        }
        return count;
    }

    public int orphanAllActive() {
        int count = 0;
        for (Lease lease : active()) {
            orphan(lease.id());
            count++;
        }
        return count;
    }

    public Lease rollback(String id, BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: отмечать откат нечего");
        Lease lease = require(id);
        if (lease.status() != LeaseStatus.ORPHANED) {
            throw new IllegalStateException("Аренда '" + id + "' в статусе " + lease.status()
                    + ": откатывают только брошенную правку, у действующей клетки ещё в работе");
        }
        List<LeaseCell> next = new ArrayList<>(lease.cells());
        if (!next.removeIf(recorded -> recorded.pos().equals(cell))) {
            throw new IllegalArgumentException("Клетка " + cell + " не записана в брошенной аренде '" + id
                    + "': откат не может закрыть чужую правку");
        }
        orphanedCells.remove(cell, id);
        Lease updated = next.isEmpty() ? lease.withCells(next).withStatus(LeaseStatus.DONE) : lease.withCells(next);
        leases.put(id, updated);
        return updated;
    }

    public Optional<Lease> owner(BlockCoord pos) {
        Objects.requireNonNull(pos, "Клетка не может быть null: владельца ищут по ней");
        String active = activeCells.get(pos);
        if (active != null) {
            return Optional.ofNullable(leases.get(active));
        }
        return Optional.ofNullable(orphanedCells.get(pos)).map(leases::get);
    }

    public Optional<Lease> activeOwner(BlockCoord pos) {
        Objects.requireNonNull(pos, "Клетка не может быть null: владельца ищут по ней");
        String active = activeCells.get(pos);
        return active == null ? Optional.empty() : Optional.ofNullable(leases.get(active));
    }

    public boolean isActive(BlockCoord pos) {
        return activeOwner(pos).isPresent();
    }

    public boolean isOrphaned(BlockCoord pos) {
        Objects.requireNonNull(pos, "Клетка не может быть null: брошенную аренду ищут по ней");
        return orphanedCells.containsKey(pos);
    }

    public List<LeaseCell> rollbackOrder(Lease lease) {
        Objects.requireNonNull(lease, "Аренда не может быть null: откатывать нечего");
        return lease.rollbackOrder();
    }

    public void restore(Lease lease) {
        Objects.requireNonNull(lease, "Аренда не может быть null: восстанавливать нечего");
        if (leases.containsKey(lease.id())) {
            throw new IllegalArgumentException("Аренда '" + lease.id() + "' записана дважды: сохранение повреждено");
        }
        if (lease.status() != LeaseStatus.DONE) {
            boolean active = lease.status() == LeaseStatus.ACTIVE;
            Map<BlockCoord, String> target = active ? activeCells : orphanedCells;
            Map<BlockCoord, String> other = active ? orphanedCells : activeCells;
            for (LeaseCell cell : lease.cells()) {
                String owner = target.putIfAbsent(cell.pos(), lease.id());
                if (owner != null) {
                    throw new IllegalArgumentException("Клетка " + cell.pos() + " записана в двух арендах ('" + owner
                            + "' и '" + lease.id() + "'): сохранение повреждено");
                }
                if (other.containsKey(cell.pos())) {
                    throw new IllegalArgumentException("Клетка " + cell.pos()
                            + " одновременно в действующей и брошенной аренде: сохранение повреждено");
                }
            }
        }
        leases.put(lease.id(), lease);
    }

    private void release(Lease lease, Map<BlockCoord, String> cells) {
        for (LeaseCell cell : lease.cells()) {
            cells.remove(cell.pos(), lease.id());
        }
    }

    private static List<LeaseCell> concat(List<LeaseCell> first, List<LeaseCell> second) {
        List<LeaseCell> joined = new ArrayList<>(first.size() + second.size());
        joined.addAll(first);
        joined.addAll(second);
        return joined;
    }
}
