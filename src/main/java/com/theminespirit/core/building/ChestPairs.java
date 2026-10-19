package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Пары сундуков схемы. Сундук, у которого рядом по горизонтали стоит сундук, обязан быть половиной двойного:
 * {@code type=left|right}, сосед по стороне соединения — тот же сундук с тем же {@code facing} и противоположным
 * {@code type}. Иначе в мире встают два одинарных сундука рядом — склад выглядит двойным, а открывается одинарным.
 * Сторона соединения — как в ванили: у {@code left} сосед по часовой от {@code facing}, у {@code right} — против.
 */
public final class ChestPairs {
    private static final String SINGLE = "single";

    private ChestPairs() {}

    public static Optional<String> problem(Schematic schematic) {
        Map<BlockCoord, Schematic.Entry> chests = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (SchematicBlocks.isChest(entry.block())) {
                chests.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry);
            }
        }
        for (Map.Entry<BlockCoord, Schematic.Entry> chest : chests.entrySet()) {
            Optional<String> problem = problem(chest.getKey(), chest.getValue(), chests);
            if (problem.isPresent()) {
                return problem;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> problem(
            BlockCoord cell, Schematic.Entry chest, Map<BlockCoord, Schematic.Entry> chests) {
        String type = chest.state().getOrDefault("type", SINGLE);
        Facing facing = Facing.of(chest.state().getOrDefault("facing", "north")).orElse(Facing.NORTH);
        String where = "сундук '" + chest.block() + "' в клетке [" + cell.x() + "," + cell.y() + "," + cell.z() + "]";
        if (SINGLE.equals(type)) {
            for (Facing side : Facing.values()) {
                if (chests.containsKey(neighbour(cell, side))) {
                    return Optional.of(where + " стоит рядом с другим сундуком, но одинарный (type=single): в мире "
                            + "встанут два одинарных вместо двойного — задайте паре type=left/right и общий facing");
                }
            }
            return Optional.empty();
        }
        if (!"left".equals(type) && !"right".equals(type)) {
            return Optional.of(where + " с type=" + type + ": допустимы single, left, right");
        }
        Facing join = "left".equals(type) ? facing.clockwise() : facing.counterClockwise();
        Schematic.Entry partner = chests.get(neighbour(cell, join));
        String opposite = "left".equals(type) ? "right" : "left";
        if (partner == null
                || !partner.block().equals(chest.block())
                || !partner.state()
                        .getOrDefault("facing", "north")
                        .equals(chest.state().getOrDefault("facing", "north"))
                || !opposite.equals(partner.state().getOrDefault("type", SINGLE))) {
            return Optional.of(where + " с type=" + type + " и facing="
                    + facing.name().toLowerCase(Locale.ROOT)
                    + " ждёт пару со стороны " + join.name().toLowerCase(Locale.ROOT)
                    + " (тот же сундук, тот же facing, type=" + opposite + "), а её нет: двойной сундук не сложится");
        }
        return Optional.empty();
    }

    private static BlockCoord neighbour(BlockCoord cell, Facing side) {
        return new BlockCoord(cell.x() + side.dx(), cell.y(), cell.z() + side.dz());
    }
}
