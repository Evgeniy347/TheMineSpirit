package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

public final class SettlementChests {
    public static final int CELL_SIZE = 16;

    private final Map<Long, List<BlockCoord>> cells = new HashMap<>();
    private final Set<BlockCoord> known = new HashSet<>();
    private long probes;

    public boolean add(BlockCoord pos) {
        Objects.requireNonNull(pos, "Сундук не может быть null: реестр хранит координаты");
        if (!known.add(pos)) {
            return false;
        }
        cells.computeIfAbsent(cell(pos), ignored -> new ArrayList<>()).add(pos);
        return true;
    }

    public boolean remove(BlockCoord pos) {
        Objects.requireNonNull(pos, "Сундук не может быть null: без него неизвестно, что снимать с реестра");
        if (!known.remove(pos)) {
            return false;
        }
        long cell = cell(pos);
        List<BlockCoord> list = cells.get(cell);
        if (list != null) {
            list.remove(pos);
            if (list.isEmpty()) {
                cells.remove(cell);
            }
        }
        return true;
    }

    public int size() {
        return known.size();
    }

    public List<BlockCoord> all() {
        List<BlockCoord> list = new ArrayList<>(known);
        list.sort(Comparator.naturalOrder());
        return list;
    }

    public List<BlockCoord> nearest(BlockCoord from, int limit) {
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: без неё порядок не задать");
        if (limit < 0) {
            throw new IllegalArgumentException(
                    "Предел выборки " + limit + " отрицателен: ближайших не бывает меньше нуля");
        }
        if (limit >= known.size()) {
            List<BlockCoord> list = new ArrayList<>(known);
            list.sort(distanceOrder(from));
            probes += list.size();
            return List.copyOf(list);
        }
        List<BlockCoord> list = new ArrayList<>();
        Search search = closest(from);
        while (list.size() < limit && search.hasNext()) {
            list.add(search.next());
        }
        return List.copyOf(list);
    }

    public Search closest(BlockCoord from) {
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: от неё ищутся ближайшие сундуки");
        return new Search(from);
    }

    public List<BlockCoord> warehouse(BlockCoord from) {
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: склад отдаётся в порядке расстояния");
        return nearest(from, known.size());
    }

    public List<BlockCoord> warehouse(BlockCoord from, Predicate<BlockCoord> insideArea) {
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: склад отдаётся в порядке расстояния");
        Objects.requireNonNull(insideArea, "Граница поселения не может быть null: без неё склад не ограничить");
        List<BlockCoord> inside = new ArrayList<>();
        for (BlockCoord pos : nearest(from, known.size())) {
            if (insideArea.test(pos)) {
                inside.add(pos);
            }
        }
        return List.copyOf(inside);
    }

    public long probes() {
        return probes;
    }

    private long cell(BlockCoord pos) {
        int x = Math.floorDiv(pos.x(), CELL_SIZE);
        int z = Math.floorDiv(pos.z(), CELL_SIZE);
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Comparator<BlockCoord> distanceOrder(BlockCoord from) {
        return Comparator.comparingLong((BlockCoord pos) -> distanceSq(from, pos))
                .thenComparing(BlockCoord::compareTo);
    }

    private static long distanceSq(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public final class Search {
        private final BlockCoord from;
        private final Queue<BlockCoord> pending;
        private int ring = -1;
        private int loaded;

        private Search(BlockCoord from) {
            this.from = from;
            this.pending = new PriorityQueue<>(distanceOrder(from));
        }

        public boolean hasNext() {
            fill();
            return !pending.isEmpty();
        }

        public BlockCoord next() {
            fill();
            if (pending.isEmpty()) {
                throw new NoSuchElementException("Сундуков больше нет: реестр отдал все, что в нём были");
            }
            return pending.poll();
        }

        private void fill() {
            while (true) {
                if (loaded >= known.size()) {
                    return;
                }
                if (!pending.isEmpty()) {
                    long bound = (long) ring * CELL_SIZE + 1;
                    if (distanceSq(from, pending.peek()) <= bound * bound) {
                        return;
                    }
                }
                ring++;
                loadRing(ring);
            }
        }

        private void loadRing(int radius) {
            int baseX = Math.floorDiv(from.x(), CELL_SIZE);
            int baseZ = Math.floorDiv(from.z(), CELL_SIZE);
            for (int x = baseX - radius; x <= baseX + radius; x++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    if (Math.max(Math.abs(x - baseX), Math.abs(z - baseZ)) != radius) {
                        continue;
                    }
                    probes++;
                    List<BlockCoord> list = cells.get(((long) x << 32) ^ (z & 0xffffffffL));
                    if (list == null) {
                        continue;
                    }
                    pending.addAll(list);
                    loaded += list.size();
                }
            }
        }
    }
}
