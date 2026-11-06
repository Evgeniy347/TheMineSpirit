package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.YardStations;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

final class Stations {
    private static final int RADIUS = 8;
    private static final int DOWN = 3;
    private static final int UP = 2;

    private Stations() {}

    static BlockCoord at(ServerLevel level, BoardCase caseValue, StageSite stage) {
        Objects.requireNonNull(level, "Мир не может быть null: станок ищется в мире");
        Objects.requireNonNull(caseValue, "Кейс не может быть null: от него зависит, нужен ли станок");
        if (!crafts(caseValue)) {
            return stage.anchor();
        }
        BlockCoord near = stage.workYard();
        List<BlockCoord> found = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -DOWN; dy <= UP; dy++) {
                    BlockCoord at = new BlockCoord(near.x() + dx, near.y() + dy, near.z() + dz);
                    if (isTable(level, at)) {
                        found.add(at);
                    }
                }
            }
        }
        return YardStations.nearest(found, near, stage.anchor());
    }

    private static boolean crafts(BoardCase caseValue) {
        return caseValue.name().startsWith("CRAFT_")
                && caseValue != BoardCase.CRAFTING_STATION
                && caseValue != BoardCase.CRAFT_FURNACE
                && TicketPlans.craftItem(caseValue).isPresent();
    }

    private static boolean isTable(ServerLevel level, BlockCoord at) {
        return "minecraft:crafting_table"
                .equals(BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(new BlockPos(at.x(), at.y(), at.z()))
                                .getBlock())
                        .toString());
    }
}
