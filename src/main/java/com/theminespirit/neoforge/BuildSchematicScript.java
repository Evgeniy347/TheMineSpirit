package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildSchematicParams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class BuildSchematicScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final Level level;
    private final String file;
    private final BlockCoord origin;
    private final List<Schematic.PlacedEntry> entries;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private int cursor;
    private int placed;
    private int skipped;
    private int failed;
    private int protectedSkipped;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private final BuildResult tally = new BuildResult();

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.file = params.file();
        this.origin = new BlockCoord(params.x(), params.y(), params.z());
        this.entries = SchematicResources.load(params.file()).entries(origin);
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки по схеме");
        }
        while (cursor < entries.size()) {
            Schematic.PlacedEntry entry = entries.get(cursor);
            BlockState current = level.getBlockState(toPos(entry.pos()));
            BlockState wanted = BlockStates.stateFor(entry.block());
            if (current.is(wanted.getBlock())) {
                cursor++;
                continue;
            }
            if (reservations.foreign(entry.pos(), spirit.getUUID())) {
                skipped++;
                cursor++;
                continue;
            }
            reservations.reserve(entry.pos(), spirit.getUUID());
            if (!approach.isActive()) {
                List<BlockCoord> stands = StandSpots.of(entry.pos(), safety, spiritCoord());
                if (stands.isEmpty()) {
                    skipped++;
                    release(entry.pos());
                    cursor++;
                    continue;
                }
                BlockCoord stand = stands.get(0);
                approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            }
            switch (approach.tick()) {
                case MOVING -> {
                    return ScriptStatus.RUNNING;
                }
                case ARRIVED -> place(entry, wanted);
                case STUCK, TIMEOUT -> {
                    skipped++;
                    release(entry.pos());
                    cursor++;
                }
            }
            return ScriptStatus.RUNNING;
        }
        reservations.releaseAll(spirit.getUUID());
        if (failed > 0) {
            return outcome;
        }
        if (placed == 0 && skipped == entries.size() && entries.size() > 0) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        tally.supplies(placed, 0);
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
    }

    private void place(Schematic.PlacedEntry entry, BlockState wanted) {
        BlockPlacer.Begin begin = placer.begin(toPos(entry.pos()), wanted, entry.block());
        release(entry.pos());
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            tally.placed(entry.block());
            cursor++;
            return;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            protectedSkipped++;
            skipped++;
            tally.skipped();
            cursor++;
            if (placed == 0 && cursor >= entries.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + entries.size());
            }
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
            failed++;
            tally.failed();
            cursor++;
            int have = placed + BlockItems.countIn(spirit.getInventory(), entry.block());
            tally.supplies(have, Math.max(countNeeded(entry.block()) - BlockItems.countIn(spirit.getInventory(), entry.block()), 0));
            fail("не хватило материала '" + entry.block() + "': поставлено " + placed + " из " + entries.size()
                    + "; нужно " + countNeeded(entry.block()) + ", есть " + BlockItems.countIn(spirit.getInventory(), entry.block()));
            return;
        }
        skipped++;
        tally.skipped();
        cursor++;
    }

    private int countNeeded(String blockId) {
        int total = 0;
        for (Schematic.PlacedEntry entry : entries) {
            if (entry.block().equals(blockId)) {
                BlockState current = level.getBlockState(toPos(entry.pos()));
                if (!current.is(BlockStates.blockFor(blockId))) {
                    total++;
                }
            }
        }
        return Math.max(total, 1);
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        int total = Math.max(entries.size(), 1);
        return Optional.of(new Progress(Math.min(cursor, entries.size()), total));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED ? Optional.of(new ScriptResult.Data(tally.toData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
        reservations.releaseAll(spirit.getUUID());
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
    public Map<String, Object> saveState() {
        Map<String, Object> base = new HashMap<>(new BuildState(placed, 0, skipped, failed, outcome == ScriptStatus.RUNNING).toMap());
        base.put("cursor", cursor);
        return Map.copyOf(base);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        placed = Math.max(placed, snapshot.placed());
        skipped = Math.max(skipped, snapshot.skipped());
        failed = Math.max(failed, snapshot.failed());
        String kind = entries.isEmpty() ? "" : entries.get(0).block();
        for (int i = 0; i < snapshot.placed() && !kind.isEmpty(); i++) {
            tally.placed(kind);
        }
        for (int i = 0; i < snapshot.skipped(); i++) {
            tally.skipped();
        }
        for (int i = 0; i < snapshot.failed(); i++) {
            tally.failed();
        }
        if (state.get("cursor") instanceof Number number) {
            cursor = Math.max(cursor, Math.min(number.intValue(), entries.size()));
        }
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private void release(BlockCoord cell) {
        reservations.release(cell, spirit.getUUID());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
