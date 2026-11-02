package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MoveDwell {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REPORT_TICKS = 1200;
    private @Nullable BlockCoord cell;
    private int ticks;

    public void reset() {
        cell = null;
        ticks = 0;
    }

    public void observe(SpiritEntity spirit, BlockCoord routeTarget) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord here = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        if (!here.equals(cell)) {
            cell = here;
            ticks = 0;
            return;
        }
        ticks++;
        if (ticks != REPORT_TICKS) {
            return;
        }
        LOGGER.info(
                "[move] дух {} не покидал клетку {} {} тиков: route-цель {}",
                spirit.getName().getString(),
                here,
                ticks,
                routeTarget);
    }
}
