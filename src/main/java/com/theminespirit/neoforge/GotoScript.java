package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.neoforge.movement.TravelCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
    // Насколько выше точки в толще земли ищется поверхность её колонки.
    private static final int SURFACE_SEARCH = 8;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private final PathMode mode;
    private String failureReason;
    private boolean zoneChecked;

    public GotoScript(SpiritEntity spirit, GotoParams params) {
        this(spirit, params, PathMode.NORMAL);
    }

    private GotoScript(SpiritEntity spirit, GotoParams params, PathMode mode) {
        this.spirit = spirit;
        this.mode = mode;
        this.target = surface(spirit.level(), new BlockPos(params.x(), params.y(), params.z()));
        this.approach = new Approach(spirit);
        this.approach.begin(target, ARRIVAL_TOLERANCE, mode);
    }

    // Точка в толще земли (якорь участка под ещё не срезанным холмом) — это поверхность её колонки: дух стоял на
    // холме над якорем и «застревал в тупике» в 4 блоках от цели (сцена 56). Свободная точка не меняется.
    private static Vec3 surface(Level level, BlockPos goal) {
        BlockPos at = goal;
        for (int step = 0; step <= SURFACE_SEARCH; step++) {
            if (open(level, at) && open(level, at.above())) {
                return Vec3.atBottomCenterOf(at);
            }
            if (step == 0 && open(level, at)) {
                break;
            }
            at = at.above();
        }
        return Vec3.atBottomCenterOf(goal);
    }

    private static boolean open(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static GotoScript careful(SpiritEntity spirit, WalkCarefulParams params) {
        return new GotoScript(spirit, new GotoParams(params.x(), params.y(), params.z()), PathMode.CAREFUL);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            failureReason = "дух исчез до достижения цели";
            return ScriptStatus.FAILED;
        }
        if (!zoneChecked) {
            zoneChecked = true;
            BlockCoord goal =
                    new BlockCoord((int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z));
            String refusal = TravelCheck.refusal(spirit, goal);
            if (refusal != null) {
                approach.cancel();
                failureReason = refusal;
                return ScriptStatus.FAILED;
            }
        }
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT -> {
                failureReason = failedToArriveReason();
                yield ScriptStatus.FAILED;
            }
            case STUCK -> {
                failureReason = stuckReason();
                yield ScriptStatus.FAILED;
            }
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

    private String failedToArriveReason() {
        return "не дошёл до " + point(target) + " за " + approach.timeoutTicks() + " тиков";
    }

    private String stuckReason() {
        if (mode == PathMode.CAREFUL && approach.refusal() != null) {
            return "недостижимо осторожным шагом до " + point(target) + ": " + approach.refusal();
        }
        // Цель и причина отказа — в сообщении: без них провал «застрял» из лога не повторить (сцена 56).
        String why = approach.refusal() == null ? "" : ": " + approach.refusal();
        return "застрял в " + point(spirit.position()) + " по пути к " + point(target) + why;
    }

    private static String point(Vec3 at) {
        return "(x=" + at.x + ", y=" + at.y + ", z=" + at.z + ")";
    }
}
