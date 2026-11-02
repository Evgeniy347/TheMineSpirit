package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.movement.TravelCheck;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
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
        this.target =
                surface(spirit.level(), outsideHouse(spirit.level(), new BlockPos(params.x(), params.y(), params.z())));
        this.approach = new Approach(spirit);
        if (mode == PathMode.NORMAL) {
            this.approach.beginWithRoutes(target, ARRIVAL_TOLERANCE);
        } else {
            this.approach.begin(target, ARRIVAL_TOLERANCE, mode);
        }
    }

    private static BlockPos outsideHouse(Level level, BlockPos goal) {
        if (!(level instanceof ServerLevel server)) {
            return goal;
        }
        Optional<StageSite> site = SettlementPlotStore.get(server).site();
        if (site.isEmpty()) {
            return goal;
        }
        BlockCoord anchor = site.orElseThrow().anchor();
        boolean inHut = site.orElseThrow().houseColumn(goal.getX(), goal.getZ())
                && goal.getY() >= anchor.y() - 1
                && goal.getY() <= anchor.y() + StageSite.BUILDING_HEIGHT;
        BlockPos floor = new BlockPos(goal.getX(), anchor.y() - 1, goal.getZ());
        BlockPos wallFeet = new BlockPos(goal.getX(), anchor.y(), goal.getZ());
        boolean built = !level.getBlockState(floor).is(BlockTags.DIRT)
                || !level.getBlockState(wallFeet).isAir();
        if (!inHut || !built) {
            return goal;
        }
        BlockCoord yard = site.orElseThrow().workYard();
        return new BlockPos(yard.x(), yard.y(), yard.z());
    }

    private static Vec3 surface(Level level, BlockPos goal) {
        if (level.getBlockState(goal).isAir()
                && level.getBlockState(goal.below()).isAir()) {
            BlockPos at = goal.below();
            for (int step = 1;
                    step < SURFACE_SEARCH && level.getBlockState(at.below()).isAir();
                    step++) {
                at = at.below();
            }
            return level.getBlockState(at.below()).isAir() ? Vec3.atBottomCenterOf(goal) : Vec3.atBottomCenterOf(at);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (open(level, goal.relative(side))) {
                return Vec3.atBottomCenterOf(goal);
            }
        }
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
        String why = approach.refusal() == null ? "" : ": " + approach.refusal();
        return "застрял в " + point(spirit.position()) + " по пути к " + point(target) + why;
    }

    private static String point(Vec3 at) {
        return "(x=" + at.x + ", y=" + at.y + ", z=" + at.z + ")";
    }
}
