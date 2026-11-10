package com.theminespirit.neoforge;

import com.theminespirit.core.board.WalkableStand;
import com.theminespirit.core.board.WorkStand;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.GroundProbe;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.movement.NetworkLevelView;
import com.theminespirit.neoforge.movement.TravelCheck;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
    private static final int SURFACE_SEARCH = 8;
    private static final int SNAP_RANGE = 16;
    private static final int SNAP_MARGIN = 4;
    private static final int NO_GROUND = Integer.MIN_VALUE;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final Approach approach;
    private final PathMode mode;
    private final String noPath;
    private String failureReason;
    private boolean zoneChecked;
    private boolean movementFailure;

    public GotoScript(SpiritEntity spirit, GotoParams params) {
        this(spirit, params, PathMode.NORMAL);
    }

    private GotoScript(SpiritEntity spirit, GotoParams params, PathMode mode) {
        this.spirit = spirit;
        this.mode = mode;
        Resolved resolved =
                resolve(spirit, outsideHouse(spirit.level(), new BlockPos(params.x(), params.y(), params.z())));
        this.target = surface(spirit.level(), resolved.point());
        this.noPath = resolved.refusal();
        this.approach = new Approach(spirit);
        if (mode == PathMode.NORMAL) {
            this.approach.beginWithRoutes(target, ARRIVAL_TOLERANCE);
        } else {
            this.approach.begin(target, ARRIVAL_TOLERANCE, mode);
        }
    }

    private record Resolved(BlockPos point, String refusal) {}

    private static Resolved resolve(SpiritEntity spirit, BlockPos point) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return new Resolved(point, null);
        }
        BlockPos feetPos = spirit.blockPosition();
        BlockCoord feet = new BlockCoord(feetPos.getX(), feetPos.getY(), feetPos.getZ());
        BlockCoord p = new BlockCoord(point.getX(), point.getY(), point.getZ());
        long dx = (long) p.x() - feet.x();
        long dz = (long) p.z() - feet.z();
        if (dx * dx + dz * dz > (long) SNAP_RANGE * SNAP_RANGE || freeAt(level, p)) {
            return new Resolved(point, null);
        }
        int r = WorkStand.RADIUS + SNAP_MARGIN;
        int minX = Math.min(feet.x(), p.x()) - r;
        int maxX = Math.max(feet.x(), p.x()) + r;
        int minY = Math.min(feet.y(), p.y()) - r;
        int maxY = Math.max(feet.y(), p.y()) + r;
        int minZ = Math.min(feet.z(), p.z()) - r;
        int maxZ = Math.max(feet.z(), p.z()) + r;
        Optional<Set<BlockCoord>> walkable = RetreatPath.walkable(
                feet,
                new NetworkLevelView(level),
                cell -> cell.x() >= minX
                        && cell.x() <= maxX
                        && cell.y() >= minY
                        && cell.y() <= maxY
                        && cell.z() >= minZ
                        && cell.z() <= maxZ);
        if (walkable.isEmpty()) {
            return new Resolved(point, null);
        }
        Optional<BlockCoord> stand = WalkableStand.near(p, walkable.orElseThrow());
        if (stand.isEmpty()) {
            stand = WorkStand.near(p, ground(level), walkable.orElseThrow()::contains);
        }
        if (stand.isEmpty()) {
            return new Resolved(
                    point,
                    "пути нет к точке " + p + " от " + feet
                            + ": ни одной стоянки рядом, до которой дух дойдёт пешком, а цель занята блоком");
        }
        BlockCoord found = stand.orElseThrow();
        return new Resolved(new BlockPos(found.x(), found.y(), found.z()), null);
    }

    private static boolean freeAt(Level level, BlockCoord feet) {
        if (feet.y() == NO_GROUND) {
            return false;
        }
        BlockPos pos = new BlockPos(feet.x(), feet.y(), feet.z());
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above())
                        .isEmpty()
                && level.getFluidState(pos).isEmpty()
                && !level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below())
                        .isEmpty();
    }

    private static WorkStand.Ground ground(ServerLevel level) {
        return new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return GroundProbe.feet(level, x, z, NO_GROUND);
            }

            @Override
            public boolean free(BlockCoord feet) {
                return freeAt(level, feet);
            }
        };
    }

    private static BlockPos outsideHouse(Level level, BlockPos goal) {
        if (!(level instanceof ServerLevel server)) {
            return goal;
        }
        Optional<StageSite> site = SettlementPlotStore.liveSite(server);
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
        if (noPath != null) {
            approach.cancel();
            failureReason = noPath;
            movementFailure = true;
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
                movementFailure = true;
                yield ScriptStatus.FAILED;
            }
            case STUCK -> {
                failureReason = stuckReason();
                movementFailure = true;
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

    @Override
    public Optional<FailureFact> failureFact() {
        return movementFailure ? Optional.of(FailureFact.NO_PATH) : Optional.empty();
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
