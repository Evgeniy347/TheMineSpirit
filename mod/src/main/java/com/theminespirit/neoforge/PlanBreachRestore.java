package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.neoforge.policy.PlanBreachData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class PlanBreachRestore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlanBreachRestore() {}

    static void restore(SpiritEntity spirit, Level level, BlockPlacer placer, BlockPos feet) {
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
            if (cell.equals(feet)
                    || cell.equals(feet.above())
                    || spirit.getEyePosition().distanceTo(Vec3.atCenterOf(cell)) > BlockBreaker.REACH) {
                pending++;
                continue;
            }
            String id = breach.blockId();
            if (placer.begin(cell, BlockStates.stateFor(id), id) instanceof BlockPlacer.Started) {
                data.clear(cell);
                restored++;
            } else {
                pending++;
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
