package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record WarehouseLayout(
        Schematic schematic, List<ChestStack> stacks, List<Sign> signs, int doubleChests, int interiorHeight) {

    public record ChestStack(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {}

    public record Sign(BlockCoord pos, String text) {}

    public WarehouseLayout {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё нечего размечать");
        stacks = List.copyOf(stacks);
        signs = List.copyOf(signs);
        if (doubleChests < 0) {
            throw new IllegalArgumentException("Двойных сундуков " + doubleChests + ": число не бывает отрицательным");
        }
        if (interiorHeight < 0) {
            throw new IllegalArgumentException("Высота стен " + interiorHeight + ": высота не бывает отрицательной");
        }
    }

    public static WarehouseLayout of(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё нечего размечать");
        List<DoubleChest> doubles = doubleChests(schematic);
        List<ChestStack> stacks = stacks(doubles);
        List<Sign> signs = signs(schematic);
        return new WarehouseLayout(schematic, stacks, signs, doubles.size(), structuralHeight(schematic));
    }

    public Optional<Sign> signOf(ChestStack stack) {
        Sign best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Sign sign : signs) {
            int dx = Math.max(
                    0, Math.max(stack.minX() - sign.pos().x(), sign.pos().x() - stack.maxX()));
            int dz = Math.max(
                    0, Math.max(stack.minZ() - sign.pos().z(), sign.pos().z() - stack.maxZ()));
            int dy = sign.pos().y() - stack.maxY();
            if (dy < 1 || dy > 3 || dx > 2 || dz > 2) {
                continue;
            }
            int distance = dx + dz + dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sign;
            }
        }
        return Optional.ofNullable(best);
    }

    public List<ChestStack> stacksWithoutSign() {
        List<ChestStack> missing = new ArrayList<>();
        for (ChestStack stack : stacks) {
            if (signOf(stack).isEmpty()) {
                missing.add(stack);
            }
        }
        return List.copyOf(missing);
    }

    private static List<DoubleChest> doubleChests(Schematic schematic) {
        Map<Integer, List<Schematic.Entry>> byLevel = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (isChest(entry.block())) {
                byLevel.computeIfAbsent(entry.dy(), y -> new ArrayList<>()).add(entry);
            }
        }
        List<DoubleChest> doubles = new ArrayList<>();
        for (Map.Entry<Integer, List<Schematic.Entry>> level : byLevel.entrySet()) {
            Set<Schematic.Entry> unvisited = new LinkedHashSet<>(level.getValue());
            while (!unvisited.isEmpty()) {
                Schematic.Entry start = unvisited.iterator().next();
                List<Schematic.Entry> component = new ArrayList<>();
                collect(start, unvisited, component);
                for (List<Schematic.Entry> pair : pairs(component, level.getKey())) {
                    int minX = pair.stream().mapToInt(Schematic.Entry::dx).min().orElseThrow();
                    int maxX = pair.stream().mapToInt(Schematic.Entry::dx).max().orElseThrow();
                    int minZ = pair.stream().mapToInt(Schematic.Entry::dz).min().orElseThrow();
                    int maxZ = pair.stream().mapToInt(Schematic.Entry::dz).max().orElseThrow();
                    doubles.add(new DoubleChest(minX, maxX, minZ, maxZ, level.getKey(), level.getKey()));
                }
            }
        }
        return List.copyOf(doubles);
    }

    private static List<List<Schematic.Entry>> pairs(List<Schematic.Entry> component, int y) {
        List<Schematic.Entry> ordered = new ArrayList<>(component);
        boolean sameX =
                ordered.stream().allMatch(entry -> entry.dx() == ordered.get(0).dx());
        boolean sameZ =
                ordered.stream().allMatch(entry -> entry.dz() == ordered.get(0).dz());
        if (!sameX && !sameZ) {
            throw new IllegalStateException("Сундуки на уровне y=" + y + " образуют фигуру, а не ряд: "
                    + "двойной сундук — два соседних блока вдоль одной стены");
        }
        ordered.sort(
                sameZ ? Comparator.comparingInt(Schematic.Entry::dx) : Comparator.comparingInt(Schematic.Entry::dz));
        if (ordered.size() % 2 != 0) {
            throw new IllegalStateException("Сундуков на уровне y=" + y + " нечётное число " + ordered.size()
                    + ": двойные сундуки складываются из пар, одиночный сундук нарушил бы стопку");
        }
        List<List<Schematic.Entry>> pairs = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index += 2) {
            pairs.add(List.of(ordered.get(index), ordered.get(index + 1)));
        }
        return List.copyOf(pairs);
    }

    private static void collect(
            Schematic.Entry start, Set<Schematic.Entry> unvisited, List<Schematic.Entry> component) {
        if (!unvisited.remove(start)) {
            return;
        }
        component.add(start);
        for (Schematic.Entry other : List.copyOf(unvisited)) {
            int dx = Math.abs(other.dx() - start.dx());
            int dz = Math.abs(other.dz() - start.dz());
            if (dx + dz == 1) {
                collect(other, unvisited, component);
            }
        }
    }

    private static List<ChestStack> stacks(List<DoubleChest> doubles) {
        Map<Footprint, List<DoubleChest>> byFootprint = new LinkedHashMap<>();
        for (DoubleChest chest : doubles) {
            byFootprint
                    .computeIfAbsent(
                            new Footprint(chest.minX(), chest.maxX(), chest.minZ(), chest.maxZ()),
                            key -> new ArrayList<>())
                    .add(chest);
        }
        List<ChestStack> stacks = new ArrayList<>();
        for (Map.Entry<Footprint, List<DoubleChest>> group : byFootprint.entrySet()) {
            List<DoubleChest> ordered = new ArrayList<>(group.getValue());
            ordered.sort(Comparator.comparingInt(DoubleChest::minY));
            int minY = ordered.get(0).minY();
            int maxY = ordered.get(ordered.size() - 1).maxY();
            for (int index = 1; index < ordered.size(); index++) {
                if (ordered.get(index).minY() != ordered.get(index - 1).maxY() + 1) {
                    throw new IllegalStateException("Двойные сундуки стопки в " + group.getKey()
                            + " стоят не друг на друге: разрыв по высоте между y="
                            + ordered.get(index - 1).maxY() + " и y="
                            + ordered.get(index).minY());
                }
            }
            stacks.add(new ChestStack(
                    group.getKey().minX(),
                    group.getKey().maxX(),
                    group.getKey().minZ(),
                    group.getKey().maxZ(),
                    minY,
                    maxY));
        }
        stacks.sort(Comparator.comparingInt(ChestStack::minZ)
                .thenComparingInt(ChestStack::minX)
                .thenComparingInt(ChestStack::minY));
        return List.copyOf(stacks);
    }

    private static List<Sign> signs(Schematic schematic) {
        List<Sign> signs = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (isSign(entry.block())) {
                String text = entry.text();
                if (text == null || text.isBlank()) {
                    throw new IllegalStateException("Табличка в [" + entry.dx() + "," + entry.dy() + "," + entry.dz()
                            + "] без категории: табличка склада обязана называть категорию");
                }
                signs.add(new Sign(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), text));
            }
        }
        signs.sort(Comparator.comparingInt((Sign sign) -> sign.pos().y())
                .thenComparingInt(sign -> sign.pos().x())
                .thenComparingInt(sign -> sign.pos().z()));
        return List.copyOf(signs);
    }

    private static int structuralHeight(Schematic schematic) {
        int roof = 0;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (!isSign(entry.block())) {
                roof = Math.max(roof, entry.dy());
            }
        }
        return Math.max(0, roof - 1);
    }

    private static boolean isChest(String block) {
        return path(block).endsWith("_chest") || "chest".equals(path(block)) || "trapped_chest".equals(path(block));
    }

    private static boolean isSign(String block) {
        return path(block).endsWith("_sign");
    }

    private static String path(String block) {
        return block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
    }

    private record Footprint(int minX, int maxX, int minZ, int maxZ) {}

    private record DoubleChest(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {}
}
