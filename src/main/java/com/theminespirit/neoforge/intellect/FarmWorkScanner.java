package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.LevelFarmView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class FarmWorkScanner {
    public static final int FARM_SCAN_RADIUS = 8;
    public static final int FARM_SCAN_HEIGHT = 2;
    public static final int FARM_SCAN_BUDGET = 4096;
    private static final int READS_PER_TILE = 2;

    private FarmWorkScanner() {
    }

    public static FarmWork read(ServerLevel level, Optional<Point> home, List<SpiritEntity> members, int seeds) {
        if (level == null || home == null || members == null) {
            throw new IllegalArgumentException(
                    "Работа фермы без уровня, дома или духов не читается: снимок зависит от всех сразу");
        }
        BlockPos center = centerOf(home, members);
        if (center == null) {
            return new FarmWork(0, 0, seeds);
        }
        LevelFarmView view = new LevelFarmView(level);
        int reads = 0;
        int mature = 0;
        int farmland = 0;
        for (int dx = -FARM_SCAN_RADIUS; dx <= FARM_SCAN_RADIUS; dx++) {
            for (int dz = -FARM_SCAN_RADIUS; dz <= FARM_SCAN_RADIUS; dz++) {
                for (int dy = -FARM_SCAN_HEIGHT; dy <= FARM_SCAN_HEIGHT; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    reads += READS_PER_TILE;
                    if (reads > FARM_SCAN_BUDGET) {
                        return new FarmWork(mature, farmland, seeds);
                    }
                    FarmTile tile = view.tileAt(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
                    if (tile.above() == Above.CROP && tile.crop().isPresent()
                            && tile.crop().orElseThrow().isMature()) {
                        mature++;
                    } else if (tile.ground() == Ground.FARMLAND && tile.above() == Above.AIR) {
                        farmland++;
                    }
                }
            }
        }
        return new FarmWork(mature, farmland, seeds);
    }

    private static BlockPos centerOf(Optional<Point> home, List<SpiritEntity> members) {
        if (home.isPresent()) {
            Point point = home.get();
            return new BlockPos((int) Math.floor(point.x()), (int) Math.floor(point.y()),
                    (int) Math.floor(point.z()));
        }
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                return spirit.blockPosition();
            }
        }
        return null;
    }
}
