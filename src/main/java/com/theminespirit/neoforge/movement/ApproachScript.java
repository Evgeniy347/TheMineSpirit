package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.StandChoice;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.ApproachParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class ApproachScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final BlockCoord target;
    private final StandWalker walker;
    private boolean started;
    private String failureReason;

    public ApproachScript(SpiritEntity spirit, ApproachParams params) {
        this.spirit = spirit;
        this.target = new BlockCoord(params.x(), params.y(), params.z());
        this.walker = new StandWalker(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            walker.cancel();
            failureReason = "дух исчез до подхода к цели";
            return ScriptStatus.FAILED;
        }
        if (!started) {
            started = true;
            String refusal = TravelCheck.refusal(spirit, target);
            if (refusal != null) {
                failureReason = refusal;
                return ScriptStatus.FAILED;
            }
            if (walker.inReach(target)) {
                return ScriptStatus.SUCCEEDED;
            }
            if (!walker.begin(target)) {
                failureReason = "подойти некуда: у цели " + target + " нет стоянки в радиусе "
                        + StandChoice.HORIZONTAL_SPREAD + " с полом, свободными ногами и головой, без опасных соседей, "
                        + "из которой цель в досягаемости " + StandChoice.REACH_DISTANCE;
                return ScriptStatus.FAILED;
            }
        }
        return switch (walker.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> arrived();
            case STUCK -> fail("застрял по дороге к стоянке у цели " + target + "; стоянок опробовано: "
                    + walker.failedStands());
            case TIMEOUT -> fail("не дошёл до стоянки у цели " + target + " за отведённое время; стоянок опробовано: "
                    + walker.failedStands());
        };
    }

    @Override
    public void cancel() {
        walker.cancel();
    }

    @Override
    public void suspend() {
        walker.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private ScriptStatus arrived() {
        if (walker.inReach(target)) {
            return ScriptStatus.SUCCEEDED;
        }
        return fail("дух встал на стоянку, но цель " + target + " дальше досягаемости " + StandChoice.REACH_DISTANCE);
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
