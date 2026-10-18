package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildSquad;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.CellPartition;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildSchematicParams;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

final class BuildSchematicScript implements ScriptInstance {
    private static final int MAX_FINISH_WAITS = 200;

    private enum PlaceOutcome {
        DONE,
        RETRY
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockCoord origin;
    private final BuildRotation rotation;
    private final List<Schematic.PlacedEntry> entries;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;
    private final BuildKey buildKey;
    private final BuildSquad squad;

    private List<Schematic.PlacedEntry> work = List.of();
    private long squadVersion = -1;
    private int cursor;
    private int placed;
    private int skipped;
    private int failed;
    private int protectedSkipped;
    private boolean retriedPass;
    private int finishWaits;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private String lastRefusal = "";
    private final BuildResult tally = new BuildResult();

    private List<BlockCoord> stands = List.of();
    private int standIndex;

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.origin = new BlockCoord(params.x(), params.y(), params.z());
        this.rotation = params.rotation();
        this.entries = SchematicResources.load(params.file()).entries(origin, rotation).stream()
                .filter(entry -> BlockItems.hasBlockItem(entry.block()))
                .toList();
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.buildKey = new BuildKey(params.file(), this.origin, rotation);
        this.squad = BuildSquadRegistry.join(level, buildKey, spirit.getUUID());
    }

    private BlockState wantedState(Schematic.PlacedEntry entry) {
        BlockState state = BlockStates.stateFor(entry.block(), entry.state());
        if (rotation == BuildRotation.NONE) {
            return state;
        }
        return state.rotate(level, toPos(entry.pos()), mcRotation());
    }

