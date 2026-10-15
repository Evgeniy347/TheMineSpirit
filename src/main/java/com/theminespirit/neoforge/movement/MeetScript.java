package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.MeetParams;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.ScriptTargetResolver;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

public final class MeetScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final String targetText;
    private final TargetSelector.Target selector;
    private final Approach approach;
    private LivingEntity target;
    private boolean started;
    private String failureReason;

    public MeetScript(SpiritEntity spirit, MeetParams params) {
        this.spirit = spirit;
        this.targetText = params.target();
        this.selector = TargetSelector.parse(params.target());
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез по дороге к цели '" + targetText + "'");
        }
        if (!started) {
            started = true;
            Optional<LivingEntity> found = ScriptTargetResolver.resolve(spirit, selector);
            if (found.isEmpty()) {
                return fail("цель '" + targetText + "' не найдена: некому встречаться");
            }
            target = found.get();
            approach.begin(target, SocialTuning.MEET_ARRIVAL);
        }
        if (target.isDeadOrDying() || target.isRemoved()) {
            approach.cancel();
            return fail("цель '" + targetText + "' пропала по дороге: встречать нечего");
        }
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT -> fail("не дошёл до цели '" + targetText + "' за " + approach.timeoutTicks()
                    + " тиков, расстояние: "
                    + spirit.position().distanceTo(target.position()));
            case STUCK -> fail("застрял по дороге к цели '" + targetText + "' в (x="
                    + spirit.getX() + ", y=" + spirit.getY() + ", z=" + spirit.getZ() + ")");
        };
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

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
