package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;

final class Pictures {
    static final BlockCoord FEET = new BlockCoord(10, 64, -5);
    static final String SOLID_LAYER = "###/###/###";
    static final String OPEN_LAYER = ".../.../...";

    record Picture(LocalSurroundings surroundings, SafetyView world) {}

    private Pictures() {}

    static Picture picture(String floor, String feet, String head, BodyFlags flags) {
        return picture(floor, feet, head, flags, Optional.empty());
    }

    static Picture picture(String floor, String feet, String head, BodyFlags flags, Optional<BlockCoord> water) {
        AsciiWorld world = AsciiWorld.of(
                new BlockCoord(FEET.x() - 1, FEET.y() - 1, FEET.z() - 1), Cell.PASSABLE, floor, feet, head);
        return new Picture(LocalSurroundings.read(FEET, world, flags, water), world);
    }

    static Picture pictureWithAbove(String floor, String feet, String head, String above, BodyFlags flags) {
        AsciiWorld world = AsciiWorld.of(
                new BlockCoord(FEET.x() - 1, FEET.y() - 1, FEET.z() - 1), Cell.PASSABLE, floor, feet, head, above);
        return new Picture(LocalSurroundings.read(FEET, world, flags, Optional.empty()), world);
    }

    static LocalSurroundings of(String floor, String feet, String head, BodyFlags flags) {
        return picture(floor, feet, head, flags).surroundings();
    }

    static LocalSurroundings of(String floor, String feet, String head, BodyFlags flags, Optional<BlockCoord> water) {
        return picture(floor, feet, head, flags, water).surroundings();
    }

    static BlockCoord at(int dx, int dy, int dz) {
        return new BlockCoord(FEET.x() + dx, FEET.y() + dy, FEET.z() + dz);
    }
}
