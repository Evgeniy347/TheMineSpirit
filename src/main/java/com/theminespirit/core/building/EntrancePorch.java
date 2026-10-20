package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Крыльцо у входа в дом (владелец 19–20.10, образец — рисунок крыльца в задаче поселения): у фундамента перед
 * дверью — ряд ступенек: под каждой створкой прямая ступенька {@code facing} к дому (высокая половина у стены), по
 * краям — по ступеньке {@code facing} к середине. Крайние игра делает угловыми ({@code shape=outer_*}), и этот же
 * {@code shape} записан в легенде, иначе сверка стройки увидит расхождение. Подняться можно спереди и с боков.
 */
public final class EntrancePorch {
    private EntrancePorch() {}

    public static Optional<String> problem(Schematic schematic, SchematicBody body, BlockCoord entry) {
        Optional<SchematicBody.Normal> normal = body.outwardNormal(entry);
        if (normal.isEmpty() || entry.y() < 1) {
            return Optional.empty();
        }
        Map<BlockCoord, Schematic.Entry> grid = new HashMap<>();
        for (Schematic.Entry block : schematic.blocks()) {
            grid.put(new BlockCoord(block.dx(), block.dy(), block.dz()), block);
        }
        Facing outward = Facing.ofVector(normal.get().dx(), normal.get().dz()).orElseThrow();
        Facing along = outward.clockwise();
        Facing toHouse = outward.opposite();
        int low = 0;
        while (isDoor(grid, shift(entry, along, -(low + 1)))) {
            low++;
        }
        int high = 0;
        while (isDoor(grid, shift(entry, along, high + 1))) {
            high++;
        }
        for (int k = -low; k <= high; k++) {
            Optional<String> step = step(grid, porchCell(entry, along, k, outward), toHouse, "straight");
            if (step.isPresent()) {
                return step;
            }
        }
        Optional<String> first = step(grid, porchCell(entry, along, -low - 1, outward), along, outer(along, toHouse));
        if (first.isPresent()) {
            return first;
        }
        return step(
                grid, porchCell(entry, along, high + 1, outward), along.opposite(), outer(along.opposite(), toHouse));
    }

    // mc: StairBlock — ступенька, смотрящая на соседнюю ступеньку с перпендикулярным facing, становится outer_left,
    // если
    // facing соседа — против часовой от её facing, иначе outer_right.
    static String outer(Facing facing, Facing neighbour) {
        return neighbour == facing.counterClockwise() ? "outer_left" : "outer_right";
    }

    private static Optional<String> step(
            Map<BlockCoord, Schematic.Entry> grid, BlockCoord cell, Facing facing, String shape) {
        Schematic.Entry block = grid.get(cell);
        String want = "ступенька facing=" + facing.name().toLowerCase(Locale.ROOT) + ", half=bottom"
                + ("straight".equals(shape) ? "" : ", shape=" + shape);
        String where = "[" + cell.x() + "," + cell.y() + "," + cell.z() + "]";
        if (block == null || !SchematicBlocks.path(block.block()).endsWith("_stairs")) {
            return Optional.of("крыльцо: в клетке " + where + " у фундамента перед дверью нужна " + want
                    + " — три ступеньки (под широкой дверью средних столько, какова её ширина), подняться спереди "
                    + "и с боков; сейчас " + (block == null ? "пусто" : block.block()));
        }
        Map<String, String> state = block.state();
        boolean ok = facing.name().toLowerCase(Locale.ROOT).equals(state.get("facing"))
                && !"top".equals(state.get("half"))
                && shape.equals(state.getOrDefault("shape", "straight"));
        return ok
                ? Optional.empty()
                : Optional.of("крыльцо: в клетке " + where + " нужна " + want + " (игра сама сделает такую форму, "
                        + "а сверка стройки сравнит её с легендой); сейчас " + block.block() + state);
    }

    private static boolean isDoor(Map<BlockCoord, Schematic.Entry> grid, BlockCoord cell) {
        Schematic.Entry block = grid.get(cell);
        return block != null && SchematicBlocks.isDoor(block.block());
    }

    private static BlockCoord shift(BlockCoord cell, Facing along, int k) {
        return new BlockCoord(cell.x() + along.dx() * k, cell.y(), cell.z() + along.dz() * k);
    }

    private static BlockCoord porchCell(BlockCoord entry, Facing along, int k, Facing outward) {
        BlockCoord door = shift(entry, along, k);
        return new BlockCoord(door.x() + outward.dx(), entry.y() - 1, door.z() + outward.dz());
    }
}
