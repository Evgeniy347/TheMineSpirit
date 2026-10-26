package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class StuckRescue {
    private static final int ROUTE_ATTEMPTS = 3;
    private final SpiritEntity spirit;
    private int teleports;
    private boolean routes;
    private int routeAttempts;
    private @Nullable RouteWalk walk;

    public StuckRescue(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void reset(boolean allowRoutes) {
        cancel();
        teleports = 0;
        routes = allowRoutes;
        routeAttempts = 0;
    }

    public boolean free(PathMode mode, BlockCoord target, int radius, boolean movingTarget) {
        Optional<BlockCoord> step = StuckTeleport.find(spirit, mode, target, teleports);
        if (step.isPresent()) {
            StuckTeleport.apply(spirit, step.get());
            teleports++;
            return true;
        }
        if (!routes || movingTarget || routeAttempts >= ROUTE_ATTEMPTS) {
            return false;
        }
        routeAttempts++;
        walk = RouteDetour.plan(spirit, target, radius, "подход").orElse(null);
        if (walk == null) {
            return false;
        }
        spirit.getNavigation().stop();
        return true;
    }

    public boolean walking() {
        RouteWalk current = walk;
        if (current == null) {
            return false;
        }
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
        RouteWalk current = walk;
        return current == null ? 0 : current.ticksBudget();
    }

    public boolean routing() {
        return walk != null;
    }

    public int teleports() {
        return teleports;
    }

    public void cancel() {
        if (walk != null) {
            walk.cancel();
            walk = null;
        }
    }
}
