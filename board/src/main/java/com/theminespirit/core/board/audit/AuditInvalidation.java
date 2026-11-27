package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class AuditInvalidation {
    private final Map<ChunkCell, ChangeRing> rings = new HashMap<>();
    private final Map<BlockCoord, ChangeRing.Change> pending = new LinkedHashMap<>();
    private final Set<BlockCoord> dirty = new HashSet<>();
    private final Map<String, String> issuedTickets = new HashMap<>();

    void issued(String ticket, String group) {
        if (ticket.isBlank() || group.isBlank()) {
            throw new IllegalArgumentException("Билет '" + ticket + "' и группа '" + group
                    + "' не могут быть пустыми: иначе своё изменение духа станет чужой грязью");
        }
        issuedTickets.put(ticket, group);
    }

    void released(String ticket) {
        issuedTickets.remove(ticket);
    }

    void releaseAll() {
        issuedTickets.clear();
    }

    boolean record(ChangeRing.Change change, AuditRegistry registry) {
        Objects.requireNonNull(change, "Изменение блока не может быть null: инвалидации нечем");
        if (change.before().equals(change.after())) {
            return false;
        }
        String group = change.group();
        if (group.isBlank() && !change.ticket().isBlank()) {
            group = issuedTickets.getOrDefault(change.ticket(), "");
        }
        ChangeRing.Change normalized = group.equals(change.group())
                ? change
                : new ChangeRing.Change(
                        change.tick(),
                        change.cell(),
                        change.before(),
                        change.after(),
                        change.actor(),
                        change.basis(),
                        change.ticket(),
                        group);
        ChunkCache cache = registry.cache(change.cell());
        if (cache == null) {
            return false;
        }
        ChunkCell chunk =
                ConstructionBatches.at(change.cell().x(), change.cell().z());
        boolean own =
                !normalized.ticket().isBlank() && normalized.group().equals(issuedTickets.get(normalized.ticket()));
        if (own) {
            if (normalized.after().equals(cache.block(change.cell()))) {
                return false;
            }
            rings.computeIfAbsent(chunk, ignored -> new ChangeRing()).add(normalized);
            cache.put(change.cell(), normalized.after());
            pending.remove(change.cell());
            dirty.remove(change.cell());
            return false;
        }
        ChangeRing.Change previous = pending.get(change.cell());
        if (previous != null && previous.tick() == change.tick()) {
            return false;
        }
        rings.computeIfAbsent(chunk, ignored -> new ChangeRing()).add(normalized);
        pending.put(change.cell(), normalized);
        dirty.add(change.cell());
        return true;
    }

    List<ChangeRing.Change> drain(int budget) {
        if (budget <= 0) {
            throw new IllegalArgumentException("Бюджет разбора изменений " + budget
                    + " не положителен: очередь переполнится и инцидент потеряет последний блок");
        }
        List<ChangeRing.Change> taken = new ArrayList<>();
        Iterator<Map.Entry<BlockCoord, ChangeRing.Change>> iterator =
                pending.entrySet().iterator();
        while (iterator.hasNext() && taken.size() < budget) {
            Map.Entry<BlockCoord, ChangeRing.Change> entry = iterator.next();
            taken.add(entry.getValue());
            iterator.remove();
        }
        return List.copyOf(taken);
    }

    Set<BlockCoord> dirty() {
        return Set.copyOf(dirty);
    }

    void clean(BlockCoord cell) {
        dirty.remove(cell);
    }

    @Nullable
    ChangeRing ring(BlockCoord cell) {
        return rings.get(ConstructionBatches.at(cell.x(), cell.z()));
    }

    int pending() {
        return pending.size();
    }

    void clearAll() {
        rings.clear();
        pending.clear();
        dirty.clear();
    }

    void retain(Set<ChunkCell> chunks) {
        rings.keySet().retainAll(chunks);
        pending.keySet().removeIf(cell -> !chunks.contains(ConstructionBatches.at(cell.x(), cell.z())));
        dirty.removeIf(cell -> !chunks.contains(ConstructionBatches.at(cell.x(), cell.z())));
    }

    void unload(ChunkCell chunk) {
        rings.remove(chunk);
        pending.keySet().removeIf(cell -> chunk.equals(ConstructionBatches.at(cell.x(), cell.z())));
        dirty.removeIf(cell -> chunk.equals(ConstructionBatches.at(cell.x(), cell.z())));
    }
}
