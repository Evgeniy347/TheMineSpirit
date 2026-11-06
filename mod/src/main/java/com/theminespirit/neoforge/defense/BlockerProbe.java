package com.theminespirit.neoforge.defense;

import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class BlockerProbe {
    public static final int BLOCK_BUDGET = 24;

    private int blockReads;

    public List<Point> probe(ServerLevel level, Point self, Point threat, List<Point> mobs) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: блоки укрытия читаются из мира");
        }
        if (self == null) {
            throw new IllegalArgumentException("self не может быть null: линия укрытия идёт от духа");
        }
        if (threat == null) {
            throw new IllegalArgumentException("threat не может быть null: линия укрытия идёт к угрозе");
        }
        if (mobs == null) {
            throw new IllegalArgumentException("mobs не может быть null: нет мобов — пустой список");
        }
        List<Point> blockers = new ArrayList<>();
        for (Point mob : mobs) {
            if (mob != null) {
                blockers.add(mob);
            }
        }
        double distance = self.distanceTo(threat);
        if (distance <= 0) {
            return List.copyOf(blockers);
        }
        int steps = Math.min((int) Math.ceil(distance), BLOCK_BUDGET - blockReads);
        for (int i = 1; i <= steps; i++) {
            double part = i / (double) (steps + 1);
            BlockPos pos = new BlockPos(
                    (int) Math.floor(self.x() + (threat.x() - self.x()) * part),
                    (int) Math.floor(self.y() + (threat.y() - self.y()) * part),
                    (int) Math.floor(self.z() + (threat.z() - self.z()) * part));
            if (!level.isLoaded(pos)) {
                continue;
            }
            blockReads++;
            var state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                blockers.add(new Point(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
            }
        }
        return List.copyOf(blockers);
    }

    public int blockReads() {
        return blockReads;
    }
}
