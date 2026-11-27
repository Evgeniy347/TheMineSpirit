package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class AuditRegistry {
    public static final int NO_LEVEL = Integer.MIN_VALUE;
    public static final long NO_PLAN = -1L;

    private final BlockDict dict = new BlockDict();
    private final DeepCaches deep = new DeepCaches();
    private final AuditBudgets budgets;
    private final Map<ChunkCell, ChunkCache> caches = new LinkedHashMap<>();
    private final List<ChunkCache> waiting = new ArrayList<>();
    private final AuditInvalidation invalid = new AuditInvalidation();
    private Set<ChunkCell> region = Set.of();
    private final int below;
    private final int layers;
    private int levelY = NO_LEVEL;
    private long planRevision = NO_PLAN;
    private long fillVersion;

    public AuditRegistry(AuditBudgets budgets, int below, int above) {
        this.budgets = Objects.requireNonNull(
                budgets, "Бюджеты аудита не могут быть null: без лимита наполнение съест такт духов");
        if (below < 1 || above < 0) {
            throw new IllegalArgumentException("Окно кэша ниже " + below + ", выше " + above
                    + ": слой плитки лежит на levelY−1, ниже него обязано быть хотя бы одно число");
        }
        this.layers = below + above + 1;
        if (this.layers > ChunkCache.MAX_LAYERS) {
            throw new IllegalArgumentException("Окно кэша " + this.layers + " слоёв больше предела "
                    + ChunkCache.MAX_LAYERS + ": память чанка выросла бы без пользы для envelope плана");
        }
        this.below = below;
    }

    public boolean plan(long revision, int levelY) {
        if (revision == planRevision && levelY == this.levelY) {
            return false;
        }
        boolean windowMoved = this.levelY != NO_LEVEL && this.levelY != levelY;
        invalid.releaseAll();
        planRevision = revision;
        this.levelY = levelY;
        if (windowMoved) {
            int minY = levelY - below;
            caches.replaceAll((chunk, ignored) -> new ChunkCache(chunk, dict, minY, layers));
            invalid.clearAll();
            deep.clear(waiting);
            fillVersion++;
            restart();
        }
        return true;
    }

    public void loaded(ChunkCell chunk) {
        Objects.requireNonNull(chunk, "Чанк не может быть null: в очередь наполнения ляжет пустой ключ");
        if (!region.contains(chunk)) {
            return;
        }
        ChunkCache cache = caches.get(chunk);
        if (cache == null) {
            ensure(chunk);
        } else if (cache.scanned()) {
            cache.clear();
            waiting.add(cache);
            fillVersion++;
        }
        if (deep.loaded(chunk, dict, waiting) > 0) {
            fillVersion++;
        }
    }

    public void ensure(ChunkCell chunk) {
        Objects.requireNonNull(chunk, "Чанк не может быть null: в очередь наполнения ляжет пустой ключ");
        if (levelY == NO_LEVEL || !region.contains(chunk) || caches.containsKey(chunk)) {
            return;
        }
        ChunkCache cache = new ChunkCache(chunk, dict, levelY - below, layers);
        caches.put(chunk, cache);
        waiting.add(cache);
    }

    public boolean ensureBand(ChunkCell chunk, int minY, int layers) {
        return deep.ensure(chunk, dict, minY, layers, waiting);
    }

    public void pruneDeepBands() {
        if (deep.size() > 0) {
            deep.clear(waiting);
            fillVersion++;
        }
    }

    public void retain(Set<ChunkCell> chunks) {
        Objects.requireNonNull(chunks, "Чанки региона не могут быть null: кэш очистился бы целиком");
        region = Set.copyOf(chunks);
        int before = caches.size();
        caches.keySet().retainAll(region);
        boolean deepChanged = deep.retain(region, waiting);
        if (caches.size() != before || deepChanged) {
            fillVersion++;
        }
        waiting.removeIf(cache -> !region.contains(cache.chunk()));
        invalid.retain(region);
    }

    public void unloaded(ChunkCell chunk) {
        boolean removed = caches.remove(chunk) != null;
        removed |= deep.unload(chunk, waiting);
        if (removed) {
            fillVersion++;
        }
        waiting.removeIf(cache -> cache.chunk().equals(chunk));
        invalid.unload(chunk);
    }

    public void reload(ChunkCell chunk) {
        ChunkCache cache = caches.get(chunk);
        boolean changed = false;
        if (cache != null && !waiting.contains(cache)) {
            cache.clear();
            waiting.add(cache);
            changed = true;
        }
        changed |= deep.reload(chunk, waiting) > 0;
        if (changed) {
            fillVersion++;
        }
    }

    public void issued(String ticket, String group) {
        invalid.issued(ticket, group);
    }

    public void released(String ticket) {
        invalid.released(ticket);
    }

    public void releaseAllIssued() {
        invalid.releaseAll();
    }

    public boolean record(ChangeRing.Change change) {
        return invalid.record(change, this);
    }

    public List<ChangeRing.Change> drain() {
        return drain(budgets.drainCellsPerTick());
    }

    public List<ChangeRing.Change> drain(int budget) {
        return invalid.drain(budget);
    }

    public Set<BlockCoord> dirty() {
        return invalid.dirty();
    }

    public void clean(BlockCoord cell) {
        invalid.clean(cell);
    }

    public @Nullable ChangeRing ring(BlockCoord cell) {
        return invalid.ring(cell);
    }

    public int pending() {
        return invalid.pending();
    }

    public int fill(AuditFill.Reader world, long tick) {
        return fill(budgets.fillCellsPerTick(), world, tick);
    }

    public int fill(int budget, AuditFill.Reader world, long tick) {
        int spent = 0;
        Iterator<ChunkCache> iterator = waiting.iterator();
        while (iterator.hasNext() && spent < budget) {
            ChunkCache cache = iterator.next();
            spent += AuditFill.fill(cache, budget - spent, world, tick);
            if (cache.scanned()) {
                iterator.remove();
                fillVersion++;
            }
        }
        return spent;
    }

    public @Nullable ChunkCache cache(BlockCoord cell) {
        ChunkCell chunk = ConstructionBatches.at(cell.x(), cell.z());
        ChunkCache found = caches.get(chunk);
        if (found != null && found.holds(cell)) {
            return found;
        }
        return deep.cache(cell);
    }

    public @Nullable String blockAt(BlockCoord cell) {
        ChunkCache cache = cache(cell);
        return cache == null || !cache.known(cell) ? null : cache.block(cell);
    }

    public int caches() {
        return caches.size() + deep.size();
    }

    public int waiting() {
        return waiting.size();
    }

    public int minY() {
        return levelY == NO_LEVEL ? NO_LEVEL : levelY - below;
    }

    public int layers() {
        return layers;
    }

    public long planRevision() {
        return planRevision;
    }

    public long fillVersion() {
        return fillVersion;
    }

    private void restart() {
        waiting.clear();
        waiting.addAll(caches.values());
        waiting.addAll(deep.all());
    }
}
