package com.theminespirit.neoforge.policy;

import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class NightLeashGate {
    public static final Set<String> FOLLOW_SCRIPTS = Set.of("follow", "come_to_player");

    private static final NightLeash LEASH = NightLeash.withDefaultRules();
    private static final long TICKS_PER_DAY = 24_000L;

    private NightLeashGate() {}

    public static void arm(SpiritEntity spirit) {
        Optional<BlockCoord> home = spirit.home();
        if (home.isEmpty()) {
            spirit.setNightLeashArmed(false);
            return;
        }
        BlockCoord cell = cellOf(spirit);
        boolean inside = LEASH.insideSettlement(cell, home.get(), LEASH.settlementRadius());
        boolean evening = LEASH.isEvening(dayFraction(spirit.level()));
        spirit.setNightLeashArmed(
                LEASH.armedNext(new NightLeash.ArmedState(evening, spirit.nightLeashArmed(), inside)));
    }

    public static String refusal(SpiritEntity spirit, BlockCoord target) {
        Optional<BlockCoord> home = spirit.home();
        if (home.isEmpty()) {
            return null;
        }
        boolean following =
                spirit.currentTaskScript().filter(FOLLOW_SCRIPTS::contains).isPresent();
        NightLeash.Decision decision = LEASH.checkTarget(
                target,
                home.get(),
                LEASH.settlementRadius(),
                new NightLeash.TargetState(
                        LEASH.isEvening(dayFraction(spirit.level())), spirit.nightLeashArmed(), following));
        if (decision instanceof NightLeash.Refused refused) {
            return refused.reason();
        }
        return null;
    }

    private static double dayFraction(Level level) {
        return Math.floorMod(level.getDayTime(), TICKS_PER_DAY) / (double) TICKS_PER_DAY;
    }

    private static BlockCoord cellOf(SpiritEntity spirit) {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
