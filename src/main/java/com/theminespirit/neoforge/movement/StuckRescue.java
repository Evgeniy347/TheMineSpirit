package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.RouteDetour.Pending;
import org.jspecify.annotations.Nullable;

public final class StuckRescue {
    private static final int ROUTE_ATTEMPTS = 3;
    private final SpiritEntity spirit;
    private final MoveDwell dwell = new MoveDwell();
    private boolean routes;
    private int routeAttempts;
    private @Nullable String refusal;
    private @Nullable RouteWalk walk;
    private @Nullable Pending pending;

    public StuckRescue(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void reset(boolean allowRoutes) {
        cancel();
        routes = allowRoutes;
        routeAttempts = 0;
        refusal = null;
        dwell.reset();
    }

    public boolean free(PathMode mode, BlockCoord target, int radius, boolean movingTarget) {
        if (!routes || movingTarget || (pending == null && routeAttempts >= ROUTE_ATTEMPTS)) {
            refusal = "маршрут не найден: цель " + target;
            return false;
        }
        if (pending == null) {
            routeAttempts++;
            pending = RouteDetour.begin(spirit, target, radius, "возврат");
            if (pending == null) {
                refusal = "маршрут не найден: цель " + target;
                return false;
            }
        }
        spirit.getNavigation().stop();
        return true;
    }

    public boolean walking() {
        if (pending != null) {
            RouteDetour.advance(spirit, pending, RouteDetour.SLICE_MILLIS);
            if (!pending.done()) {
                return true;
            }
            RouteWalk ready = pending.walk();
            refusal = pending.refusal();
            pending = null;
            walk = ready;
            if (walk == null) {
                return false;
            }
        }
        RouteWalk current = walk;
        if (current == null) {
            return false;
        }
        dwell.observe(spirit, current.target());
        RouteWalk.State state = current.tick();
        if (state == RouteWalk.State.RUNNING) {
            return true;
        }
        if (state == RouteWalk.State.FAILED) {
            RouteDetour.failed(spirit, current);
        }
        RouteDetour.removed(spirit, current);
        walk = null;
        return false;
    }

    public int ticksBudget() {
        if (pending != null) {
            return RouteDetour.pendingBudget();
        }
        RouteWalk current = walk;
        return current == null ? 0 : current.ticksBudget();
    }

    public boolean routing() {
        return pending != null || walk != null;
    }

    public @Nullable String refusal() {
        return refusal;
    }

    public void cancel() {
        pending = null;
        if (walk != null) {
            walk.cancel();
            walk = null;
        }
    }
}
