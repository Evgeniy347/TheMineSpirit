package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Двойная дверь (владелец 20.10): петли створок — по краям проёма, ручки — посередине рядом. Иначе открытые створки
 * встают посреди прохода. Ряд створок вдоль стены делится на пары с начала ряда (портал на четыре створки — две пары).
 * Петля {@code hinge=left} — со стороны {@code facing} против часовой, {@code right} — по часовой: так игра ставит
 * петли, когда вторую створку ставят рядом с первой.
 */
public final class DoorPairs {
    private DoorPairs() {}

    public static Optional<String> problem(Schematic schematic) {
        Map<BlockCoord, Schematic.Entry> doors = new HashMap<>();
        for (Schematic.Entry block : schematic.blocks()) {
            if (SchematicBlocks.isDoor(block.block())
                    && !"upper".equals(block.state().get("half"))) {
                doors.put(new BlockCoord(block.dx(), block.dy(), block.dz()), block);
            }
        }
        for (Map.Entry<BlockCoord, Schematic.Entry> door : doors.entrySet()) {
            Optional<Facing> facing = facing(door.getValue());
            if (facing.isEmpty()) {
                continue;
            }
            Facing along = facing.get().clockwise();
            if (sameDoor(doors, step(door.getKey(), along, -1), facing.get())) {
                continue;
            }
            List<BlockCoord> run = new ArrayList<>();
            for (BlockCoord cell = door.getKey(); sameDoor(doors, cell, facing.get()); cell = step(cell, along, 1)) {
                run.add(cell);
            }
            for (int i = 0; i + 1 < run.size(); i += 2) {
                Optional<String> pair = pair(doors, run.get(i), run.get(i + 1), facing.get(), along);
                if (pair.isPresent()) {
                    return pair;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> pair(
            Map<BlockCoord, Schematic.Entry> doors, BlockCoord first, BlockCoord second, Facing facing, Facing along) {
        Schematic.Entry firstDoor = Objects.requireNonNull(doors.get(first), "Створка ряда пропала из карты дверей");
        Schematic.Entry secondDoor = Objects.requireNonNull(doors.get(second), "Створка ряда пропала из карты дверей");
        if (hingeSide(firstDoor, facing) == along.opposite() && hingeSide(secondDoor, facing) == along) {
            return Optional.empty();
        }
        return Optional.of("у двойной двери [" + first.x() + "," + first.y() + "," + first.z() + "]–[" + second.x()
                + "," + second.y() + "," + second.z() + "] петли не по краям проёма: ручки створок должны сходиться "
                + "посередине, иначе открытые створки встают посреди прохода — у первой створки hinge="
                + (along.opposite() == facing.clockwise() ? "right" : "left") + ", у второй hinge="
                + (along == facing.clockwise() ? "right" : "left"));
    }

    private static Facing hingeSide(Schematic.Entry door, Facing facing) {
        return "right".equals(door.state().get("hinge")) ? facing.clockwise() : facing.counterClockwise();
    }

    private static Optional<Facing> facing(Schematic.Entry door) {
        return Facing.of(door.state().getOrDefault("facing", "north"));
    }

    private static boolean sameDoor(Map<BlockCoord, Schematic.Entry> doors, BlockCoord cell, Facing facing) {
        Schematic.Entry door = doors.get(cell);
        return door != null && facing(door).equals(Optional.of(facing));
    }

    private static BlockCoord step(BlockCoord cell, Facing along, int k) {
        return new BlockCoord(cell.x() + along.dx() * k, cell.y(), cell.z() + along.dz() * k);
    }
}
