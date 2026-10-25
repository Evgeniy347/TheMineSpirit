package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.Unstuck;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.NightLeashGate;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class StuckTeleport {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StuckTeleport() {}

    public static Optional<BlockCoord> find(SpiritEntity spirit, PathMode mode, BlockCoord target, int used) {
        Unstuck rules = DataTables.INSTANCE.unstuck().current();
        if (used >= rules.teleportsPerApproach() || mode == PathMode.SWIM || mode == PathMode.CAREFUL) {
            return Optional.empty();
        }
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        Optional<BlockCoord> step = rules.step(
                blockCoord(spirit.position()),
                target,
                cell -> MiningSafety.canStand(cell, safety) && allowedThere(spirit, cell),
                cell -> clearPath(spirit, cell));
        if (step.isEmpty()) {
            LOGGER.info(
                    "[move] {} застрял у {}: перенос не найден, путь закрыт",
                    spirit.getName().getString(),
                    blockCoord(spirit.position()));
        }
        return step;
    }

    public static void apply(SpiritEntity spirit, BlockCoord cell) {
        BlockCoord from = blockCoord(spirit.position());
        spirit.getNavigation().stop();
        spirit.teleportTo(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        spirit.countTeleport();
        LOGGER.info(
                "[move] {} перенос {} → {}: застрял, открытого прохода рядом нет",
                spirit.getName().getString(),
                from,
                cell);
    }

    private static boolean allowedThere(SpiritEntity spirit, BlockCoord cell) {
        if (NightLeashGate.refusal(spirit, cell) != null) {
            return false;
        }
        return spirit.workZone().map(zone -> zone.excess(cell) == 0).orElse(true);
    }

    private static boolean clearPath(SpiritEntity spirit, BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        Vec3 lowFrom = spirit.position().add(0, 0.25, 0);
        Vec3 lowTo = new Vec3(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
        if (!clearLine(spirit, lowFrom, lowTo)) {
            return false;
        }
        Vec3 eyeTo = new Vec3(pos.getX() + 0.5, pos.getY() + spirit.getEyeHeight(), pos.getZ() + 0.5);
        return clearLine(spirit, spirit.getEyePosition(), eyeTo);
    }

    private static boolean clearLine(SpiritEntity spirit, Vec3 from, Vec3 to) {
        return spirit.level()
                        .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spirit))
                        .getType()
                == HitResult.Type.MISS;
    }

    private static BlockCoord blockCoord(Vec3 point) {
        return new BlockCoord((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
    }
}
