package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StillWatch;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.movement.PitEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class DigOutWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIG_OUT = "dig_out";
    private static final long PAUSE_TICKS = 1200;
    private static final long RESCUE_WINDOW_TICKS = 12000;
    private static final int AIR_SCAN = 32;
    private static final int NO_AIR = -1;

    private final BoardService host;
    private final Map<UUID, Long> pausedUntil = new HashMap<>();
    private final Map<UUID, Long> nextCheck = new HashMap<>();
    private final Map<UUID, String> lastCheck = new HashMap<>();
    private final Map<UUID, Deque<Long>> rescues = new HashMap<>();
    private final StillWatch still = new StillWatch();

    DigOutWatch(BoardService host) {
        this.host = host;
    }

    void tick(ServerLevel level, List<SpiritEntity> spirits, long now) {
        BlockCoord settlementHome = BoardService.homeOf(spirits);
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            boolean digging = DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
            boolean paused = now < pausedUntil.getOrDefault(spirit.getUUID(), 0L);
            boolean stillNow = still.still(spirit.getUUID(), coord(feet), now, PAUSE_TICKS);
            if (DigOutScript.open(level, feet)) {
                if (!digging && !paused && host.idleOrActivity(spirit)) {
                    climbOut(spirit, now);
                }
                continue;
            }
            boolean sealed = !digging && !paused && DigOutScript.sealed(level, feet);
            int air = airAbove(level, feet);
            boolean sky = air > 0 && DigOutScript.open(level, feet.above(air));
            checkLog(
                    spirit,
                    feet,
                    new Check(
                            sealed,
                            digging,
                            paused,
                            air,
                            sky,
                            spirit.currentTaskScript().orElse("-")),
                    now);
            if (sealed) {
                rescue(spirit, level, settlementHome, now, "замурован", air, sky);
                continue;
            }
            if (stillNow && !digging && !paused && inWater(level, feet)) {
                rescue(spirit, level, settlementHome, now, "не сдвинулся " + PAUSE_TICKS + " тиков в воде", air, sky);
            }
        }
    }

    private void rescue(
            SpiritEntity spirit,
            ServerLevel level,
            BlockCoord settlementHome,
            long now,
            String why,
            int air,
            boolean sky) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord goal = SurfacePoint.of(level, settlementHome);
        pausedUntil.put(spirit.getUUID(), now + PAUSE_TICKS);
        LOGGER.warn(
                "[incident] дух {} {} в {} (опора {}, вода={}, no_escape=true): выходит к поверхности у поселения {};"
                        + " ближайший воздух над духом {}, небо над ним {}, спасений за 10 мин: {}",
                spirit.displayName(),
                why,
                feet.toShortString(),
                BlockStates.idOf(level.getBlockState(feet.below())),
                inWater(level, feet),
                goal,
                airText(air),
                sky ? "есть" : "нет",
                countRescue(spirit.getUUID(), now));
        host.releaseAssigned(spirit);
        host.forgetLaunch(spirit);
        spirit.startTask(new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z())));
    }

    private void climbOut(SpiritEntity spirit, long now) {
        Optional<PitEscape.Exit> pit = PitRescue.trapped(spirit);
        if (pit.isEmpty()) {
            return;
        }
        pausedUntil.put(spirit.getUUID(), now + PAUSE_TICKS);
        PitEscape.Exit exit = pit.orElseThrow();
        LOGGER.warn(
                "[incident] дух {} в яме {}: пути наверх нет — поднимается на {} у ({}, {}) к краю",
                spirit.displayName(),
                spirit.blockPosition().toShortString(),
                exit.rise(),
                exit.dx(),
                exit.dz());
        host.releaseAssigned(spirit);
        host.forgetLaunch(spirit);
        spirit.startTask(PitRescue.plan(spirit, exit));
    }

    private void checkLog(SpiritEntity spirit, BlockPos feet, Check check, long now) {
        UUID id = spirit.getUUID();
        if (now < nextCheck.getOrDefault(id, 0L)) {
            return;
        }
        nextCheck.put(id, now + PAUSE_TICKS);
        String key = check.stateKey();
        if (key.equals(lastCheck.put(id, key))) {
            return;
        }
        LOGGER.info(
                "[dig_out] проверка: дух {} в {} — замурован {}, уже копает {}, пауза {}, ближайший воздух над духом {},"
                        + " небо над ним {}, задача {}, дом {}, спасений за 10 мин: {}",
                spirit.displayName(),
                feet.toShortString(),
                check.sealed(),
                check.digging(),
                check.paused(),
                airText(check.air()),
                check.sky() ? "есть" : "нет",
                check.task(),
                spirit.home().map(BlockCoord::toString).orElse("нет"),
                rescuesSoFar(id, now));
    }

    private int rescuesSoFar(UUID id, long now) {
        Deque<Long> times = rescues.get(id);
        if (times == null) {
            return 0;
        }
        while (!times.isEmpty() && times.peekFirst() < now - RESCUE_WINDOW_TICKS) {
            times.removeFirst();
        }
        return times.size();
    }

    record Check(boolean sealed, boolean digging, boolean paused, int air, boolean sky, String task) {
        String stateKey() {
            return sealed + "|" + digging + "|" + paused;
        }
    }

    private int countRescue(UUID id, long now) {
        Deque<Long> times = rescues.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() < now - RESCUE_WINDOW_TICKS) {
            times.removeFirst();
        }
        times.addLast(now);
        return times.size();
    }

    private static boolean inWater(Level level, BlockPos feet) {
        return level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(feet.above()).is(FluidTags.WATER);
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String airText(int air) {
        return air <= 0 ? "нет" : "+" + air;
    }

    private static int airAbove(Level level, BlockPos feet) {
        for (int dy = 1; dy <= AIR_SCAN; dy++) {
            BlockPos pos = feet.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isEmpty()
                    && state.getCollisionShape(level, pos).isEmpty()) {
                return dy;
            }
        }
        return NO_AIR;
    }
}
