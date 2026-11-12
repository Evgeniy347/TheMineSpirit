package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.RouteDetour;
import com.theminespirit.neoforge.movement.RouteDetour.Pending;
import com.theminespirit.neoforge.movement.RouteWalk;
import com.theminespirit.neoforge.movement.SurfaceRoute;
import org.jspecify.annotations.Nullable;

final class DigOutRoute {
    enum State {
        RUNNING,
        NO_ROUTE,
        DESCENT_FAILED
    }

    private static final int RETRIES = 3;

    private final SpiritEntity spirit;
    private final BlockCoord target;
    private final boolean descending;
    private final int radius;
    private final boolean rescue;
    private @Nullable RouteWalk route;
    private @Nullable Pending pending;
    private SurfaceRoute.@Nullable Pending surface;
    private int retries;
    private boolean routeFailed;

    DigOutRoute(SpiritEntity spirit, BlockCoord target, boolean descending, int radius, boolean rescue) {
        this.spirit = spirit;
        this.target = target;
        this.descending = descending;
        this.radius = radius;
        this.rescue = rescue;
    }

    static boolean mode(int targetY, int feetY, boolean current, boolean following) {
        return following ? current : targetY < feetY;
    }

    State tick() {
        if (route == null && pending == null && surface == null && retries < RETRIES && !routeFailed) {
            surface = SurfaceRoute.begin(spirit, target, descending, rescue);
            retries++;
        }
        if (surface != null) {
            SurfaceRoute.advance(spirit, surface, SurfaceRoute.SLICE_MILLIS);
            if (!surface.done()) {
                return State.RUNNING;
            }
            route = surface.walk();
            surface = null;
            if (route == null) {
                pending = RouteDetour.begin(spirit, target, radius, "dig_out");
            }
        }
        if (pending != null) {
            RouteDetour.advance(spirit, pending, RouteDetour.SLICE_MILLIS);
            if (!pending.done()) {
                return State.RUNNING;
            }
            route = pending.walk();
            pending = null;
        }
        if (descending && route == null && retries >= RETRIES) {
            return State.DESCENT_FAILED;
        }
        RouteWalk current = route;
        if (current == null) {
            return State.NO_ROUTE;
        }
        RouteWalk.State state = current.tick();
        if (state == RouteWalk.State.RUNNING) {
            return State.RUNNING;
        }
        if (state == RouteWalk.State.FAILED) {
            RouteDetour.failed(spirit, current);
            routeFailed = true;
        }
        RouteDetour.removed(spirit, current);
        route = null;
        return State.NO_ROUTE;
    }

    void cancel() {
        surface = null;
        pending = null;
        if (route != null) {
            route.cancel();
            route = null;
        }
    }
}
