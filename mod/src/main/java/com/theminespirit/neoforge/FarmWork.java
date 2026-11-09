package com.theminespirit.neoforge;

import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.HarvestMethod;
import com.theminespirit.core.farming.SeedPolicy;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class FarmWork {
    private final SpiritEntity spirit;
    private final FarmJob job;
    private final CropKind crop;
    private final int count;
    private final boolean replant;
    private final LevelFarmView view;
    private final FarmActions actions;
    private final HarvestTargets targets;
    private final FarmCounters counters;
    private final FarmSearch search;
    private final FarmLoot loot;
    private final BlockBreaker breaker;
    private final Consumer<String> fail;
    private final Function<BlockPos, BlockBreaker.Begin> beginUnlimited;

    private CropKind currentKind;
    private BlockCoord cropPos;
    private int columnHeight;

    FarmWork(
            SpiritEntity spirit,
            FarmJob job,
            CropKind crop,
            int count,
            boolean replant,
            LevelFarmView view,
            FarmActions actions,
            HarvestTargets targets,
            FarmCounters counters,
            FarmSearch search,
            FarmLoot loot,
            BlockBreaker breaker,
            Consumer<String> fail,
            Function<BlockPos, BlockBreaker.Begin> beginUnlimited) {
        this.spirit = spirit;
        this.job = job;
        this.crop = crop;
        this.count = count;
        this.replant = replant;
        this.view = view;
        this.actions = actions;
        this.targets = targets;
        this.counters = counters;
        this.search = search;
        this.loot = loot;
        this.breaker = breaker;
        this.fail = fail;
        this.beginUnlimited = beginUnlimited;
    }

    CropKind kind() {
        return currentKind;
    }

    BlockCoord cropPos() {
        return cropPos;
    }

    void restoreKind(CropKind kind) {
        currentKind = kind;
    }

    FarmSession.State act() {
        FarmTile tile = view.tileAt(search.ground());
        if (!job.eligible(tile, crop, targets.hydration().watered(search.ground()))) {
            counters.addIneligible();
            finishTarget();
            return FarmSession.State.PICK;
        }
        BlockPos ground = FarmSearch.posOf(search.ground());
        return switch (job) {
            case TILL -> tillOrClearWeed(tile, ground);
            case PLANT -> plantNow(ground);
            case HARVEST -> {
                currentKind = tile.crop().orElseThrow().kind();
                yield harvestCrop(ground);
            }
            case BONE_MEAL -> boneMealNow(ground.above());
        };
    }

    private FarmSession.State harvestCrop(BlockPos ground) {
        HarvestMethod method = currentKind.harvestMethod();
        if (method == HarvestMethod.PICK) {
            return pickNow(ground.above(), currentKind);
        }
        if (method == HarvestMethod.BREAK_COLUMN) {
            int height = view.columnHeight(search.ground());
            if (height < 2) {
                counters.addIneligible();
                finishTarget();
                return FarmSession.State.PICK;
            }
            columnHeight = height;
            return beginBreak(ground.above(height));
        }
        if (method == HarvestMethod.BREAK_FRUIT) {
            return harvestAttached(view.fruitAt(search.ground(), currentKind));
        }
        if (currentKind.hasSupport()) {
            return harvestAttached(view.attachedCrop(search.ground(), currentKind));
        }
        return beginBreak(ground.above());
    }

    private FarmSession.State harvestAttached(Optional<BlockCoord> target) {
        if (target.isEmpty()) {
            counters.addIneligible();
            finishTarget();
            return FarmSession.State.PICK;
        }
        cropPos = target.get();
        return beginBreak(FarmSearch.posOf(cropPos));
    }

    private FarmSession.State tillOrClearWeed(FarmTile tile, BlockPos ground) {
        if (tile.above() == Above.WEED) {
            return beginBreak(ground.above());
        }
        counters.recordAction(spirit.tickCount);
        if (actions.till(ground)) {
            counters.addDone();
        } else {
            counters.addRejected();
        }
        finishTarget();
        return FarmSession.State.PICK;
    }

    private FarmSession.State plantNow(BlockPos ground) {
        int seeds = actions.count(CropRegistry.plantItem(crop));
        if (SeedPolicy.forPlanting(crop, seeds, count - counters.done()) == 0) {
            fail.accept(counters.lostSuppliesReason());
            return FarmSession.State.ACT;
        }
        counters.recordAction(spirit.tickCount);
        if (plantAt(crop, ground)) {
            counters.addDone();
        } else {
            counters.addRejected();
        }
        finishTarget();
        return FarmSession.State.PICK;
    }

    private boolean plantAt(CropKind kind, BlockPos ground) {
        if (!kind.hasSupport()) {
            return actions.plant(ground, kind);
        }
        Optional<BlockCoord> spot = view.attachedSpot(search.ground(), kind);
        if (spot.isEmpty()) {
            return false;
        }
        Direction side = Direction.fromDelta(
                spot.get().x() - ground.getX(), 0, spot.get().z() - ground.getZ());
        if (side == null) {
            throw new IllegalStateException("Клетка посадки " + spot.get() + " не сосед опоры " + ground
                    + ": какао растёт только на боку ствола");
        }
        return actions.plantOnSide(ground, side, kind);
    }

    private FarmSession.State boneMealNow(BlockPos pos) {
        counters.recordAction(spirit.tickCount);
        if (actions.boneMeal(pos)) {
            counters.addDone();
            if (job.eligible(view.tileAt(search.ground()), crop, false)) {
                search.reoffer(search.ground());
            }
        } else {
            counters.addRejected();
        }
        finishTarget();
        return FarmSession.State.PICK;
    }

    private FarmSession.State beginBreak(BlockPos target) {
        if (beginUnlimited.apply(target) instanceof BlockBreaker.Started) {
            return FarmSession.State.BREAK;
        }
        counters.addNoStand();
        finishTarget();
        return FarmSession.State.PICK;
    }

    private FarmSession.State pickNow(BlockPos target, CropKind kind) {
        counters.recordAction(spirit.tickCount);
        if (actions.pick(target, kind)) {
            counters.addDone();
            loot.startCollect(currentKind, cropPos, search.ground());
            return FarmSession.State.COLLECT;
        }
        counters.addRejected();
        finishTarget();
        return FarmSession.State.PICK;
    }

    FarmSession.State breakTick() {
        counters.recordAction(spirit.tickCount);
        return switch (breaker.tick()) {
            case BREAKING -> FarmSession.State.BREAK;
            case BROKEN -> afterBroken();
            case GONE -> {
                if (job == FarmJob.HARVEST) {
                    finishTarget();
                    yield FarmSession.State.PICK;
                }
                yield FarmSession.State.ACT;
            }
        };
    }

    private FarmSession.State afterBroken() {
        if (job != FarmJob.HARVEST) {
            return FarmSession.State.ACT;
        }
        if (currentKind.harvestMethod() == HarvestMethod.BREAK_COLUMN) {
            BlockPos broken = FarmSearch.posOf(search.ground()).above(columnHeight);
            actions.rescueDrops(broken);
            columnHeight--;
            if (columnHeight >= 2) {
                return beginBreak(FarmSearch.posOf(search.ground()).above(columnHeight));
            }
        }
        counters.addDone();
        loot.startCollect(currentKind, cropPos, search.ground());
        return FarmSession.State.COLLECT;
    }

    FarmSession.State replantTick() {
        int seeds = actions.count(CropRegistry.plantItem(currentKind));
        if (SeedPolicy.forReplant(currentKind, seeds) == SeedPolicy.Decision.OUT_OF_SEEDS) {
            counters.addNoSeeds();
        } else {
            counters.recordAction(spirit.tickCount);
            if (!plantAt(currentKind, FarmSearch.posOf(search.ground()))) {
                counters.addUnreachable();
            }
        }
        finishTarget();
        return FarmSession.State.PICK;
    }

    void finishTarget() {
        search.clearGround();
        currentKind = null;
        cropPos = null;
        columnHeight = 0;
    }

    Map<String, Object> save(int done, FarmSession.State state) {
        boolean replantPending = replant
                && search.ground() != null
                && currentKind != null
                && currentKind.harvestMethod() == HarvestMethod.BREAK
                && !currentKind.hasSupport()
                && (state == FarmSession.State.COLLECT || state == FarmSession.State.REPLANT);
        if (!replantPending) {
            return Map.of(FarmSession.DONE_KEY, done);
        }
        return Map.of(
                FarmSession.DONE_KEY,
                done,
                FarmSession.REPLANT_X_KEY,
                search.ground().x(),
                FarmSession.REPLANT_Y_KEY,
                search.ground().y(),
                FarmSession.REPLANT_Z_KEY,
                search.ground().z(),
                FarmSession.REPLANT_KIND_KEY,
                currentKind.name());
    }
}
