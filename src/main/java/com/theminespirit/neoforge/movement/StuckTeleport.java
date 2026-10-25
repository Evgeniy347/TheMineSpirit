package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.Passage;
import com.theminespirit.core.movement.Unstuck;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.NightLeashGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        Vec3 eye = spirit.getEyePosition();
        Vec3 head = new Vec3(cell.x() + 0.5, cell.y() + spirit.getEyeHeight(), cell.z() + 0.5);
        return clearLine(spirit, eye, head) && !crossesClosedPassage(spirit, blockCoord(spirit.position()), cell);
    }

    private static boolean crossesClosedPassage(SpiritEntity spirit, BlockCoord from, BlockCoord to) {
        List<Passage.Cell> passages = new ArrayList<>();
        for (int x = Math.min(from.x(), to.x()) - 1; x <= Math.max(from.x(), to.x()) + 1; x++) {
            for (int y = Math.min(from.y(), to.y()) - 1; y <= Math.max(from.y(), to.y()) + 1; y++) {
                for (int z = Math.min(from.z(), to.z()) - 1; z <= Math.max(from.z(), to.z()) + 1; z++) {
                    Passage.Cell passage = PassageWalk.passageAt(spirit.level(), new BlockCoord(x, y, z));
                    if (passage != null) {
                        passages.add(passage);
                    }
                }
            }
        }
        return Passage.blockedBetween(from, to, passages);
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
