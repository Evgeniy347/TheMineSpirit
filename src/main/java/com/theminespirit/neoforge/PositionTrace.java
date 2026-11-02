package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ChangeSummary;
import com.theminespirit.core.observation.DebouncedState;
import com.theminespirit.core.observation.StateSignature;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

public final class PositionTrace {
    static final int CHECK_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ChangeSummary log = new ChangeSummary(BoardTuning.logSummaryTicks());
    private final DebouncedState stable = new DebouncedState(CHECK_TICKS * 2L);
    private long lastCheck = Long.MIN_VALUE;

    public void tick(SpiritEntity spirit) {
        long now = spirit.level().getGameTime();
        BlockPos feet = spirit.blockPosition();
        String script =
                spirit.brainIfPresent().flatMap(SpiritBrain::currentScript).orElse("-");
        int air = spirit.getAirSupply();
        boolean water = spirit.isInWater();
        boolean ground = spirit.onGround();
        String next = StateSignature.spirit(air, water);
        boolean changed = stable.update(next, now).isPresent();
        if (!changed && lastCheck != Long.MIN_VALUE && now - lastCheck < CHECK_TICKS) {
            return;
        }
        lastCheck = now;
        log.note("pos", stable.current(), air, now).ifPresent(summary -> {
            if (summary.changed()) {
                LOGGER.info(
                        "[pos] дух {} в ({}, {}, {}), script={}, воздух={}, в воде={}, на земле={}",
                        spirit.displayName(),
                        feet.getX(),
                        feet.getY(),
                        feet.getZ(),
                        script,
                        air,
                        water,
                        ground);
                return;
            }
            LOGGER.info(
                    "[pos] сводка: дух {} остаётся в ({}, {}, {}), script={}, воздух {}–{}, в воде={}, "
                            + "на земле={}, проверок {}",
                    spirit.displayName(),
                    feet.getX(),
                    feet.getY(),
                    feet.getZ(),
                    script,
                    summary.min(),
                    summary.max(),
                    water,
                    ground,
                    summary.count());
        });
    }
}
