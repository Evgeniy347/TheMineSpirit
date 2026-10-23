package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Schematic(
        int sizeX,
        int sizeY,
        int sizeZ,
        List<Entry> blocks,
        List<Cell> air,
        @Nullable Repeat repeat) {
    public record Cell(int dx, int dy, int dz) {}

    public record Entry(
            int dx,
            int dy,
            int dz,
            String block,
            Map<String, String> state,
            @Nullable String text,
            boolean optional) {
        public Entry(int dx, int dy, int dz, String block) {
            this(dx, dy, dz, block, Map.of(), "", false);
        }

        public Entry(int dx, int dy, int dz, String block, String text) {
            this(dx, dy, dz, block, Map.of(), text, false);
        }

        public Entry(int dx, int dy, int dz, String block, Map<String, String> state) {
            this(dx, dy, dz, block, state, "", false);
        }

        public Entry(int dx, int dy, int dz, String block, Map<String, String> state, @Nullable String text) {
            this(dx, dy, dz, block, state, text, false);
        }

        public Entry {
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            if (block.isBlank()) {
                throw new IllegalArgumentException("Блок схемы пуст: по пустому id блок не поставить");
            }
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
            text = text == null ? "" : text;
        }
    }

    public Schematic {
        Objects.requireNonNull(
                blocks,
                "Список блоков схемы не может быть null: пустую схему задают пустым списком только через парсер с ошибкой");
        Objects.requireNonNull(
                air, "Список клеток воздуха не может быть null: схему без обязательного воздуха задают пустым списком");
        blocks = List.copyOf(blocks);
        air = List.copyOf(air);
    }

    public Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks, List<Cell> air) {
        this(sizeX, sizeY, sizeZ, blocks, air, null);
    }

    public Schematic(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        this(sizeX, sizeY, sizeZ, blocks, List.of(), null);
    }

    /** Повтор сегмента файла: шаг в клетках и условие остановки (число сегментов или целевой мировой y). */
    public record Repeat(int dx, int dy, int dz, Until until) {
        public Repeat {
            Objects.requireNonNull(until, "Условие повтора не может быть null: без него повтор бесконечен");
        }

        public sealed interface Until permits Times, TargetY {}

        public record Times(int count) implements Until {
            public Times {
                if (count < 1) {
                    throw new IllegalArgumentException("Число сегментов " + count + " меньше 1: повторять нечего");
                }
            }
        }

        public record TargetY(int y) implements Until {}

        public boolean descends() {
            return dy < 0;
        }

        // Сколько копий сегмента нужно, чтобы низ последней достиг цели. baseMinY — низ сегмента в файле.
        public int copies(int topY, int baseMinY) {
            if (until instanceof Times times) {
                return times.count();
            }
            int target = ((TargetY) until).y();
            if (dy == 0) {
                throw new IllegalStateException("Повтор до y=" + target + " без шага по y: цель недостижима");
            }
            long start = (long) topY + baseMinY;
            if (dy < 0 ? start <= target : start >= target) {
                // Вход уже на глубине цели (мир GameTest у дна, y≈−60): копать нечего, уровень достигнут.
                return 0;
            }
            long distance = Math.abs((long) target - start);
            long count = distance / Math.abs((long) dy) + 1;
            if (count > CatalogLimits.UNDERGROUND_REPEAT_SIZE_MAX) {
                throw new IllegalStateException("Повтор до y=" + target + " требует " + count
                        + " сегментов: это больше предела схемы; стройте с ближе расположенной целью");
            }
            return (int) count;
        }
    }

    /** Развёрнутая схема и сдвиг, который вернул её координаты в неотрицательные. */
    public record Expanded(Schematic schematic, BlockCoord offset) {}

    public Expanded expand(int topY) {
        if (repeat == null) {
            return new Expanded(this, new BlockCoord(0, 0, 0));
        }
        int copies = repeat.copies(topY, baseMinY());
        if (copies == 0) {
            return new Expanded(
                    new Schematic(sizeX, sizeY, sizeZ, List.of(), List.of(), null), new BlockCoord(0, 0, 0));
        }
        if (copies == 1) {
            return new Expanded(this, new BlockCoord(0, 0, 0));
        }
        Cells cells = repeated(copies);
        Bounds bounds = bounds(cells);
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeY = bounds.maxY() - bounds.minY() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        int limit = CatalogLimits.UNDERGROUND_REPEAT_SIZE_MAX;
        if (sizeX > limit || sizeY > limit || sizeZ > limit) {
            throw new IllegalStateException("Повтор разворачивает схему до " + sizeX + "×" + sizeY + "×" + sizeZ
                    + ", а предел " + limit + ": стройте сегментами или сдвиньте цель");
        }
        int shiftX = -bounds.minX();
        int shiftY = -bounds.minY();
        int shiftZ = -bounds.minZ();
        return new Expanded(
                new Schematic(
                        sizeX,
                        sizeY,
                        sizeZ,
                        shiftedBlocks(cells, shiftX, shiftY, shiftZ),
                        shiftedAir(cells, shiftX, shiftY, shiftZ),
                        null),
                new BlockCoord(shiftX, shiftY, shiftZ));
    }

    private int baseMinY() {
        int baseMinY = Integer.MAX_VALUE;
        for (Entry entry : blocks) {
            baseMinY = Math.min(baseMinY, entry.dy());
        }
        for (Cell cell : air) {
            baseMinY = Math.min(baseMinY, cell.dy());
        }
        return baseMinY == Integer.MAX_VALUE ? 0 : baseMinY;
    }

    private Cells repeated(int copies) {
        Repeat current = Objects.requireNonNull(repeat, "Повтор вызывается только при заданном repeat");
        Map<CellKey, Entry> placed = new LinkedHashMap<>();
        Map<CellKey, Cell> empty = new LinkedHashMap<>();
        for (int copy = 0; copy < copies; copy++) {
            int ox = current.dx() * copy;
            int oy = current.dy() * copy;
            int oz = current.dz() * copy;
            for (Entry entry : blocks) {
                placed.put(
                        new CellKey(entry.dx() + ox, entry.dy() + oy, entry.dz() + oz),
                        new Entry(
                                entry.dx() + ox,
                                entry.dy() + oy,
                                entry.dz() + oz,
                                entry.block(),
                                entry.state(),
                                entry.text(),
                                entry.optional()));
            }
            for (Cell cell : air) {
                CellKey key = new CellKey(cell.dx() + ox, cell.dy() + oy, cell.dz() + oz);
                if (!placed.containsKey(key)) {
                    empty.put(key, new Cell(cell.dx() + ox, cell.dy() + oy, cell.dz() + oz));
                }
            }
        }
        empty.keySet().removeAll(placed.keySet());
        return new Cells(placed, empty);
    }

    private record Cells(Map<CellKey, Entry> blocks, Map<CellKey, Cell> air) {}

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private static Bounds bounds(Cells cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (CellKey key : cells.blocks().keySet()) {
            minX = Math.min(minX, key.x);
            minY = Math.min(minY, key.y);
            minZ = Math.min(minZ, key.z);
            maxX = Math.max(maxX, key.x);
            maxY = Math.max(maxY, key.y);
            maxZ = Math.max(maxZ, key.z);
        }
        for (CellKey key : cells.air().keySet()) {
            minX = Math.min(minX, key.x);
            minY = Math.min(minY, key.y);
            minZ = Math.min(minZ, key.z);
            maxX = Math.max(maxX, key.x);
            maxY = Math.max(maxY, key.y);
            maxZ = Math.max(maxZ, key.z);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<Entry> shiftedBlocks(Cells cells, int dx, int dy, int dz) {
        List<Entry> moved = new ArrayList<>(cells.blocks().size());
        for (Entry entry : cells.blocks().values()) {
            moved.add(new Entry(
                    entry.dx() + dx,
                    entry.dy() + dy,
                    entry.dz() + dz,
                    entry.block(),
                    entry.state(),
                    entry.text(),
                    entry.optional()));
        }
        return moved;
    }

    private static List<Cell> shiftedAir(Cells cells, int dx, int dy, int dz) {
        List<Cell> moved = new ArrayList<>(cells.air().size());
        for (Cell cell : cells.air().values()) {
            moved.add(new Cell(cell.dx() + dx, cell.dy() + dy, cell.dz() + dz));
        }
        return moved;
    }

    private record CellKey(int x, int y, int z) {}

    public List<BlockCoord> airCells(BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, где очищать");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него очистится не та клетка");
        List<BlockCoord> cells = new ArrayList<>(air.size());
        for (Cell cell : air) {
            BuildRotation.Cell rotated = rotation.rotate(cell.dx(), cell.dz(), sizeX, sizeZ);
            cells.add(new BlockCoord(origin.x() + rotated.x(), origin.y() + cell.dy(), origin.z() + rotated.z()));
        }
        return List.copyOf(cells);
    }

    public List<PlacedEntry> entries(BlockCoord origin) {
        return entries(origin, BuildRotation.NONE);
    }

    public List<PlacedEntry> entries(BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(origin, "Якорь схемы не может быть null: без него неизвестно, куда ставить");
        Objects.requireNonNull(rotation, "Поворот схемы не может быть null: без него блок встанет не туда");
        List<PlacedEntry> placed = new ArrayList<>(blocks.size());
        for (Entry entry : blocks) {
            BuildRotation.Cell cell = rotation.rotate(entry.dx(), entry.dz(), sizeX, sizeZ);
            placed.add(new PlacedEntry(
                    new BlockCoord(origin.x() + cell.x(), origin.y() + entry.dy(), origin.z() + cell.z()),
                    entry.block(),
                    entry.state(),
                    entry.text(),
                    entry.optional()));
        }
        placed.sort(Comparator.comparingInt((PlacedEntry e) -> e.pos().y())
                .thenComparingInt(e -> placementRank(e.block()))
                .thenComparingInt(e -> e.pos().x())
                .thenComparingInt(e -> e.pos().z()));
        return List.copyOf(placed);
    }

    private static int placementRank(String block) {
        String path = block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
        if (path.endsWith("_sign")) {
            return 2;
        }
        return "chest".equals(path) || "trapped_chest".equals(path) || path.endsWith("_chest") ? 1 : 0;
    }

    public record PlacedEntry(
            BlockCoord pos,
            String block,
            Map<String, String> state,
            @Nullable String text,
            boolean optional) {
        public PlacedEntry(BlockCoord pos, String block) {
            this(pos, block, Map.of(), "", false);
        }

        public PlacedEntry(BlockCoord pos, String block, String text) {
            this(pos, block, Map.of(), text, false);
        }

        public PlacedEntry(BlockCoord pos, String block, Map<String, String> state) {
            this(pos, block, state, "", false);
        }

        public PlacedEntry {
            Objects.requireNonNull(pos, "Позиция блока схемы не может быть null: без неё неизвестно, куда ставить");
            Objects.requireNonNull(block, "Блок схемы не может быть null: без него неизвестно, что ставить");
            Objects.requireNonNull(state, "Состояние блока схемы не может быть null: пустое задаётся пустой картой");
            state = Map.copyOf(state);
            text = text == null ? "" : text;
        }
    }
}
