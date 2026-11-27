package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class DeepCaches {
    private record Band(int minY, int layers) {}

    private final Map<ChunkCell, List<Band>> definitions = new LinkedHashMap<>();
    private final Map<ChunkCell, List<ChunkCache>> active = new LinkedHashMap<>();

    boolean ensure(ChunkCell chunk, BlockDict dict, int minY, int layers, List<ChunkCache> waiting) {
        Objects.requireNonNull(chunk, "Чанк глубокого кэша не может быть null");
        List<Band> bands = definitions.computeIfAbsent(chunk, ignored -> new ArrayList<>());
        int maxY = minY + layers - 1;
        for (Band band : bands) {
            if (band.minY() <= minY && band.minY() + band.layers() - 1 >= maxY) {
                return false;
            }
        }
        bands.add(new Band(minY, layers));
        ChunkCache created = new ChunkCache(chunk, dict, minY, layers);
        active.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(created);
        waiting.add(created);
        return true;
    }

    @Nullable
    ChunkCache cache(BlockCoord cell) {
        List<ChunkCache> bands = active.get(ConstructionBatches.at(cell.x(), cell.z()));
        if (bands == null) {
            return null;
        }
        for (ChunkCache band : bands) {
            if (band.holds(cell)) {
                return band;
            }
        }
        return null;
    }

    Collection<ChunkCache> all() {
        List<ChunkCache> all = new ArrayList<>();
        active.values().forEach(all::addAll);
        return all;
    }

    boolean remove(ChunkCell chunk, List<ChunkCache> waiting) {
        definitions.remove(chunk);
        List<ChunkCache> removed = active.remove(chunk);
        if (removed == null) {
            return false;
        }
        waiting.removeAll(removed);
        return true;
    }

    boolean unload(ChunkCell chunk, List<ChunkCache> waiting) {
        List<ChunkCache> removed = active.remove(chunk);
        if (removed == null) {
            return false;
        }
        waiting.removeAll(removed);
        return true;
    }

    boolean retain(Set<ChunkCell> chunks, List<ChunkCache> waiting) {
        boolean changed = definitions.keySet().retainAll(chunks);
        changed |= active.keySet().retainAll(chunks);
        if (changed) {
            waiting.removeIf(cache -> !chunks.contains(cache.chunk()));
        }
        return changed;
    }

    void clear(List<ChunkCache> waiting) {
        for (List<ChunkCache> bands : active.values()) {
            waiting.removeAll(bands);
        }
        definitions.clear();
        active.clear();
    }

    int reload(ChunkCell chunk, List<ChunkCache> waiting) {
        int count = 0;
        for (ChunkCache band : active.getOrDefault(chunk, List.of())) {
            if (!waiting.contains(band)) {
                band.clear();
                waiting.add(band);
                count++;
            }
        }
        return count;
    }

    int loaded(ChunkCell chunk, BlockDict dict, List<ChunkCache> waiting) {
        int count = 0;
        for (Band band : definitions.getOrDefault(chunk, List.of())) {
            boolean exists = active.getOrDefault(chunk, List.of()).stream()
                    .anyMatch(cache -> cache.minY() == band.minY() && cache.layers() == band.layers());
            if (!exists) {
                ChunkCache created = new ChunkCache(chunk, dict, band.minY(), band.layers());
                active.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(created);
                waiting.add(created);
                count++;
            }
        }
        return count;
    }

    int size() {
        int total = 0;
        for (List<ChunkCache> bands : active.values()) {
            total += bands.size();
        }
        return total;
    }
}
