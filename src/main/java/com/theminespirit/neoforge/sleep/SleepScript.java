package com.theminespirit.neoforge.sleep;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.sleep.BedChoice;
import com.theminespirit.core.sleep.SleepFacts;
import com.theminespirit.core.sleep.SleepRule;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SleepScript implements ScriptInstance {
    static final String NO_FREE_BED = "кровать не найдена: в поселении нет свободной кровати";

    private static final double ARRIVAL = 1.25;
    private static final double THREAT_RADIUS = 16.0;

    private final SpiritEntity spirit;
    private final Approach approach;
    private final NightLeash leash = NightLeash.withDefaultRules();
    private BlockCoord target;
    private boolean lying;
    private String failureReason;
    private ScriptResult result;

    public SleepScript(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, не успев уснуть");
        }
        SleepFacts facts = facts();
        if (lying) {
            if (!spirit.isSleeping()) {
                return ScriptStatus.SUCCEEDED;
            }
            if (!SleepRule.timeToSleep(facts)) {
                spirit.stopSleeping();
                return ScriptStatus.SUCCEEDED;
            }
            return ScriptStatus.RUNNING;
        }
        if (!SleepRule.timeToSleep(facts)) {
            return ScriptStatus.SUCCEEDED;
        }
        if (target == null) {
            Optional<BlockCoord> bed = pickBed();
            if (bed.isEmpty()) {
                return fail(NO_FREE_BED);
            }
            target = bed.get();
            approach.begin(Vec3.atCenterOf(Beds.pos(target)), ARRIVAL);
        }
        return switch (approach.tick()) {
            case ARRIVED -> lieDown();
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT -> fail(unreachable("путь не найден за отведённое время"));
            case STUCK -> fail(unreachable(approach.refusal()));
        };
    }

    @Override
    public void cancel() {
        approach.cancel();
        if (lying) {
            spirit.stopSleeping();
            lying = false;
        }
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private ScriptStatus lieDown() {
        if (!Beds.isBed(spirit.level(), target) || Beds.occupied(spirit.level(), target)) {
            target = null;
            return ScriptStatus.RUNNING;
        }
        spirit.startSleeping(Beds.pos(target));
        spirit.setBed(target);
        lying = true;
        result = new ScriptResult.Data(Map.of("bed", target.toString(), "slept", true));
        return ScriptStatus.RUNNING;
    }

    private Optional<BlockCoord> pickBed() {
        Optional<BlockCoord> home = spirit.home();
        if (home.isEmpty()) {
            return Optional.empty();
        }
        int radius = leash.settlementRadius();
        List<BlockCoord> beds = Beds.find(spirit.level(), spirit.blockPosition(), radius);
        Set<BlockCoord> taken = Beds.taken(spirit.level(), beds);
        return BedChoice.choose(spirit.bed(), beds, taken, cell(), home.get(), radius);
    }

    private SleepFacts facts() {
        boolean night = SleepRule.isNight(SleepRule.dayFraction(spirit.level().getDayTime()));
        boolean danger = spirit.hurtTime > 0 || threatNear();
        boolean inside = spirit.home()
                .map(home -> leash.insideSettlement(cell(), home, leash.settlementRadius()))
                .orElse(false);
        boolean defence = spirit.isDefenceAssigned();
        return new SleepFacts(night, danger, inside, defence);
    }

    private boolean threatNear() {
        AABB box = spirit.getBoundingBox().inflate(THREAT_RADIUS);
        return !spirit.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> !entity.equals(spirit) && entity.isAlive() && entity instanceof Enemy)
                .isEmpty();
    }

    private BlockCoord cell() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        if (lying) {
            spirit.stopSleeping();
            lying = false;
        }
        return ScriptStatus.FAILED;
    }

    private static String unreachable(String detail) {
        return detail == null || detail.isBlank()
                ? "кровать недоступна: путь перекрыт"
                : "кровать недоступна: " + detail;
    }
}
