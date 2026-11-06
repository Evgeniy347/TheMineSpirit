package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BridgeParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BridgeScript implements ScriptInstance {
    private static final int GAP_SCAN_LIMIT = 64;
    private static final double STAND_ARRIVAL = 1.25;

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockCoord target;
    private final int length;
    private final String requestedBlock;
    private final Approach approach;
    private final BlockPlacer placer;

    private String blockId;
    private BlockState wanted;
    private BlockCoord step;
    private List<BlockCoord> cells;
    private int placed;
    private int used;
    private boolean walking;
    private boolean initialized;
    private boolean zoneChecked;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public BridgeScript(SpiritEntity spirit, BridgeParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = new BlockCoord(params.x(), params.y(), params.z());
        this.length = params.length();
        this.requestedBlock = params.block();
        this.approach = new Approach(spirit);
        this.placer = new BlockPlacer(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время постройки моста");
        }
        if (!zoneChecked) {
            zoneChecked = true;
            String refusal = TravelCheck.refusal(spirit, target);
            if (refusal != null) {
                return fail(refusal);
            }
        }
        if (!initialized && !initialize()) {
            return outcome;
        }
        if (walking) {
            return tickWalk();
        }
        return tickBuild();
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
    public Optional<Progress> progress() {
        return Optional.of(new Progress(placed, length));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        if (cells == null) {
            return Optional.of(new ScriptResult.Text("переправа по сплошному полу: мост не потребовался"));
        }
        return Optional.of(
                new ScriptResult.Text("мост длиной " + cells.size() + " поставлен, израсходовано " + used + " блоков"));
    }

    private boolean initialize() {
        BlockPos feet = spirit.blockPosition();
        int dx = target.x() - feet.getX();
        int dz = target.z() - feet.getZ();
        step = Math.abs(dx) >= Math.abs(dz)
                ? new BlockCoord(Integer.signum(dx), 0, 0)
                : new BlockCoord(0, 0, Integer.signum(dz));
        BlockCoord base = new BlockCoord(feet.getX(), feet.getY() - 1, feet.getZ());
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        BlockCoord start = findGapStart(base, Math.min(span, GAP_SCAN_LIMIT));
        if (start == null) {
            walking = true;
            beginFinalWalk();
            initialized = true;
            return true;
        }
        cells = new ArrayList<>();
        BlockCoord cell = start;
        for (int i = 0; i < length; i++) {
            cells.add(cell);
            cell = new BlockCoord(cell.x() + step.x(), cell.y(), cell.z() + step.z());
        }
        String resolved = requestedBlock != null
                ? requestedBlock
                : WaterCrossingPlanner.cheapestAvailable(CheapBlocks.counts(spirit))
                        .orElse(null);
        if (resolved == null) {
            fail("нет материала для моста: нужны земля, булыжник или доски, есть 0");
            return false;
        }
        blockId = resolved;
        wanted = BlockStates.stateFor(blockId);
        if (CheapBlocks.count(spirit, blockId) < length) {
            fail("нет материала '" + blockId + "': нужно " + length + ", есть " + CheapBlocks.count(spirit, blockId));
            return false;
        }
        initialized = true;
        return true;
    }

    private void beginFinalWalk() {
        approach.begin(
                Vec3.atBottomCenterOf(new BlockPos(target.x(), target.y(), target.z())),
                CatalogLimits.NAV_ARRIVAL_TOLERANCE);
    }

    private BlockCoord findGapStart(BlockCoord base, int span) {
        BlockCoord cell = base;
        for (int i = 0; i < span; i++) {
            cell = new BlockCoord(cell.x() + step.x(), cell.y(), cell.z() + step.z());
            if (!solidFloor(cell)) {
                return cell;
            }
        }
        return null;
    }

    private ScriptStatus tickBuild() {
        if (placed >= cells.size()) {
            walking = true;
            beginFinalWalk();
            return tickWalk();
        }
        BlockCoord cell = cells.get(placed);
        String hazard = hazard(cell);
        if (hazard != null) {
            return fail(hazard);
        }
        if (solidFloor(cell)) {
            placed++;
            return ScriptStatus.RUNNING;
        }
        ScriptStatus stand = approachStand(cell);
        if (stand != null) {
            return stand;
        }
        BlockPlacer.Begin begin = placer.begin(new BlockPos(cell.x(), cell.y(), cell.z()), wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            used++;
            return ScriptStatus.RUNNING;
        }
        if (begin instanceof BlockPlacer.Protected protectedBlock) {
            return fail("ставить запрещено: " + protectedBlock.reason());
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> fail("нет материала '" + blockId + "': нужно " + length + ", есть " + used);
            case OCCUPIED -> fail("клетка " + toShort(cell) + " занята: мост некуда ставить");
            case NO_SUPPORT -> fail("под клеткой " + toShort(cell) + " нет опоры: блок повиснет");
            case OUT_OF_REACH -> fail("клетка " + toShort(cell) + " вне досягаемости");
            case CHUNK_NOT_LOADED -> fail("чанк с клеткой " + toShort(cell) + " не загружен");
        };
    }

    private ScriptStatus approachStand(BlockCoord cell) {
        Vec3 stand = new Vec3(cell.x() - step.x() + 0.5, cell.y() + 1, cell.z() - step.z() + 0.5);
        if (spirit.position().distanceTo(stand) <= STAND_ARRIVAL) {
            return null;
        }
        if (!approach.isActive()) {
            approach.begin(stand, STAND_ARRIVAL);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> null;
            case STUCK, TIMEOUT -> fail("не подошёл к месту постановки блока " + toShort(cell));
        };
    }

    private ScriptStatus tickWalk() {
        return switch (approach.tick()) {
            case ARRIVED -> {
                outcome = ScriptStatus.SUCCEEDED;
                yield outcome;
            }
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT ->
                fail("не перешёл мост к (x=" + target.x() + ", y=" + target.y() + ", z=" + target.z() + ") за "
                        + approach.timeoutTicks() + " тиков");
            case STUCK ->
                fail("не перешёл мост к (x=" + target.x() + ", y=" + target.y() + ", z=" + target.z()
                        + "): путь перекрыт");
        };
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
    }

    private boolean solidFloor(BlockCoord cell) {
        BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced();
    }

    private String hazard(BlockCoord cell) {
        if (level.getFluidState(new BlockPos(cell.x(), cell.y(), cell.z())).is(FluidTags.LAVA)) {
            return "в клетке " + toShort(cell) + " лава: мост через лаву не строится";
        }
        if (level.getFluidState(new BlockPos(cell.x(), cell.y() - 1, cell.z())).is(FluidTags.LAVA)) {
            return "под клеткой " + toShort(cell) + " лава: мост над лавой не строится";
        }
        return null;
    }

    private static String toShort(BlockCoord cell) {
        return "(" + cell.x() + ", " + cell.y() + ", " + cell.z() + ")";
    }
}