    private Rotation mcRotation() {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки по схеме");
        }
        refreshWork();
        while (cursor < work.size()) {
            Schematic.PlacedEntry entry = work.get(cursor);
            BlockState current = level.getBlockState(toPos(entry.pos()));
            BlockState wanted = wantedState(entry);
            if (current.is(wanted.getBlock()) && matchesState(current, wanted, entry.state())) {
                resetEntry();
                cursor++;
                continue;
            }
            if (reservations.foreign(entry.pos(), spirit.getUUID())) {
                skipped++;
                cursor++;
                continue;
            }
            reservations.reserve(entry.pos(), spirit.getUUID());
            if (current.is(wanted.getBlock())) {
                repairState(entry.pos(), wanted);
                resetEntry();
                cursor++;
                continue;
            }
            targetTick(entry, wanted);
            return ScriptStatus.RUNNING;
        }
        return finishPass();
    }

    private ScriptStatus finishPass() {
        reservations.releaseAll(spirit.getUUID());
        List<Schematic.PlacedEntry> missing = missingEntries();
        if (missing.isEmpty()) {
            leaveSquad();
            skipped = 0;
            tally.supplies(placed, 0);
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (missing.stream().anyMatch(entry -> reservations.foreign(entry.pos(), spirit.getUUID()))) {
            if (++finishWaits <= MAX_FINISH_WAITS) {
                return ScriptStatus.RUNNING;
            }
        }
        if (!retriedPass) {
            retriedPass = true;
            finishWaits = 0;
            work = List.copyOf(missing);
            cursor = 0;
            resetEntry();
            return ScriptStatus.RUNNING;
        }
        skipped = missing.size();
        return fail("пропущено " + missing.size() + " из " + entries.size() + " клеток схемы: " + sample(missing)
                + "; дух " + spiritCoord() + "; поставлено " + placed + "; стоянок " + stands.size()
                + "; последний отказ: " + lastRefusal);
    }

    private List<Schematic.PlacedEntry> missingEntries() {
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            BlockState wanted = wantedState(entry);
            BlockState current = level.getBlockState(toPos(entry.pos()));
            if (!current.is(wanted.getBlock()) || !matchesState(current, wanted, entry.state())) {
                missing.add(entry);
            }
        }
        return missing;
    }

    private static String sample(List<Schematic.PlacedEntry> missing) {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (Schematic.PlacedEntry entry : missing) {
            if (shown == 8) {
                text.append("…");
                break;
            }
            if (shown > 0) {
                text.append(", ");
            }
            text.append(entry.block()).append('@').append(entry.pos());
            shown++;
        }
        return text.toString();
    }

    private static boolean matchesState(BlockState current, BlockState wanted, Map<String, String> specified) {
        for (String name : specified.keySet()) {
            Property<?> property = current.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !current.getValue(property).equals(wanted.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    private void repairState(BlockCoord pos, BlockState wanted) {
        BlockPos target = toPos(pos);
        level.setBlock(target, wanted, Block.UPDATE_ALL);
        if (!wanted.hasProperty(BlockStateProperties.BED_PART)) {
            BlockState updated = Block.updateFromNeighbourShapes(wanted, level, target);
            if (!updated.equals(wanted)) {
                level.setBlock(target, updated, Block.UPDATE_ALL);
            }
        }
    }

    private void refreshWork() {
        if (squad.slotOf(spirit.getUUID()) < 0) {
            squad.join(spirit.getUUID());
        }
        if (squad.version() == squadVersion) {
            return;
        }
        squadVersion = squad.version();
        List<Schematic.PlacedEntry> own = CellPartition.split(entries, squad.size()).get(squad.slotOf(spirit.getUUID()));
        Set<Schematic.PlacedEntry> ownSet = new LinkedHashSet<>(own);
        List<Schematic.PlacedEntry> all = new ArrayList<>(own);
        for (Schematic.PlacedEntry entry : entries) {
            if (!ownSet.contains(entry)) {
                all.add(entry);
            }
        }
        work = List.copyOf(all);
        cursor = 0;
        resetEntry();
    }

    private void leaveSquad() {
        BuildSquadRegistry.leave(level, buildKey, spirit.getUUID());
    }

    private void targetTick(Schematic.PlacedEntry entry, BlockState wanted) {
        if (!approach.isActive() && SpiritReach.reachable(spiritCoord(), entry.pos())) {
            if (place(entry, wanted) == PlaceOutcome.DONE) {
                resetEntry();
                return;
            }
        }
        BlockState current = level.getBlockState(toPos(entry.pos()));
        if (!current.isAir() && !current.canBeReplaced()) {
            lastRefusal = "клетка " + entry.pos() + " занята блоком " + BlockStates.idOf(current) + ": ставить некуда";
            skipEntry(entry);
            return;
        }
        if (!approach.isActive()) {
            if (stands.isEmpty()) {
                stands = standCandidates(entry.pos());
                standIndex = 0;
            }
            if (standIndex >= stands.size()) {
                if (stands.isEmpty()) {
                    lastRefusal = "нет стоянок у " + entry.pos() + "; дух " + spiritCoord();
                }
                skipEntry(entry);
                return;
            }
            approachTo(stands.get(standIndex));
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                if (place(entry, wanted) == PlaceOutcome.RETRY) {
                    approach.cancel();
                    standIndex++;
                    if (standIndex >= stands.size()) {
                        skipEntry(entry);
                    }
                } else {
                    resetEntry();
                }
            }
            case STUCK, TIMEOUT -> {
                approach.cancel();
                standIndex++;
                if (standIndex >= stands.size()) {
                    skipEntry(entry);
                }
            }
        }
    }

    private void approachTo(BlockCoord stand) {
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
    }

    private List<BlockCoord> standCandidates(BlockCoord cell) {
        List<BlockCoord> candidates = new ArrayList<>();
        for (BlockCoord stand : StandSpots.of(cell, safety, spiritCoord())) {
            addStand(candidates, stand, cell);
        }
        addStandOnTop(candidates, new BlockCoord(cell.x() + 1, cell.y(), cell.z()), cell);
        addStandOnTop(candidates, new BlockCoord(cell.x() - 1, cell.y(), cell.z()), cell);
        addStandOnTop(candidates, new BlockCoord(cell.x(), cell.y(), cell.z() + 1), cell);
        addStandOnTop(candidates, new BlockCoord(cell.x(), cell.y(), cell.z() - 1), cell);
        for (int dx = -SpiritReach.HORIZONTAL; dx <= SpiritReach.HORIZONTAL; dx++) {
            for (int dz = -SpiritReach.HORIZONTAL; dz <= SpiritReach.HORIZONTAL; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                addColumnBelow(candidates, cell, dx, dz);
            }
        }
        BlockCoord from = spiritCoord();
        candidates.sort(Comparator.comparingLong(stand -> distanceSquared(from, stand)));
        return List.copyOf(candidates);
    }

    private void addColumnBelow(List<BlockCoord> candidates, BlockCoord cell, int dx, int dz) {
        BlockCoord column = new BlockCoord(cell.x() + dx, cell.y(), cell.z() + dz);
        if (!level.getBlockState(toPos(column)).isAir()) {
            return;
        }
        for (int depth = 0; depth <= 6; depth++) {
            BlockCoord stand = new BlockCoord(column.x(), column.y() - depth, column.z());
            if (MiningSafety.canStand(stand, safety)) {
                addStand(candidates, stand, cell);
                return;
            }
        }
    }

    private void addStandOnTop(List<BlockCoord> candidates, BlockCoord neighbour, BlockCoord cell) {
        if (level.getBlockState(toPos(neighbour)).isAir()) {
            return;
        }
        addStand(candidates, new BlockCoord(neighbour.x(), neighbour.y() + 1, neighbour.z()), cell);
    }

    private void addStand(List<BlockCoord> candidates, BlockCoord stand, BlockCoord cell) {
        if (SpiritReach.forbiddenStand(stand, cell)) {
            return;
        }
        if (!MiningSafety.canStand(stand, safety) || !SpiritReach.reachable(stand, cell)
                || candidates.contains(stand)) {
            return;
        }
        candidates.add(stand);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private void skipEntry(Schematic.PlacedEntry entry) {
        skipped++;
        release(entry.pos());
        resetEntry();
        cursor++;
    }

    private void resetEntry() {
        approach.cancel();
        stands = List.of();
        standIndex = 0;
    }

    private PlaceOutcome place(Schematic.PlacedEntry entry, BlockState wanted) {
        BlockPlacer.Begin begin = placer.begin(toPos(entry.pos()), wanted, entry.block());
        release(entry.pos());
        if (begin instanceof BlockPlacer.Started started) {
            if (!entry.text().isBlank()) {
                SignTexts.write(level, started.pos(), entry.text());
            }
            placed++;
            tally.placed(entry.block());
            cursor++;
            return PlaceOutcome.DONE;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            lastRefusal = "запрещено: " + single.reason();
            protectedSkipped++;
            skipped++;
            cursor++;
            if (placed == 0 && cursor >= entries.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + entries.size());
            }
            return PlaceOutcome.DONE;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        lastRefusal = refused.reason() + ": " + refused.message();
        if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
            failed++;
            tally.failed();
            cursor++;
            int have = placed + BlockItems.countIn(spirit.getInventory(), entry.block());
            tally.supplies(have, Math.max(countNeeded(entry.block()) - BlockItems.countIn(spirit.getInventory(), entry.block()), 0));
            fail("не хватило материала '" + entry.block() + "': поставлено " + placed + " из " + entries.size()
                    + "; нужно " + countNeeded(entry.block()) + ", есть " + BlockItems.countIn(spirit.getInventory(), entry.block()));
            return PlaceOutcome.DONE;
        }
        return PlaceOutcome.RETRY;
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
        leaveSquad();
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
        if (cell != null) {
            reservations.release(cell, spirit.getUUID());
        }
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
