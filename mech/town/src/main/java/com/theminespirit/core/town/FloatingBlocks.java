package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FloatingBlocks {
    public enum Kind {
        FENCE,
        CHEST,
        REGISTERED_CHEST,
        STATION,
        STUMP,
        TREE,
        OTHER
    }

    public record Finding(BlockCoord at, Kind kind, String blockId, int height, String reason, List<BlockCoord> cells) {
        public Finding(BlockCoord at, Kind kind, String blockId, int height, String reason) {
            this(at, kind, blockId, height, reason, List.of(at));
        }

        public Finding {
            Objects.requireNonNull(at, "Клетка находки не может быть null: по ней её снимают");
            Objects.requireNonNull(kind, "Вид находки не может быть null: от него зависит уборка");
            Objects.requireNonNull(blockId, "Имя блока не может быть null: по нему выбирают инструмент");
            Objects.requireNonNull(reason, "Причина не может быть null: её показывает аудит");
            Objects.requireNonNull(cells, "Клетки находки не могут быть null: иначе нечего убирать");
            if (!cells.contains(at)) {
                throw new IllegalArgumentException("Клетка находки " + at + " обязана входить в её клетки " + cells
                        + ": иначе уборка начнётся не с того блока");
            }
        }
    }

    public interface World {
        String blockId(BlockCoord at);

        boolean natural(String blockId);

        boolean planned(BlockCoord at);

        boolean log(BlockCoord at);

        boolean treeLog(BlockCoord at);

        boolean supported(BlockCoord at);

        boolean registeredChest(BlockCoord at);

        int groundY(int x, int z);
    }

    private static final Set<String> STATIONS = Set.of(
            "furnace",
            "blast_furnace",
            "smoker",
            "crafting_table",
            "smithing_table",
            "anvil",
            "chipped_anvil",
            "damaged_anvil",
            "cartography_table",
            "fletching_table",
            "grindstone",
            "loom",
            "stonecutter",
            "enchanting_table",
            "brewing_stand");
    private static final String AIR = "minecraft:air";

    private FloatingBlocks() {}

    public static List<Finding> find(ColumnMask area, int bottom, int top, World world) {
        Objects.requireNonNull(area, "Площадь поиска не может быть null: висящие ищут только на участке");
        Objects.requireNonNull(world, "Мир не может быть null: без него опору и план не прочитать");
        if (bottom > top) {
            throw new IllegalArgumentException(
                    "Низ " + bottom + " выше верха " + top + ": вверх ногами участок не сканируется");
        }
        List<Finding> found = new ArrayList<>();
        for (ColumnMask.Cell column : area.cells()) {
            for (int y = bottom; y <= top; y++) {
                classify(new BlockCoord(column.x(), y, column.z()), world).ifPresent(found::add);
            }
        }
        return List.copyOf(found);
    }

    public static Optional<Finding> classify(BlockCoord at, World world) {
        Objects.requireNonNull(at, "Клетка не может быть null: её классифицируют");
        Objects.requireNonNull(world, "Мир не может быть null: без него блок и опору не прочитать");
        String id = world.blockId(at);
        if (id == null || AIR.equals(id) || world.planned(at)) {
            return Optional.empty();
        }
        boolean log = world.log(at);
        boolean leaf = pathOf(id).endsWith("_leaves");
        if (!log && !leaf && world.natural(id)) {
            return Optional.empty();
        }
        boolean tree = (log || leaf) && floatingTree(at, world);
        if (!tree) {
            boolean stump = log && !world.treeLog(at);
            if (!stump && world.supported(at)) {
                return Optional.empty();
            }
        }
        boolean registered = chestPath(pathOf(id)) && world.registeredChest(at);
        Kind kind = tree ? Kind.TREE : kind(id, log, registered);
        int height = Math.max(0, at.y() - world.groundY(at.x(), at.z()) - 1);
        return Optional.of(new Finding(at, kind, id, height, reason(kind, height)));
    }

    public static Kind kind(String blockId, boolean log, boolean registered) {
        if (log) {
            return Kind.STUMP;
        }
        String path = pathOf(blockId);
        if (fence(path)) {
            return Kind.FENCE;
        }
        if (chestPath(path)) {
            return registered ? Kind.REGISTERED_CHEST : Kind.CHEST;
        }
        return STATIONS.contains(path) ? Kind.STATION : Kind.OTHER;
    }

    public static boolean countedInGraph(Finding finding) {
        Objects.requireNonNull(finding, "Находка не может быть null: по её виду решают, входит ли она в граф");
        return switch (finding.kind()) {
            case CHEST, REGISTERED_CHEST, STATION -> false;
            case FENCE, STUMP, TREE, OTHER -> true;
        };
    }

    public static List<Finding> countedInGraph(List<Finding> findings) {
        Objects.requireNonNull(findings, "Находки не могут быть null: пусто — пустой список");
        return findings.stream().filter(FloatingBlocks::countedInGraph).toList();
    }

    public static int hangingCount(List<Finding> findings) {
        return countedInGraph(findings).size();
    }

    public static String pathOf(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: путь берётся из него");
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }

    private static boolean chestPath(String path) {
        return "chest".equals(path) || "trapped_chest".equals(path) || "barrel".equals(path) || path.endsWith("_chest");
    }

    private static boolean fence(String path) {
        return "fence".equals(path)
                || "fence_gate".equals(path)
                || path.endsWith("_fence")
                || path.endsWith("_fence_gate");
    }

    private static boolean floatingTree(BlockCoord at, World world) {
        Set<BlockCoord> logs = new LinkedHashSet<>();
        Set<BlockCoord> leaves = new LinkedHashSet<>();
        if (world.log(at)) {
            logs.add(at);
        } else if (isLeaves(at, world)) {
            leaves.add(at);
        } else {
            return false;
        }
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(at);
        queue.add(at);
        while (!queue.isEmpty()) {
            BlockCoord current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockCoord next = new BlockCoord(current.x() + dx, current.y() + dy, current.z() + dz);
                        if (!seen.add(next)) {
                            continue;
                        }
                        if (world.log(next)) {
                            if (logs.size() >= 128) {
                                return false;
                            }
                            logs.add(next);
                            queue.add(next);
                        } else if (isLeaves(next, world)) {
                            if (leaves.size() >= 512) {
                                return false;
                            }
                            leaves.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        Set<BlockCoord> anchors = logs.isEmpty() ? leaves : logs;
        if (anchors.isEmpty()) {
            return false;
        }
        return anchors.stream().noneMatch(cell -> {
            BlockCoord under = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
            return !logs.contains(under) && !leaves.contains(under) && world.supported(cell);
        });
    }

    private static boolean isLeaves(BlockCoord at, World world) {
        String id = world.blockId(at);
        return id != null && pathOf(id).endsWith("_leaves");
    }

    private static String reason(Kind kind, int height) {
        if (kind == Kind.STUMP) {
            return "бревно без дерева, вне плана";
        }
        if (kind == Kind.TREE) {
            return "часть висящего дерева (ствол или листва), вне плана";
        }
        String place = "висит на высоте " + height + " над землёй, вне плана";
        if (kind == Kind.REGISTERED_CHEST) {
            return place + ", сундук в реестре поселения";
        }
        return place;
    }
}
