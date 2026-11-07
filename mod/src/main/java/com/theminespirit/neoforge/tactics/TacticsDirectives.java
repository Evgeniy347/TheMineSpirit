package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.DirectiveOwnership;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.adapter.RetreatTracker;
import com.theminespirit.core.tactics.adapter.StoredTactics;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

final class TacticsDirectives {
    private final TacticsService host;
    private final DirectiveOwnership ownership = new DirectiveOwnership();
    private CombatDirective directive;
    private Optional<StoredTactics> preDirective = Optional.empty();
    private LivingEntity forcedTarget;
    private RetreatMethod forcedMethod = RetreatMethod.NONE;
    private Optional<Point> forcedPoint = Optional.empty();
    private boolean held;

    TacticsDirectives(TacticsService host) {
        this.host = host;
    }

    void apply(CombatDirective next, DirectiveOwner owner) {
        if (next == null) {
            throw new IllegalArgumentException("Приказ = null: боевой скрипт обязан передать CombatDirective, "
                    + "иначе тактика не знает, какой режим и цель включить");
        }
        if (directive == null) {
            preDirective = Optional.of(host.snapshot());
        }
        directive = next;
        ownership.appliedBy(owner);
        held = false;
        host.setOrdered(next.mode());
        forcedTarget = null;
        setRetreat(next.retreat().orElse(RetreatMethod.NONE), next.safePoint());
        if (next.mode() == CombatMode.PASSIVE) {
            host.spirit.setTarget(null);
        }
        host.markForced();
    }

    boolean clear(DirectiveOwner owner) {
        if (directive == null || !ownership.mayClear(owner)) {
            return false;
        }
        directive = null;
        held = false;
        ownership.cleared();
        forcedTarget = null;
        setRetreat(RetreatMethod.NONE, Optional.empty());
        host.restore(preDirective.orElse(new StoredTactics(
                TacticsService.DEFAULT_MODE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                RetreatTracker.IDLE)));
        preDirective = Optional.empty();
        host.spirit.setTarget(null);
        return true;
    }

    Optional<CombatDirective> active() {
        return Optional.ofNullable(directive);
    }

    CombatDirective current() {
        return directive;
    }

    void hold() {
        if (directive != null && !held) {
            held = true;
            host.release();
        }
    }

    boolean held() {
        return held;
    }

    void resetHeld() {
        held = false;
    }

    void setTarget(LivingEntity target) {
        forcedTarget = target;
        host.spirit.setTarget(target);
    }

    void setRetreat(RetreatMethod method, Optional<Point> point) {
        if (method == null) {
            throw new IllegalArgumentException(
                    "Способ отхода = null: укажите RetreatMethod, " + "для «способ не навязан» — RetreatMethod.NONE");
        }
        if (point == null) {
            throw new IllegalArgumentException("Точка отхода = null: отсутствие точки передаётся Optional.empty()");
        }
        if (point.isPresent() && method == RetreatMethod.NONE) {
            throw new IllegalArgumentException("Точка " + point.get() + " при способе NONE: "
                    + "точка имеет смысл только с явным способом отхода, иначе дух не знает, зачем она нужна");
        }
        forcedMethod = method;
        forcedPoint = point;
    }

    RetreatMethod method() {
        return forcedMethod;
    }

    Optional<Point> point() {
        return forcedPoint;
    }

    Optional<LivingEntity> livingTarget() {
        return Optional.ofNullable(forcedTarget).filter(LivingEntity::isAlive);
    }

    LivingEntity target() {
        return forcedTarget;
    }
}
