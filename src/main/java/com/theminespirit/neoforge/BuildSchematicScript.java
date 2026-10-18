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
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class BuildSchematicScript implements ScriptInstance {
    private enum PlaceOutcome {
        DONE,
        RETRY
    }

    private enum ScaffoldPhase {
        NONE,
        APPROACH,
        USE
    }

    // Дух обходит постройку по площадке: запас демо-раскладки (5) позволяет встать на землю сбоку от
    // дальнего угла крыши, куда ортогональный сосед не дотягивается.
    private static final int STAND_RING_RADIUS = 3;

    private static final String[] SCAFFOLD_MATERIALS = {
            "minecraft:cobblestone", "minecraft:oak_planks", "minecraft:dirt", "minecraft:stone"
    };

    private final SpiritEntity spirit;
    private final Level level;
    private final String file;
    private final BlockCoord origin;
    private final BuildRotation rotation;
    private final List<Schematic.PlacedEntry> entries;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;
    private final BuildKey buildKey;
    private final BuildSquad squad;
    private final Set<BlockCoord> scaffolds = new LinkedHashSet<>();

    private List<Schematic.PlacedEntry> work = List.of();
    private long squadVersion = -1;
    private int cursor;
    private int placed;
    private int skipped;
    private int failed;
    private int protectedSkipped;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private final BuildResult tally = new BuildResult();

    private List<BlockCoord> stands = List.of();
    private int standIndex;
    private ScaffoldPhase scaffoldPhase = ScaffoldPhase.NONE;
    private BlockCoord scaffold;
    private String scaffoldBlock;

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.file = params.file();
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
        BlockState state = BlockStates.stateFor(entry.block());
        return rotation == BuildRotation.NONE ? state : state.rotate(level, toPos(entry.pos()), mcRotation());
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
            if (current.is(wanted.getBlock())) {
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
            if (scaffoldPhase == ScaffoldPhase.APPROACH) {
                scaffoldApproachTick(entry, wanted);
            } else if (scaffoldPhase == ScaffoldPhase.USE) {
                scaffoldUseTick(entry, wanted);
            } else {
                targetTick(entry, wanted);
            }
            return ScriptStatus.RUNNING;
        }
        leaveSquad();
        reservations.releaseAll(spirit.getUUID());
        removeScaffolds();
        if (failed > 0) {
            return outcome;
        }
        if (placed == 0 && skipped >= entries.size() && entries.size() > 0) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        tally.supplies(placed, 0);
        outcome = ScriptStatus.SUCCEEDED;
        return outcome;
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
        if (!approach.isActive()) {
            if (stands.isEmpty()) {
                stands = standCandidates(entry.pos());
                standIndex = 0;
            }
            if (standIndex >= stands.size()) {
                beginScaffold(entry);
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
                        beginScaffold(entry);
                    }
                } else {
                    resetEntry();
                }
            }
            case STUCK, TIMEOUT -> {
                approach.cancel();
                standIndex++;
                if (standIndex >= stands.size()) {
                    beginScaffold(entry);
                }
            }
        }
    }

    private void beginScaffold(Schematic.PlacedEntry entry) {
        if (scaffoldPhase != ScaffoldPhase.NONE) {
            skipEntry(entry);
            return;
        }
        BlockCoord below = below(entry.pos());
        String material = scaffoldMaterial();
        if (material == null || scaffolds.contains(below) || !canBeScaffold(below)) {
            skipEntry(entry);
            return;
        }
        scaffold = below;
        scaffoldBlock = material;
        scaffoldPhase = ScaffoldPhase.APPROACH;
        stands = standCandidates(below);
        standIndex = 0;
        if (stands.isEmpty()) {
            skipEntry(entry);
            return;
        }
        approachTo(stands.get(0));
    }

    private void scaffoldApproachTick(Schematic.PlacedEntry entry, BlockState wanted) {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                BlockPlacer.Begin begin = placer.begin(toPos(scaffold), BlockStates.stateFor(scaffoldBlock), scaffoldBlock);
                if (begin instanceof BlockPlacer.Started) {
                    scaffolds.add(scaffold);
                    scaffoldPhase = ScaffoldPhase.USE;
                    approachTo(scaffold);
                } else {
                    skipEntry(entry);
                }
            }
            case STUCK, TIMEOUT -> skipEntry(entry);
        }
    }

    private void scaffoldUseTick(Schematic.PlacedEntry entry, BlockState wanted) {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                place(entry, wanted);
                resetEntry();
            }
            case STUCK, TIMEOUT -> skipEntry(entry);
        }
    }

    private void approachTo(BlockCoord stand) {
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
    }

    private List<BlockCoord> standCandidates(BlockCoord cell) {
        List<BlockCoord> candidates = new ArrayList<>(StandSpots.of(cell, safety, spiritCoord()));
        BlockCoord below = below(cell);
        if (!candidates.contains(below) && MiningSafety.canStand(below, safety)) {
            candidates.add(below);
        }
        addStandOnTop(candidates, new BlockCoord(cell.x() + 1, cell.y(), cell.z()));
        addStandOnTop(candidates, new BlockCoord(cell.x() - 1, cell.y(), cell.z()));
        addStandOnTop(candidates, new BlockCoord(cell.x(), cell.y(), cell.z() + 1));
        addStandOnTop(candidates, new BlockCoord(cell.x(), cell.y(), cell.z() - 1));
        addDeepBelow(candidates, cell);
        for (int radius = 1; radius <= STAND_RING_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    addColumnBelow(candidates, cell, dx, dz);
                }
            }
        }
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
                if (reachable(cell, stand) && !candidates.contains(stand)) {
                    candidates.add(stand);
                }
                return;
            }
        }
    }

    private void addDeepBelow(List<BlockCoord> candidates, BlockCoord cell) {
        for (int depth = 1; depth <= 6; depth++) {
            BlockCoord stand = new BlockCoord(cell.x(), cell.y() - depth, cell.z());
            if (!MiningSafety.canStand(stand, safety)) {
                continue;
            }
            if (reachable(cell, stand) && !candidates.contains(stand)) {
                candidates.add(stand);
            }
        }
    }

    private boolean reachable(BlockCoord cell, BlockCoord stand) {
        double dx = stand.x() + 0.5 - (cell.x() + 0.5);
        double dy = stand.y() + 1.6 - (cell.y() + 0.5);
        double dz = stand.z() + 0.5 - (cell.z() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= BlockBreaker.REACH;
    }

    private void addStandOnTop(List<BlockCoord> candidates, BlockCoord neighbour) {
        if (level.getBlockState(toPos(neighbour)).isAir()) {
            return;
        }
        BlockCoord top = new BlockCoord(neighbour.x(), neighbour.y() + 1, neighbour.z());
        if (!candidates.contains(top) && MiningSafety.canStand(top, safety)) {
            candidates.add(top);
        }
    }

    private String scaffoldMaterial() {
        for (String candidate : SCAFFOLD_MATERIALS) {
            if (BlockItems.countIn(spirit.getInventory(), candidate) > 0) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canBeScaffold(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        BlockPos floor = toPos(below(cell));
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private void skipEntry(Schematic.PlacedEntry entry) {
        skipped++;
        tally.skipped();
        release(entry.pos());
        resetEntry();
        cursor++;
    }

    private void resetEntry() {
        approach.cancel();
        stands = List.of();
        standIndex = 0;
        scaffoldPhase = ScaffoldPhase.NONE;
        scaffold = null;
        scaffoldBlock = null;
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
            protectedSkipped++;
            skipped++;
            tally.skipped();
            cursor++;
            if (placed == 0 && cursor >= entries.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + entries.size());
            }
            return PlaceOutcome.DONE;
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

    private void removeScaffolds() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        PlacedBlocksData data = PlacedBlocksData.get(server);
        for (BlockCoord cell : scaffolds) {
            BlockState current = level.getBlockState(toPos(cell));
            if (current.is(Blocks.COBBLESTONE) || current.is(Blocks.OAK_PLANKS) || current.is(Blocks.DIRT)
                    || current.is(Blocks.STONE)) {
                level.setBlock(toPos(cell), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            data.unmarkSpirit(toPos(cell));
        }
        scaffolds.clear();
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
        removeScaffolds();
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
