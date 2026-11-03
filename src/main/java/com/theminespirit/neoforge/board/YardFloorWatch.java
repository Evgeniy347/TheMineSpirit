package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.core.town.YardFloor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

final class YardFloorWatch {
    private static final int MAX_DEPTH = 12;

    private YardFloorWatch() {}

    static TownSnapshot.Progress progress(ServerLevel level, StageSite site) {
        if (!loaded(level, site)) {
            return TownSnapshot.Progress.NONE;
        }
        YardFloor.Audit found = audit(level, site);
        return new TownSnapshot.Progress(found.supported(), found.stations().size(), found.damaged());
    }

    static boolean loaded(ServerLevel level, StageSite site) {
        var yard = site.yard();
        int y = site.anchor().y();
        for (int x = yard.x(); x < yard.x() + yard.width(); x++) {
            for (int z = yard.z(); z < yard.z() + yard.depth(); z++) {
                for (int dy = -3; dy <= 1; dy++) {
                    if (!level.isLoaded(new BlockPos(x, y + dy, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static YardFloor.Audit audit(ServerLevel level, StageSite site) {
        List<BlockCoord> stations = new ArrayList<>();
        var yard = site.yard();
        int y = site.anchor().y();
        for (int x = yard.x(); x < yard.x() + yard.width(); x++) {
            for (int z = yard.z(); z < yard.z() + yard.depth(); z++) {
                for (int dy = -3; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(x, y + dy, z);
                    if (station(block(level, cell))) {
                        stations.add(cell);
                    }
                }
            }
        }
        return YardFloor.of(stations, MAX_DEPTH, cell -> {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        });
    }

    private static boolean station(String block) {
        return block.endsWith("chest") || block.endsWith("crafting_table") || block.endsWith("furnace");
    }

    private static String block(ServerLevel level, BlockCoord cell) {
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .getBlock())
                .toString();
    }
}
