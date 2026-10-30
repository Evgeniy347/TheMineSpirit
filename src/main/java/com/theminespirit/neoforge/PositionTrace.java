package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

public final class PositionTrace {
    static final int PERIOD_TICKS = 300;

    private static final Logger LOGGER = LogUtils.getLogger();

    private long last = Long.MIN_VALUE;

    public void tick(SpiritEntity spirit) {
        long now = spirit.level().getGameTime();
        if (last != Long.MIN_VALUE && now - last < PERIOD_TICKS) {
            return;
        }
        last = now;
        BlockPos feet = spirit.blockPosition();
        LOGGER.info(
                "[pos] дух {} в ({}, {}, {}), script={}, воздух={}, в воде={}, на земле={}",
                spirit.displayName(),
                feet.getX(),
                feet.getY(),
                feet.getZ(),
                spirit.brainIfPresent().flatMap(SpiritBrain::currentScript).orElse("-"),
                spirit.getAirSupply(),
                spirit.isInWater(),
                spirit.onGround());
    }
}
