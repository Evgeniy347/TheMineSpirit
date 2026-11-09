package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticsAction;
import com.theminespirit.core.tactics.adapter.ExecutionResult;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

final class TacticsExecution {
    private final TacticsService host;

    TacticsExecution(TacticsService host) {
        this.host = host;
    }

    void execute() {
        if (host.outcome == null) {
            return;
        }
        if (host.retreat.retreating() && host.plan.isPresent()) {
            host.active = true;
            host.action = TacticsAction.RETREAT;
            host.progress = host.retreating.step(host.plan.get(), entity(host.shootTargetId));
            return;
        }
        LivingEntity target = host.orders.directives().target() != null
                        && host.orders.directives().target().isAlive()
                ? host.orders.directives().target()
                : entity(host.targetId).orElse(null);
        if (host.outcome.decision() == TacticalOption.FIGHT && target != null) {
            host.active = true;
            host.action = TacticsAction.FIGHT;
            host.returnWalk.clearCooldown();
            host.retreating.reset();
            if (host.result instanceof ExecutionResult.Shooting
                    && host.spirit.gear().arrows() > 0) {
                host.combat.shoot(target);
            } else {
                host.combat.melee(target);
            }
            return;
        }
        if (host.outcome.decision() == TacticalOption.IGNORE
                && !host.orders.directives().held()
                && host.returnWalk.due(host.spirit.level().getGameTime())) {
            host.active = true;
            host.action = host.orders.zone().isPresent() ? TacticsAction.GUARD : TacticsAction.RETURN;
            host.returnWalk.walk(host.returnWalk.point());
            return;
        }
        release();
    }

    void release() {
        host.action = TacticsAction.IDLE;
        if (host.active) {
            host.retreating.reset();
            host.combat.stop();
            host.spirit.gear().holdNothing();
            host.spirit.setTarget(null);
        }
        host.active = false;
    }

    private Optional<LivingEntity> entity(String id) {
        if (id == null || host.facts == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(host.facts.entities().get(id)).filter(LivingEntity::isAlive);
    }
}
