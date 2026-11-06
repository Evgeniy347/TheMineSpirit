package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.KeepDistanceParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class KeepDistanceScript implements ScriptInstance {
    public static final int DECISION_PERIOD_TICKS = 10;
    public static final double SLACK = 1.0;
    public static final double RETREAT_MARGIN = 1.0;
    public static final double RETREAT_ARRIVAL = 0.8;

    private final SpiritEntity spirit;
    private final String targetName;
    private final int distance;
    private final Approach approach;
    private Entity target;
    private int cooldown;
    private String failureReason;

    public KeepDistanceScript(SpiritEntity spirit, KeepDistanceParams params) {
        this.spirit = spirit;
        this.targetName = params.target();
        this.distance = params.distance();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез, пока держал дистанцию до '" + targetName + "'");
        }
        if (approach.isActive()) {
            approach.tick();
            return ScriptStatus.RUNNING;
        }
        if (cooldown > 0) {
            cooldown--;
            return ScriptStatus.RUNNING;
        }
        cooldown = DECISION_PERIOD_TICKS - 1;
        target = resolveTarget();
        if (target == null) {
            return fail("цель '" + targetName + "' не найдена или исчезла: не от кого держать дистанцию");
        }
        decide();
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private void decide() {
        double current = spirit.position().distanceTo(target.position());
        if (current > distance + SLACK) {
            approach.begin(target, distance + SLACK / 2);
        } else if (current < distance) {
            approach.begin(retreatPoint(), RETREAT_ARRIVAL);
        }
    }

    private Vec3 retreatPoint() {
        Vec3 away = spirit.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        Vec3 direction = flat.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 point = target.position().add(direction.scale(distance + RETREAT_MARGIN));
        return new Vec3(point.x, spirit.getY(), point.z);
    }

    private Entity resolveTarget() {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return null;
        }
        spirit.countEntityQuery();
        for (ServerPlayer player : level.players()) {
            if (player.getName().getString().equals(targetName)) {
                return player;
            }
        }
        Entity byId = entityById(level);
        return byId == null || byId.isRemoved() ? null : byId;
    }

    private Entity entityById(ServerLevel level) {
        try {
            return level.getEntity(UUID.fromString(targetName));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
