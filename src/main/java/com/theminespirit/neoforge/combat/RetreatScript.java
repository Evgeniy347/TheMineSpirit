package com.theminespirit.neoforge.combat;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.RetreatParams;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;

public final class RetreatScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private Point destination;
    private int ticks;
    private boolean applied;
    private String failureReason;
    private ScriptResult result;

    public RetreatScript(SpiritEntity spirit, RetreatParams params) {
        this.spirit = spirit;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока отступал");
        }
        if (!applied) {
            Point self = new Point(spirit.getX(), spirit.getY(), spirit.getZ());
            Optional<Point> retreatPoint = SafePointChoice.nearest(self,
                    spirit.home().map(home -> new Point(home.x() + 0.5, home.y(), home.z() + 0.5)),
                    spirit.camp().map(Camp::center));
            if (retreatPoint.isEmpty()) {
                return fail("точки отхода нет: поставьте дом командой /spirit home или лагерь командой /spirit camp");
            }
            destination = retreatPoint.get();
            spirit.tactics().applyDirective(CombatDirective.retreat(destination));
            applied = true;
        }
        ticks++;
        double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ()).distanceTo(destination);
        if (distance <= RetreatPlanner.HOME_ARRIVAL) {
            return succeed();
        }
        if (ticks >= CatalogLimits.RETREAT_TIMEOUT_TICKS) {
            return fail("не дошёл до точки за " + ticks + " тиков (тайм-аут "
                    + CatalogLimits.RETREAT_TIMEOUT_TICKS + "), осталось: " + distance);
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        release();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private ScriptStatus succeed() {
        result = new ScriptResult.Data(Map.of(
                "mode", CombatDirective.retreat(destination).mode().name(),
                "target", destination.toString(),
                "ticks", ticks,
                "outcome", "arrived"));
        release();
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus fail(String reason) {
        release();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private void release() {
        if (applied) {
            spirit.tactics().clearDirective();
            applied = false;
        }
    }
}
