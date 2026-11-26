package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ClearStands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COLUMNS = 256;
    private static final int STANDS_MAX = 2048;

    private record Candidates(ClearAnchor.Stand top, ClearAnchor.Stand near) {}

    private ClearStands() {}

    static ClearAnchor.@Nullable Stand best(
            ClearAreaSession session, Set<BlockCoord> tried, List<BlockCoord> targets, Set<BlockCoord> blockedColumns) {
        BlockPos feet = session.spirit.blockPosition();
        List<ClearAnchor.Stand> stands = stands(session).stream()
                .filter(stand -> blockedColumns.stream().noneMatch(column -> ClearAnchor.sameColumn(column, stand)))
                .toList();
        ClearAnchor.Stand chosen = ClearAnchor.best(
                targets, stands, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), horizontalReach(), tried);
        if (chosen == null) {
            LOGGER.info(
                    "[clear] {}: якорь не найден: клеток {}, стоянок {}, опробовано {}, дух {}",
                    session.spirit.getName().getString(),
                    session.remaining.size(),
                    stands.size(),
                    tried.size(),
                    new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
        }
        return chosen;
    }

    static int horizontalReach() {
        return ClearAnchor.reachFor(SpiritReach.current().horizontal());
    }

    private static List<ClearAnchor.Stand> stands(ClearAreaSession session) {
        Set<BlockCoord> columns = new LinkedHashSet<>();
        BlockPos spiritAt = session.spirit.blockPosition();
        List<BlockCoord> sorted = new ArrayList<>(session.remaining);
        sorted.sort(Comparator.comparingDouble(cell -> spiritAt.distSqr(ClearAreaSession.toPos(cell))));
        for (BlockCoord cell : sorted) {
            columns.add(new BlockCoord(cell.x(), cell.y(), cell.z()));
            if (columns.size() >= COLUMNS) {
                break;
            }
        }
        Set<BlockCoord> targets = new HashSet<>(session.remaining);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockCoord cell : session.remaining) {
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }
        Set<ClearAnchor.Stand> stands = new LinkedHashSet<>();
        int reach = horizontalReach();
        for (BlockCoord column : columns) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    Candidates found = stand(session, column.x() + dx, column.z() + dz, minY, maxY, targets);
                    if (found != null) {
                        stands.add(found.top());
                        stands.add(found.near());
                    }
                }
            }
            if (stands.size() >= STANDS_MAX) {
                break;
            }
        }
        Candidates here = stand(
                session,
                spiritAt.getX(),
                spiritAt.getZ(),
                Math.min(minY, spiritAt.getY()),
                Math.max(maxY, spiritAt.getY()),
                targets);
        if (here != null) {
            stands.add(here.top());
            stands.add(here.near());
        }
        return new ArrayList<>(stands);
    }

    private static @Nullable Candidates stand(
            ClearAreaSession session, int x, int z, int minY, int maxY, Set<BlockCoord> targets) {
        Level level = session.level;
        int top = maxY + 1;
        int low = minY - ClearAnchor.MAX_RISE - 2;
        ClearAnchor.@Nullable Stand highest = null;
        ClearAnchor.@Nullable Stand nearest = null;
        int spiritY = session.spirit.blockPosition().getY();
        int nearestDistance = Integer.MAX_VALUE;
        for (int y = top; y >= low; y--) {
            BlockPos feetPos = new BlockPos(x, y, z);
            BlockState feet = level.getBlockState(feetPos);
            if (!feet.isAir() && !feet.canBeReplaced()) {
                continue;
            }
            BlockPos head = feetPos.above();
            BlockState above = level.getBlockState(head);
            if (!above.isAir() && !above.canBeReplaced()) {
                continue;
            }
            BlockPos floor = feetPos.below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                continue;
            }
            BlockCoord coord = new BlockCoord(x, y, z);
            BlockCoord under = new BlockCoord(x, y - 1, z);
            if (targets.contains(under) || targets.contains(coord)) {
                continue;
            }
            if (!MiningSafety.canStand(coord, session.safety)) {
                continue;
            }
            ClearAnchor.Stand candidate = new ClearAnchor.Stand(x, y, z);
            if (highest == null) {
                highest = candidate;
            }
            int distance = Math.abs(y - spiritY);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return highest == null ? null : new Candidates(highest, nearest == null ? highest : nearest);
    }
}
