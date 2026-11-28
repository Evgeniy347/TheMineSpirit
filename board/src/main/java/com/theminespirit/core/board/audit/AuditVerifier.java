package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class AuditVerifier {
    public record Result(List<AuditMiss> misses, int checked, int unknown, Set<ChunkCell> refill) {
        public Result {
            misses = List.copyOf(
                    Objects.requireNonNull(misses, "Промахи не могут быть null: пустой список — List.of()"));
            refill = Set.copyOf(Objects.requireNonNull(refill, "Чанки перезаливки не могут быть null"));
            if (checked < 0 || unknown < 0 || unknown > checked) {
                throw new IllegalArgumentException("Сверено " + checked + ", неизвестно " + unknown
                        + ": неизвестных клеток не бывает больше проверенных");
            }
        }
    }

    private ExpectIndex.Index index = new ExpectIndex.Index(Map.of(), 0, 0);
    private List<BlockCoord> order = List.of();
    private int cursor;

    public void reset(ExpectIndex.Index index, int centerChunkX, int centerChunkZ) {
        this.index = Objects.requireNonNull(index, "Индекс ожиданий не может быть null: сверять нечего");
        Map<ChunkCell, List<BlockCoord>> byChunk = new LinkedHashMap<>();
        for (Expectation expectation : index.cells().values()) {
            BlockCoord cell = expectation.cell();
            byChunk.computeIfAbsent(ConstructionBatches.at(cell.x(), cell.z()), ignored -> new ArrayList<>())
                    .add(cell);
        }
        List<BlockCoord> next = new ArrayList<>();
        for (ChunkCell chunk : ConstructionBatches.order(byChunk.keySet(), centerChunkX, centerChunkZ)) {
            List<BlockCoord> cells =
                    Objects.requireNonNull(byChunk.get(chunk), "У чанка спирали обязана быть своя очередь клеток");
            cells.sort(Comparator.comparingInt(BlockCoord::y)
                    .thenComparingInt(BlockCoord::x)
                    .thenComparingInt(BlockCoord::z));
            next.addAll(cells);
        }
        order = List.copyOf(next);
        cursor = 0;
    }

    public Result verify(AuditRegistry registry, AuditFill.Reader world, int budget, long tick, long planRevision) {
        Objects.requireNonNull(registry, "Реестр кэша не может быть null: сверять не с чем");
        Objects.requireNonNull(world, "Чтец мира не может быть null: факты читать нечем");
        if (budget <= 0) {
            throw new IllegalArgumentException("Бюджет сверки " + budget
                    + " не положителен: спираль не сдвинется и промах кэша останется незамеченным");
        }
        if (order.isEmpty()) {
            return new Result(List.of(), 0, 0, Set.of());
        }
        if (cursor >= order.size()) {
            cursor = 0;
        }
        List<AuditMiss> misses = new ArrayList<>();
        Set<ChunkCell> refill = new LinkedHashSet<>();
        int checked = 0;
        int unknown = 0;
        while (checked < budget && cursor < order.size()) {
            BlockCoord cell = order.get(cursor++);
            Expectation expectation = index.at(cell).orElseThrow();
            ChunkCache cache = registry.cache(cell);
            if (cache == null || cache.filledTick() < 0) {
                unknown++;
                checked++;
                continue;
            }
            long lastVisit = cache.lastVisit();
            cache.visited(tick);
            String actual = world.blockAt(cell);
            if (actual == null) {
                cache.markUnknown(cell);
                unknown++;
                checked++;
                continue;
            }
            String cached = cache.block(cell);
            if (cached == null) {
                cached = "unknown";
            }
            checked++;
            if (cached.equals(actual)) {
                registry.clean(cell);
                continue;
            }
            ChangeRing ring = registry.ring(cell);
            AuditMiss.Signs signs = new AuditMiss.Signs(
                    cache.filledTick() >= 0 && cache.known(cell),
                    ring != null && ring.seenSince(cell, cache.filledTick()),
                    BlockEquivalence.check(expectation.wanted(), actual) == BlockEquivalence.Verdict.DONE,
                    false);
            AuditMiss miss = AuditMiss.of(
                    AuditMiss.classify(signs),
                    AuditMiss.CACHE_STALE,
                    cell,
                    cached,
                    actual,
                    expectation.wanted(),
                    context(expectation, cache, ring, lastVisit, planRevision));
            misses.add(miss);
            refill.add(cache.chunk());
        }
        return new Result(misses, checked, unknown, refill);
    }

    public int pending() {
        return order.isEmpty() ? 0 : order.size() - cursor;
    }

    private static AuditMiss.Context context(
            Expectation expectation, ChunkCache cache, @Nullable ChangeRing ring, long lastVisit, long planRevision) {
        ChangeRing.Change last = ring == null ? null : ring.last();
        return new AuditMiss.Context(
                GroupKeys.key(expectation),
                (int) Math.min(Integer.MAX_VALUE, planRevision),
                cache.filledTick(),
                lastVisit,
                ring == null ? 0 : ring.size(),
                last == null ? "" : last.text(),
                AuditMiss.CACHE_STALE);
    }
}
