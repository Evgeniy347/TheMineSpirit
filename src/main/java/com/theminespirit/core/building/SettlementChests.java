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
import java.util.Set;

/**
 * Реестр сундуков поселения (решение владельца 20.10): все сундуки видны духам сразу, без предела радиуса вокруг
 * стройки. Установка и снятие сундука меняют реестр за O(1); поиск ближайших идёт по пространственным ячейкам и
 * читает только те ячейки, которые нужны, — число операций не растёт от числа далёких сундуков.
 *
 * <p>Порядок ближайших — точный: ячейки-кольца вокруг ног духа читаются от ближних к дальним, а из найденного
 * отдаётся только то, что заведомо ближе всех ещё не прочитанных ячеек.
 */
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

    // Упорядоченный снимок — для сохранения в мир и полного обхода (начало плана стройки).
    public List<BlockCoord> all() {
        List<BlockCoord> list = new ArrayList<>(known);
        list.sort(Comparator.naturalOrder());
        return list;
    }

    // Ближайшие сундуки по одному: берётся не больше limit, дальние ячейки не читаются.
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

    // Число прочитанных ячеек и кандидатов: тест масштаба видит, что поиск не зависит от далёких сундуков.
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

    /** Ленивый обход сундуков от ближнего к дальнему: читает ячейки только до первого нужного результата. */
    public final class Search {
        private final BlockCoord from;
        private final PriorityQueue<BlockCoord> pending;
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
