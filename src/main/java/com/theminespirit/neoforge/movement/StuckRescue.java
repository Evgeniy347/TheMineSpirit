package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.TeleportTrail;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class StuckRescue {
    private static final int ROUTE_ATTEMPTS = 3;
    private final SpiritEntity spirit;
    private final TeleportTrail trail = new TeleportTrail();
    private final MoveDwell dwell = new MoveDwell();
    private boolean routes;
    private int routeAttempts;
    private @Nullable String refusal;
    private @Nullable RouteWalk walk;
    private @Nullable BlockCoord teleportTarget;

    public StuckRescue(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void reset(boolean allowRoutes) {
        cancel();
        routes = allowRoutes;
        routeAttempts = 0;
        refusal = null;
        teleportTarget = null;
        dwell.reset();
    }

    public boolean free(PathMode mode, BlockCoord target, int radius, boolean movingTarget) {
        teleportTarget = target;
        Optional<BlockCoord> step = StuckTeleport.find(spirit, mode, target, trail);
        if (step.isPresent()) {
            StuckTeleport.apply(spirit, step.get(), target, trail);
            return true;
        }
        String teleportFailure = StuckTeleport.failure(target, trail);
        if (!routes || movingTarget || routeAttempts >= ROUTE_ATTEMPTS) {
            refusal = teleportFailure;
            return false;
        }
        routeAttempts++;
        walk = RouteDetour.plan(spirit, target, radius, "подход").orElse(null);
        if (walk == null) {
            refusal = teleportFailure;
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
        dwell.observe(spirit, current.target(), teleportTarget);
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
        return trail.attempts();
    }

    public @Nullable String refusal() {
        return refusal;
    }

    public void cancel() {
        if (walk != null) {
            walk.cancel();
            walk = null;
        }
    }
}
