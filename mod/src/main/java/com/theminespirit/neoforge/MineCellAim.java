package com.theminespirit.neoforge;

import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class MineCellAim {
    enum Outcome {
        RUNNING,
        RETRY,
        FAILED
    }

    private static final double ARRIVAL = 0.6;
    private static final int WAIT_TICKS = 200;

    private final SpiritEntity spirit;
    private final Level level;
    private final Approach approach;
    private final List<BlockCoord> stands = new ArrayList<>();
    private final Set<BlockCoord> tried = new HashSet<>();
    private @Nullable BlockCoord cell;
    private @Nullable String failure;
    private String reason = "";
    private int choice;
    private int waited;
    private boolean walking;
    private boolean waiting;

    MineCellAim(SpiritEntity spirit) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому подходить");
        this.level = spirit.level();
        this.approach = new Approach(spirit);
    }

    void beginApproach(BlockCoord target, List<BlockCoord> offered, String why) {
        Objects.requireNonNull(target, "Клетка не может быть null: без неё неизвестно, к чему подходить");
        Objects.requireNonNull(offered, "Стоянки не могут быть null: пустой список — отказ");
        restart(target);
        reason = why;
        stands.clear();
        stands.addAll(offered);
        choice = 0;
        waiting = false;
        nextStand();
    }

    void beginWait(BlockCoord target, String why) {
        Objects.requireNonNull(target, "Клетка не может быть null: без неё неизвестно, кого ждать");
        restart(target);
        reason = why;
        waited = 0;
        waiting = true;
        stands.clear();
    }

    @Nullable
    BlockCoord cell() {
        return cell;
    }

    boolean tried(BlockCoord stand) {
        return tried.contains(stand);
    }

    @Nullable
    String failure() {
        return failure;
    }

    Outcome tick() {
        BlockCoord target = cell;
        if (target == null) {
            return Outcome.RETRY;
        }
        if (waiting) {
            return waitTick(target);
        }
        if (failure != null) {
            return Outcome.FAILED;
        }
        if (!walking) {
            return Outcome.RUNNING;
        }
        return switch (approach.tick()) {
            case MOVING -> Outcome.RUNNING;
            case ARRIVED -> {
                tried.add(stands.get(choice));
                stopWalking();
                yield Outcome.RETRY;
            }
            case STUCK, TIMEOUT -> {
                tried.add(stands.get(choice));
                choice++;
                stopWalking();
                if (choice < stands.size()) {
                    nextStand();
                    yield failure == null ? Outcome.RUNNING : Outcome.FAILED;
                }
                failure = "к " + target + " не подойти: " + reason + "; испробованы стоянки " + stands;
                yield Outcome.FAILED;
            }
        };
    }

    void reset() {
        approach.cancel();
        cell = null;
        failure = null;
        reason = "";
        stands.clear();
        tried.clear();
        choice = 0;
        waited = 0;
        walking = false;
        waiting = false;
    }

    static @Nullable LivingEntity occupant(Level level, SpiritEntity spirit, BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        List<LivingEntity> found = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(pos),
                entity -> !entity.equals(spirit) && !entity.isRemoved() && !entity.isSpectator());
        return found.isEmpty() ? null : found.get(0);
    }

    static String describe(LivingEntity entity) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        BlockPos pos = entity.blockPosition();
        return type + " в (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private Outcome waitTick(BlockCoord target) {
        LivingEntity current = occupant(level, spirit, target);
        if (current == null) {
            waiting = false;
            return Outcome.RETRY;
        }
        if (++waited > WAIT_TICKS) {
            failure = "клетку " + target + " занимает " + describe(current) + ": дух ждал " + WAIT_TICKS
                    + " тиков, место не освободилось";
            waiting = false;
            return Outcome.FAILED;
        }
        return Outcome.RUNNING;
    }

    private void restart(BlockCoord target) {
        if (!target.equals(cell)) {
            reset();
            cell = target;
        }
        failure = null;
        reason = "";
    }

    private void nextStand() {
        if (stands.isEmpty() || choice >= stands.size()) {
            failure = "к " + cell + " не подойти: " + reason + "; стоянок с прямой видимостью нет";
            walking = false;
            return;
        }
        BlockCoord stand = stands.get(choice);
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), ARRIVAL);
        walking = true;
    }

    static String context(Level level, SpiritEntity spirit, List<MineColumn> plan, BlockCoord cell) {
        boolean planCell = level instanceof ServerLevel server
                && SettlementPlotStore.liveSite(server)
                        .map(site -> site.planCell(cell.x(), cell.y(), cell.z()))
                        .orElse(false);
        boolean inPlan = plan.stream()
                .anyMatch(column -> column.contains(cell) || column.floor().equals(cell));
        Vec3 eye = spirit.getEyePosition();
        Optional<BlockCoord> blocker =
                BlockSight.firstBlocker(new Point(eye.x, eye.y, eye.z), cell, new LoadedTerrain(level), coord -> false);
        return "; inPlan=" + inPlan + ", planCell=" + planCell + ", глаза=(" + eye.x + ", " + eye.y + ", " + eye.z
                + "), мешающий блок=" + blocker.map(BlockCoord::toString).orElse("нет");
    }

    private void stopWalking() {
        walking = false;
    }
}
