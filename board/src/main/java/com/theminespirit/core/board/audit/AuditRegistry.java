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
    private final AuditBudgets budgets;
    private final Map<ChunkCell, ChunkCache> caches = new LinkedHashMap<>();
    private final List<ChunkCache> waiting = new ArrayList<>();
    private Set<ChunkCell> region = Set.of();
    private final int below;
    private final int layers;
    private int levelY = NO_LEVEL;
    private long planRevision = NO_PLAN;

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
        planRevision = revision;
        this.levelY = levelY;
        if (windowMoved) {
            int minY = levelY - below;
            caches.replaceAll((chunk, ignored) -> new ChunkCache(chunk, dict, minY, layers));
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

    public void retain(Set<ChunkCell> chunks) {
        Objects.requireNonNull(chunks, "Чанки региона не могут быть null: кэш очистился бы целиком");
        region = Set.copyOf(chunks);
        caches.keySet().retainAll(region);
        waiting.removeIf(cache -> !region.contains(cache.chunk()));
    }

    public void unloaded(ChunkCell chunk) {
        ChunkCache cache = caches.remove(chunk);
        if (cache == null) {
            return;
        }
        waiting.remove(cache);
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
            }
        }
        return spent;
    }

    public @Nullable ChunkCache cache(BlockCoord cell) {
        ChunkCache found = caches.get(ConstructionBatches.at(cell.x(), cell.z()));
        return found != null && found.holds(cell) ? found : null;
    }

    public @Nullable String blockAt(BlockCoord cell) {
        ChunkCache cache = cache(cell);
        return cache == null || !cache.known(cell) ? null : cache.block(cell);
    }

    public int caches() {
        return caches.size();
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

    private void restart() {
        waiting.clear();
        waiting.addAll(caches.values());
    }
}
