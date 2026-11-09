package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.neoforge.policy.PlanBreachData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class PlanBreachRestore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlanBreachRestore() {}

    public static void restore(SpiritEntity spirit, Level level, BlockPlacer placer, BlockPos feet) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        PlanBreachData data = PlanBreachData.get(server);
        int restored = 0;
        int pending = 0;
        for (Map.Entry<BlockPos, PlanBreachData.Breach> entry : data.open().entrySet()) {
            PlanBreachData.Breach breach = entry.getValue();
            if (!spirit.getUUID().equals(breach.spirit())) {
                continue;
            }
            BlockPos cell = entry.getKey();
            if (!level.getBlockState(cell).isAir()) {
                data.clear(cell);
                continue;
            }
            if (cell.equals(feet) || cell.equals(feet.above())) {
                pending++;
                LOGGER.info(
                        "[dig_out] {}: клетка {} занята духом — заделка позже",
                        spirit.displayName(),
                        cell.toShortString());
                continue;
            }
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(cell));
            if (distance > BlockBreaker.REACH) {
                pending++;
                LOGGER.info(
                        "[dig_out] {}: клетка {} дальше {} ({}): заделка позже",
                        spirit.displayName(),
                        cell.toShortString(),
                        BlockBreaker.REACH,
                        distance);
                continue;
            }
            String id = EarthCover.restoreBlock(breach.blockId());
            BlockPlacer.Begin begin = placer.begin(cell, BlockStates.stateFor(id), id);
            if (begin instanceof BlockPlacer.Started) {
                data.clear(cell);
                restored++;
            } else {
                pending++;
                LOGGER.info(
                        "[dig_out] {}: клетку {} не заделать: {}",
                        spirit.displayName(),
                        cell.toShortString(),
                        begin instanceof BlockPlacer.Refused refused
                                ? refused.message()
                                : ((BlockPlacer.Protected) begin).reason());
            }
        }
        if (restored > 0) {
            LOGGER.info("[dig_out] {}: пролом плана заделан тем же блоком — клеток {}", spirit.displayName(), restored);
        }
        if (pending > 0) {
            LOGGER.info(
                    "[dig_out] {}: не заделать {} клеток плана на месте — восстановление ставит доска, аудит дома "
                            + "не считает его целым",
                    spirit.displayName(),
                    pending);
        }
    }
}
