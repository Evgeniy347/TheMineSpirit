package com.theminespirit.neoforge;

import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.WalkableStand;
import com.theminespirit.core.board.WorkStand;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.GroundProbe;
import com.theminespirit.neoforge.movement.NetworkLevelView;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

final class StandSearch {
    private static final int SNAP_MARGIN = 4;
    private static final int NO_GROUND = Integer.MIN_VALUE;

    record Resolved(BlockPos point, String refusal) {}

    private StandSearch() {}

    static Resolved resolve(Entity spirit, BlockPos point) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return new Resolved(point, null);
        }
        BlockPos feetPos = spirit.blockPosition();
        BlockCoord feet = new BlockCoord(feetPos.getX(), feetPos.getY(), feetPos.getZ());
        BlockCoord p = new BlockCoord(point.getX(), point.getY(), point.getZ());
        long dx = (long) p.x() - feet.x();
        long dz = (long) p.z() - feet.z();
        if (dx * dx + dz * dz
                        > (long) BoardConfig.current().facts().standSnapRadius()
                                * BoardConfig.current().facts().standSnapRadius()
                || freeAt(level, p)) {
            return new Resolved(point, null);
        }
        int r = BoardTuning.workStandRadius() + SNAP_MARGIN;
        int minX = Math.min(feet.x(), p.x()) - r;
        int maxX = Math.max(feet.x(), p.x()) + r;
        int minY = Math.min(feet.y(), p.y()) - r;
        int maxY = Math.max(feet.y(), p.y()) + r;
        int minZ = Math.min(feet.z(), p.z()) - r;
        int maxZ = Math.max(feet.z(), p.z()) + r;
        Optional<Set<BlockCoord>> walkable = RetreatPath.walkable(
                feet,
                new NetworkLevelView(level),
                cell -> cell.x() >= minX
                        && cell.x() <= maxX
                        && cell.y() >= minY
                        && cell.y() <= maxY
                        && cell.z() >= minZ
                        && cell.z() <= maxZ);
        if (walkable.isEmpty()) {
            return new Resolved(point, null);
        }
        Set<BlockCoord> occupied = occupiedByOtherSpirits(spirit, p);
        occupied.addAll(StandClaims.taken(level, spirit, p));
        Optional<BlockCoord> stand = WalkableStand.near(p, walkable.orElseThrow())
                .filter(cell -> !occupied.contains(cell))
                .or(() -> WalkableStand.near(p, walkable.orElseThrow()));
        if (stand.isEmpty()) {
            stand = WorkStand.near(p, ground(level), walkable.orElseThrow()::contains, occupied);
        }
        if (stand.isEmpty()) {
            return new Resolved(
                    point,
                    "пути нет к точке " + p + " от " + feet
                            + ": ни одной стоянки рядом, до которой дух дойдёт пешком, а цель занята блоком");
        }
        BlockCoord found = stand.orElseThrow();
        StandClaims.claim(spirit, found);
        return new Resolved(new BlockPos(found.x(), found.y(), found.z()), null);
    }

    private static Set<BlockCoord> occupiedByOtherSpirits(Entity spirit, BlockCoord around) {
        Set<BlockCoord> occupied = new HashSet<>();
        if (!(spirit.level() instanceof ServerLevel level)) {
            return occupied;
        }
        for (Object found : level.getEntitiesOfClass(
                Entity.class,
                new AABB(
                        around.x() - BoardTuning.workStandRadius(),
                        around.y() - 4,
                        around.z() - BoardTuning.workStandRadius(),
                        around.x() + BoardTuning.workStandRadius(),
                        around.y() + 4,
                        around.z() + BoardTuning.workStandRadius()))) {
            Entity other = (Entity) found;
            if (other.getUUID().equals(spirit.getUUID())) {
                continue;
            }
            occupied.add(new BlockCoord(
                    other.blockPosition().getX(),
                    other.blockPosition().getY(),
                    other.blockPosition().getZ()));
        }
        return occupied;
    }

    private static boolean freeAt(ServerLevel level, BlockCoord feet) {
        return GroundProbe.freeAt(level, feet);
    }

    private static WorkStand.Ground ground(ServerLevel level) {
        return new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return GroundProbe.feet(level, x, z, NO_GROUND);
            }

            @Override
            public boolean free(BlockCoord feet) {
                return feet.y() != NO_GROUND && freeAt(level, feet);
            }
        };
    }
}
