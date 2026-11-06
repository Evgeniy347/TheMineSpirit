package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class DoorPassage {
    private DoorPassage() {}

    static Optional<String> problem(Schematic schematic, SchematicWalk.Result walk) {
        Set<BlockCoord> doors = new HashSet<>();
        for (Schematic.Entry block : schematic.blocks()) {
            if (SchematicBlocks.isDoor(block.block())) {
                doors.add(new BlockCoord(block.dx(), block.dy(), block.dz()));
            }
        }
        for (Schematic.Entry block : schematic.blocks()) {
            BlockCoord door = new BlockCoord(block.dx(), block.dy(), block.dz());
            if (!SchematicBlocks.isDoor(block.block())
                    || doors.contains(new BlockCoord(door.x(), door.y() - 1, door.z()))
                    || !walk.feet().contains(door)) {
                continue;
            }
            Optional<Facing> facing = Facing.of(block.state().getOrDefault("facing", "north"));
            if (facing.isEmpty()) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                BlockCoord beyond = new BlockCoord(
                        door.x() + facing.get().dx() * side,
                        door.y(),
                        door.z() + facing.get().dz() * side);
                if (!walk.feet().contains(beyond)
                        && !walk.feet().contains(new BlockCoord(beyond.x(), beyond.y() - 1, beyond.z()))
                        && !walk.feet().contains(new BlockCoord(beyond.x(), beyond.y() + 1, beyond.z()))) {
                    return Optional.of("дверь [" + door.x() + "," + door.y() + "," + door.z() + "] ведёт в тупик: "
                            + "по ту сторону, в клетке [" + beyond.x() + "," + beyond.y() + "," + beyond.z()
                            + "], не встать (стена, забор, перила или мебель вплотную) — поиск пути заводит духа в "
                            + "проём, и он встаёт там; за дверью нужна свободная клетка с полом (балкон — глубже "
                            + "перил хотя бы на ряд)");
                }
            }
        }
        return Optional.empty();
    }
}
