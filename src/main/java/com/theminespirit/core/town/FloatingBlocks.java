package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
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
        OTHER
    }

    public record Finding(BlockCoord at, Kind kind, String blockId, int height, String reason) {
        public Finding {
            Objects.requireNonNull(at, "Клетка находки не может быть null: по ней её снимают");
            Objects.requireNonNull(kind, "Вид находки не может быть null: от него зависит уборка");
            Objects.requireNonNull(blockId, "Имя блока не может быть null: по нему выбирают инструмент");
            Objects.requireNonNull(reason, "Причина не может быть null: её показывает аудит");
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
        if (!log && world.natural(id)) {
            return Optional.empty();
        }
        boolean stump = log && !world.treeLog(at);
        if (!stump && world.supported(at)) {
            return Optional.empty();
        }
        boolean registered = chestPath(pathOf(id)) && world.registeredChest(at);
        Kind kind = kind(id, log, registered);
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

    private static String reason(Kind kind, int height) {
        if (kind == Kind.STUMP) {
            return "бревно без дерева, вне плана";
        }
        String place = "висит на высоте " + height + " над землёй, вне плана";
        if (kind == Kind.REGISTERED_CHEST) {
            return place + ", сундук в реестре поселения";
        }
        return place;
    }
}
