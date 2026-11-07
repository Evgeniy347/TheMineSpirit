package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.ReturnStall;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class TacticsReturn {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TICKS = 600;
    private static final int COOLDOWN_TICKS = 1200;

    private final TacticsService host;
    private final SpiritEntity spirit;
    private final Approach approach;
    private final RetreatExecutor retreating;
    private final ReturnStall stall = new ReturnStall();
    private Optional<Point> target = Optional.empty();
    private int ticks;
    private long blockedUntil;
    private String lastState = "-";
    private boolean returned;

    TacticsReturn(TacticsService host, SpiritEntity spirit, Approach approach, RetreatExecutor retreating) {
        this.host = host;
        this.spirit = spirit;
        this.approach = approach;
        this.retreating = retreating;
    }

    void set(Optional<Point> point) {
        target = point;
        returned = false;
    }

    boolean due(long now) {
        return target.isPresent() && !returned && now >= blockedUntil;
    }

    Point point() {
        return target.orElseThrow();
    }

    void clearCooldown() {
        ticks = 0;
        blockedUntil = 0;
    }

    void walk(Point point) {
        retreating.reset();
        spirit.gear().holdNothing();
        Vec3 destination = new Vec3(point.x(), point.y(), point.z());
        double distance = spirit.position().distanceTo(destination);
        if (distance <= TacticsService.RETURN_ARRIVAL) {
            approach.cancel();
            returned = true;
            ticks = 0;
            return;
        }
        if (approach.detouring()) {
            approach.tick();
            return;
        }
        ticks++;
        boolean stalled = stall.stalled(point, distance);
        if (stalled) {
            LOGGER.warn(
                    "[incident] дух {} не дошёл до якоря {} за {} тиков (осталось {}, стоит в {}, подход {}): возврат "
                            + "снят, дух свободен",
                    spirit.displayName(),
                    point,
                    ReturnStall.STALL_TICKS,
                    String.format(Locale.ROOT, "%.1f", distance),
                    spirit.blockPosition().toShortString(),
                    lastState);
            lastState = "-";
        }
        if (stalled || ticks > MAX_TICKS) {
            if (ticks > MAX_TICKS) {
                LOGGER.warn(
                        "[incident] дух {} шёл к якорю {} дольше {} тиков (осталось {}, стоит в {}): возврат снят на {}"
                                + " тиков, дух свободен",
                        spirit.displayName(),
                        point,
                        MAX_TICKS,
                        String.format(Locale.ROOT, "%.1f", distance),
                        spirit.blockPosition().toShortString(),
                        COOLDOWN_TICKS);
            }
            approach.cancel();
            returned = true;
            ticks = 0;
            stall.reset();
            blockedUntil = spirit.level().getGameTime() + COOLDOWN_TICKS;
            return;
        }
        if (!approach.isActive() && DigOutScript.sealed(spirit.level(), spirit.blockPosition())) {
            returned = true;
            ticks = 0;
            blockedUntil = spirit.level().getGameTime() + COOLDOWN_TICKS;
            host.release();
            return;
        }
        if (!approach.isActive()) {
            approach.beginWithRoutes(destination, TacticsService.RETURN_ARRIVAL);
        }
        Approach.State state = approach.tick();
        if (state != Approach.State.MOVING) {
            lastState = state + (approach.refusal() == null ? "" : ": " + approach.refusal());
        }
    }
}
