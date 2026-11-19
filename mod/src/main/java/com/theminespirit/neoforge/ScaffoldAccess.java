package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.ClearScaffold;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.PlanWalkways;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ScaffoldAccess {
    record Plan(BlockCoord target, BlockCoord base, int height) {}

    private ScaffoldAccess() {}

    static @Nullable Plan find(ClearAreaSession session, List<BlockCoord> cells, Set<Plan> tried) {
        SpiritEntity spirit = session.spirit;
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        int radius = Math.min(SpiritReach.current().horizontal(), (int) BlockBreaker.REACH - 1);
        List<BlockCoord> targets = cells.stream()
                .sorted(Comparator.comparingDouble(cell -> feet.distSqr(ClearAreaSession.toPos(cell))))
                .toList();
        for (BlockCoord target : targets) {
            List<BlockCoord> nearby = new ArrayList<>(ClearScaffold.bases(from, target, radius));
            nearby.addAll(ClearScaffold.surfaceBases(from, target, radius, base -> standable(session, base)));
            List<BlockCoord> bases = nearby.stream()
                    .sorted(Comparator.comparing(base ->
                            session.level instanceof ServerLevel level && PlanWalkways.on(level, base.x(), base.z())))
                    .toList();
            for (BlockCoord base : bases) {
                if (tried.stream()
                        .anyMatch(plan ->
                                plan.target().equals(target) && plan.base().equals(base))) {
                    continue;
                }
                int first = Math.max(1, target.y() - base.y() - MiningTuning.MAX_STAND_RISE);
                for (int height = first; height <= target.y() - base.y(); height++) {
                    Plan plan = new Plan(target, base, height);
                    if (safe(session, plan)) {
                        return plan;
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(ClearAreaSession session, BlockCoord base) {
        BlockPos at = ClearAreaSession.toPos(base);
        BlockPos floor = at.below();
        return MiningSafety.canStand(base, session.safety)
                && session.level.getBlockState(floor).isFaceSturdy(session.level, floor, Direction.UP);
    }

    static boolean safe(ClearAreaSession session, Plan plan) {
        BlockCoord target = plan.target();
        BlockPos at = ClearAreaSession.toPos(plan.base());
        BlockPos floor = at.below();
        if (session.targets.clear(target)
                || session.targets.leased(target)
                || session.reservations.foreign(target, session.spirit.getUUID())
                || !(MiningSafety.canBreak(target, session.safety) instanceof BreakSafety.Safe)
                || !standable(session, plan.base())) {
            return false;
        }
        BlockCoord under = new BlockCoord(floor.getX(), floor.getY(), floor.getZ());
        if (session.targets.leased(under)
                && !(floor.equals(session.spirit.blockPosition().below())
                        && PillarDownScript.ownBelow(session.spirit))) {
            return false;
        }
        for (int dy = 0; dy <= plan.height() + 1; dy++) {
            BlockPos cell = at.above(dy);
            BlockCoord coord = new BlockCoord(cell.getX(), cell.getY(), cell.getZ());
            if (session.level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null
                    || !session.level.getBlockState(cell).isAir()
                    || session.targets.leased(coord)
                    || session.reservations.foreign(coord, session.spirit.getUUID())
                    || !(MiningSafety.canBreak(coord, session.safety) instanceof BreakSafety.Safe)) {
                return false;
            }
        }
        if (!session.level
                .getEntities(
                        session.spirit,
                        new AABB(at).expandTowards(0, plan.height() + 1, 0),
                        entity -> entity instanceof LivingEntity)
                .isEmpty()) {
            return false;
        }
        Vec3 eye =
                new Vec3(at.getX() + 0.5, at.getY() + plan.height() + session.spirit.getEyeHeight(), at.getZ() + 0.5);
        if (eye.distanceTo(Vec3.atCenterOf(ClearAreaSession.toPos(target))) > BlockBreaker.REACH - 0.5) {
            return false;
        }
        BlockPos pos = ClearAreaSession.toPos(target);
        BlockHitResult hit = session.level.clip(new ClipContext(
                eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, session.spirit));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }
}
