package com.theminespirit.core.movement.network;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Сеть тропинок (решение владельца 20.10): граф отрезков, узлы — концы отрезков. Изменение блока находит задетые
 * отрезки по индексу «блок → отрезки» за O(задетых), без обхода сети; задетые помечаются к пересчёту, и маршрут их
 * обходит, пока их не пересчитают. Счётчик {@link #operations()} — для теста на масштаб.
 */
public final class PathNetwork {
    // Отрезок зависит от пола под ногами, ног, головы и клетки над головой (над ней проходит голова на шаге вверх).
    private static final int BELOW_FEET = -1;
    private static final int ABOVE_HEAD = 2;

    private final Map<Integer, PathSegment> segments = new HashMap<>();
    private final Map<BlockCoord, Set<Integer>> byBlock = new HashMap<>();
    private final Map<BlockCoord, Set<Integer>> byCell = new HashMap<>();
    private final Map<BlockCoord, Set<Integer>> byEnd = new HashMap<>();
    private final Set<Integer> dirty = new TreeSet<>();
    private int nextId;
    private long operations;

    public int add(List<BlockCoord> cells) {
        PathSegment segment = new PathSegment(nextId++, cells);
        index(segment);
        return segment.id();
    }

    public void replace(int id, List<BlockCoord> cells) {
        PathSegment old = require(id);
        unindex(old);
        dirty.remove(id);
        index(new PathSegment(id, cells));
    }

    public void remove(int id) {
        unindex(require(id));
        dirty.remove(id);
    }

    public void removeAll(Collection<Integer> ids) {
        for (int id : List.copyOf(ids)) {
            if (segments.containsKey(id)) {
                remove(id);
            }
        }
    }

    public Set<Integer> blockChanged(BlockCoord block) {
        Objects.requireNonNull(block, "Изменённый блок не может быть null: без него неизвестно, что пересчитать");
        operations++;
        Set<Integer> touched = byBlock.get(block);
        if (touched == null) {
            return Set.of();
        }
        operations += touched.size();
        dirty.addAll(touched);
        return Set.copyOf(touched);
    }

    public List<Integer> dirty() {
        return List.copyOf(dirty);
    }

    public PathSegment segment(int id) {
        return require(id);
    }

    public Collection<PathSegment> segments() {
        return Collections.unmodifiableCollection(segments.values());
    }

    public Set<Integer> segmentsAt(BlockCoord feet) {
        return Set.copyOf(byCell.getOrDefault(feet, Set.of()));
    }

    public boolean contains(BlockCoord feet) {
        Set<Integer> ids = byCell.get(feet);
        return ids != null && !usable(ids).isEmpty();
    }

    public int size() {
        return segments.size();
    }

    public long operations() {
        return operations;
    }

    // Отрезок, по которому от клетки входа до клетки выхода короче всего идти; оба — клетки сети. Ответ — точки,
    // между соседними не больше длины отрезка: вход, концы пройденных отрезков, выход.
    public Optional<List<BlockCoord>> route(BlockCoord entry, BlockCoord exit) {
        Objects.requireNonNull(entry, "Вход в сеть не может быть null: от него ищется маршрут");
        Objects.requireNonNull(exit, "Выход из сети не может быть null: к нему ищется маршрут");
        if (entry.equals(exit)) {
            return Optional.of(List.of(entry));
        }
        Map<BlockCoord, Double> exitCost = new HashMap<>();
        double direct = Double.POSITIVE_INFINITY;
        for (Hit hit : hits(exit)) {
            exitCost.merge(hit.segment.first(), hit.segment.lengthBetween(0, hit.index), Math::min);
            exitCost.merge(hit.segment.last(), hit.segment.lengthBetween(hit.index, lastIndex(hit)), Math::min);
            int onEntry = hit.segment.cells().indexOf(entry);
            if (onEntry >= 0) {
                direct = Math.min(direct, hit.segment.lengthBetween(onEntry, hit.index));
            }
        }
        Map<BlockCoord, Double> dist = new HashMap<>();
        Map<BlockCoord, @Nullable BlockCoord> previous = new HashMap<>();
        PriorityQueue<Visit> queue = new PriorityQueue<>();
        for (Hit hit : hits(entry)) {
            relax(dist, previous, queue, null, hit.segment.first(), hit.segment.lengthBetween(0, hit.index));
            relax(
                    dist,
                    previous,
                    queue,
                    null,
                    hit.segment.last(),
                    hit.segment.lengthBetween(hit.index, lastIndex(hit)));
        }
        double best = direct;
        @Nullable BlockCoord bestEnd = null;
        Set<BlockCoord> done = new HashSet<>();
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            operations++;
            if (visit.cost >= best || !done.add(visit.node)) {
                continue;
            }
            Double out = exitCost.get(visit.node);
            if (out != null && visit.cost + out < best) {
                best = visit.cost + out;
                bestEnd = visit.node;
            }
            for (int id : usable(byEnd.getOrDefault(visit.node, Set.of()))) {
                PathSegment segment = require(id);
                BlockCoord other = segment.first().equals(visit.node) ? segment.last() : segment.first();
                relax(dist, previous, queue, visit.node, other, visit.cost + segment.length());
            }
        }
        if (best == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        List<BlockCoord> points = new ArrayList<>();
        points.add(exit);
        for (BlockCoord node = bestEnd; node != null; node = previous.get(node)) {
            points.add(node);
        }
        points.add(entry);
        Collections.reverse(points);
        return Optional.of(dedupe(points));
    }

    private void relax(
            Map<BlockCoord, Double> dist,
            Map<BlockCoord, @Nullable BlockCoord> previous,
            PriorityQueue<Visit> queue,
            @Nullable BlockCoord from,
            BlockCoord to,
            double cost) {
        operations++;
        if (cost < dist.getOrDefault(to, Double.POSITIVE_INFINITY)) {
            dist.put(to, cost);
            previous.put(to, from);
            queue.add(new Visit(to, cost));
        }
    }

    private static List<BlockCoord> dedupe(List<BlockCoord> points) {
        List<BlockCoord> result = new ArrayList<>();
        for (BlockCoord point : points) {
            if (result.isEmpty() || !result.get(result.size() - 1).equals(point)) {
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    private List<Hit> hits(BlockCoord cell) {
        List<Hit> hits = new ArrayList<>();
        for (int id : usable(byCell.getOrDefault(cell, Set.of()))) {
            PathSegment segment = require(id);
            hits.add(new Hit(segment, segment.cells().indexOf(cell)));
        }
        return hits;
    }

    private Set<Integer> usable(Set<Integer> ids) {
        Set<Integer> result = new TreeSet<>(ids);
        result.removeAll(dirty);
        return result;
    }

    private static int lastIndex(Hit hit) {
        return hit.segment.cells().size() - 1;
    }

    private PathSegment require(int id) {
        PathSegment segment = segments.get(id);
        if (segment == null) {
            throw new IllegalArgumentException("отрезка " + id + " нет в сети: его уже убрали или не добавляли");
        }
        return segment;
    }

    private void index(PathSegment segment) {
        segments.put(segment.id(), segment);
        for (BlockCoord cell : segment.cells()) {
            operations++;
            byCell.computeIfAbsent(cell, key -> new HashSet<>()).add(segment.id());
            for (int dy = BELOW_FEET; dy <= ABOVE_HEAD; dy++) {
                BlockCoord block = new BlockCoord(cell.x(), cell.y() + dy, cell.z());
                byBlock.computeIfAbsent(block, key -> new HashSet<>()).add(segment.id());
            }
        }
        byEnd.computeIfAbsent(segment.first(), key -> new HashSet<>()).add(segment.id());
        byEnd.computeIfAbsent(segment.last(), key -> new HashSet<>()).add(segment.id());
    }

    private void unindex(PathSegment segment) {
        segments.remove(segment.id());
        for (BlockCoord cell : segment.cells()) {
            operations++;
            drop(byCell, cell, segment.id());
            for (int dy = BELOW_FEET; dy <= ABOVE_HEAD; dy++) {
                drop(byBlock, new BlockCoord(cell.x(), cell.y() + dy, cell.z()), segment.id());
            }
        }
        drop(byEnd, segment.first(), segment.id());
        drop(byEnd, segment.last(), segment.id());
    }

    private static void drop(Map<BlockCoord, Set<Integer>> index, BlockCoord key, int id) {
        Set<Integer> ids = index.get(key);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) {
                index.remove(key);
            }
        }
    }

    private record Hit(PathSegment segment, int index) {}

    private record Visit(BlockCoord node, double cost) implements Comparable<Visit> {
        @Override
        public int compareTo(Visit other) {
            int byCost = Double.compare(cost, other.cost);
            return byCost != 0 ? byCost : node.compareTo(other.node);
        }
    }
}
