package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildOrder;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class FillSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STAND_LOGGED = 4;
    private static final int MAX_DEFERRALS = 2;
    private static final int LONG_STATE_TICKS = 200;

    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        EXIT,
        PICK,
        APPROACH,
        PILLAR,
        DONE
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final BlockState wanted;
    private final Set<BlockCoord> region;
    private int stateTicks;
    private @Nullable State lastState;
    private @Nullable BlockCoord currentStand;
    private final Map<BlockCoord, Integer> deferred = new HashMap<>();
    private final List<BlockCoord> order;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private final Set<BlockCoord> spiritCells;
    private boolean noExit;

    private State state = State.PICK;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int cursor;
    private BlockCoord current;
    private int placed;
    private int prebuilt;
    private int skipped;
    private int failed;
    private int noStand;
    private int protectedSkipped;
    private int foreignSkipped;
    private int blockReads;

    private final BuildResult tally = new BuildResult();
    private @Nullable PillarColumn pillar;

    public FillSession(SpiritEntity spirit, String blockId, BuildBox box) {
        this(spirit, blockId, box.cellsBottomUp());
    }

    public FillSession(SpiritEntity spirit, String blockId, List<BlockCoord> cells) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.blockId = blockId;
        this.wanted = BlockStates.stateFor(blockId);
        this.region = Set.copyOf(cells);
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        Set<BlockCoord> occupied = spiritCells();
        this.spiritCells = Set.copyOf(occupied);
        this.order = new ArrayList<>(BuildOrder.placement(List.copyOf(cells), occupied));
        // Дух в яме, которую засыпает, не выходит из неё (выход уводил его на край, откуда дно не достать, INC-66):
        // засыпает вокруг себя слой за слоем, а свою клетку — под себя столбом.
        if (insideBox(spirit.blockPosition()) && !occupied.isEmpty() && !onFloor()) {
            state = State.EXIT;
        }
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки");
        }
        stateTicks = state == lastState ? stateTicks + 1 : 0;
        lastState = state;
        if (stateTicks > 0 && stateTicks % LONG_STATE_TICKS == 0) {
            // Долго в одном состоянии — в лог, где и с чем: подсыпка, стоящая сотни тиков в пруду, без строки не
            // разобрать (сцена 56).
            LOGGER.info(
                    "[fill] {}: {} тиков в {} у {} (стоянка {}, дух в {}, в воде {})",
                    spirit.getName().getString(),
                    stateTicks,
                    state,
                    current,
                    currentStand,
                    spirit.blockPosition().toShortString(),
                    spirit.isInWater());
        }
        switch (state) {
            case EXIT -> exitTick();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case PILLAR -> pillarTick();
            case DONE -> finish();
        }
        return outcome;
    }

    public void cancel() {
        approach.cancel();
        releaseAll();
    }

    public void suspend() {
        approach.suspend();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        int total = order.size();
        return new ScriptInstance.Progress(Math.min(cursor, total), Math.max(total, 1));
    }

    public int placed() {
        return placed;
    }

    public int skipped() {
        return skipped;
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, Object> resultData() {
        return tally.toData();
    }

    public int blockReads() {
        return blockReads;
    }

    public Map<String, Object> saveState() {
        return new BuildState(placed, 0, skipped, failed, outcome == Outcome.RUNNING).toMap();
    }

    public void restoreState(Map<String, Object> saved) {
        BuildState state = BuildState.fromMap(saved);
        placed = Math.max(placed, state.placed());
        skipped = Math.max(skipped, state.skipped());
        failed = Math.max(failed, state.failed());
        for (int i = 0; i < state.placed(); i++) {
            tally.placed(blockId);
        }
        for (int i = 0; i < state.skipped(); i++) {
            tally.skipped();
        }
        for (int i = 0; i < state.failed(); i++) {
            tally.failed();
        }
    }

    private void exitTick() {
        BlockCoord exit = exitStand();
        if (exit == null) {
            noExit = true;
            state = State.PICK;
            return;
        }
        if (!approach.isActive()) {
            approach.begin(new Vec3(exit.x() + 0.5, exit.y(), exit.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> state = State.PICK;
            case STUCK, TIMEOUT -> state = State.PICK;
        }
    }

    private void pick() {
        while (cursor < order.size()) {
            BlockCoord cell = order.get(cursor);
            if (noExit && spiritCells.contains(cell)) {
                skipped++;
                tally.skipped();
                cursor++;
                continue;
            }
            BlockPos pos = toPos(cell);
            blockReads++;
            BlockState current = level.getBlockState(pos);
            if (current.is(wanted.getBlock())) {
                prebuilt++;
                cursor++;
                continue;
            }
            if (reservations.foreign(cell, spirit.getUUID())) {
                foreignSkipped++;
                skipped++;
                tally.skipped();
                cursor++;
                continue;
            }
            reservations.reserve(cell, spirit.getUUID());
            this.current = cell;
            if (deferOwnWater(cell)) {
                continue;
            }
            if (cell.equals(spiritCoord())) {
                // Своя клетка — под себя: прыжок и блок под ноги, дух поднимается на слой (INC-66).
                pillar = pillarHere(cell);
                state = State.PILLAR;
                return;
            }
            if (inReach(cell)) {
                // Клетка в досягаемости — ставится с места, без подхода к соседней стоянке: подход к каждой клетке
                // ямы съедал большую часть подсыпки (сцена 56).
                placeCurrent();
                return;
            }
            List<BlockCoord> stands = StandSpots.of(cell, safetyView(), spiritCoord());
            if (stands.isEmpty() && floorUnder(cell) && allowedStand(cell)) {
                // Дно ямы в блок шириной: с края до него не дотянуться, соседей-стоянок нет — дух спускается в саму
                // клетку и засыпает столбом под себя (сцена 56: яма 1×1×3 оставалась, выравнивание шло по кругу).
                stands = List.of(cell);
            }

            if (stands.isEmpty()) {
                if (noStand < NO_STAND_LOGGED) {
                    BlockPos at = toPos(cell);
                    LOGGER.info(
                            "[fill] {}: нет стоянки у {}: под ней {}, в ней {}, над ней {}, верх без листвы y={}",
                            spirit.getName().getString(),
                            cell,
                            level.getBlockState(at.below()).getBlock(),
                            level.getBlockState(at).getBlock(),
                            level.getBlockState(at.above()).getBlock(),
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z()));
                }
                noStand++;
                skipped++;
                tally.skipped();
                release(cell);
                cursor++;
                continue;
            }
            stands = stands.stream().filter(this::allowedStand).toList();
            if (stands.isEmpty()) {
                noStand++;
                skipped++;
                tally.skipped();
                release(cell);
                cursor++;
                continue;
            }
            BlockCoord stand = stands.get(0);
            currentStand = stand;
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            state = State.APPROACH;
            return;
        }
        state = State.DONE;
        finish();
    }

    private void pillarTick() {
        switch (pillar.tick()) {
            case RUNNING -> {}
            case BUILT -> {
                placed++;
                tally.placed(blockId);
                releaseAndAdvance();
                state = State.PICK;
            }
            case FAILED -> {
                LOGGER.info(
                        "[fill] {}: столб в {} не вышел: {}",
                        spirit.getName().getString(),
                        current,
                        pillar.failureReason());
                skipped++;
                tally.skipped();
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    // Над стоянкой до верха колонки — только воздух, вода и листва: пруд в низине и яма под кроной — не пещера.
    private boolean openAbove(BlockCoord stand) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stand.x(), stand.z());
        for (int y = stand.y() + 1; y < top; y++) {
            BlockPos pos = new BlockPos(stand.x(), y, stand.z());
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && !(state.getBlock() instanceof LeavesBlock)) {
                return false;
            }
        }
        return true;
    }

    // Своя клетка в воде пруда — после соседних: из воды дух ставит их с места, потом выходит на них и засыпает свою
    // с соседней стоянки (столб в воде не выходит, дух всплывает, сцена 56).
    private boolean deferOwnWater(BlockCoord cell) {
        if (!cell.equals(spiritCoord()) || !floatingIn(cell) || deferred.merge(cell, 1, Integer::sum) > MAX_DEFERRALS) {
            return false;
        }
        order.add(cell);
        release(cell);
        cursor++;
        return true;
    }

    private boolean floatingIn(BlockCoord cell) {
        return level.getBlockState(toPos(cell)).getFluidState().is(FluidTags.WATER);
    }

    private PillarColumn pillarHere(BlockCoord cell) {
        return level.getBlockState(toPos(cell)).getFluidState().is(FluidTags.WATER)
                ? PillarColumn.throughWater(spirit, blockId)
                : new PillarColumn(spirit, blockId, 1);
    }

    private boolean inReach(BlockCoord cell) {
        BlockPos feet = spirit.blockPosition();
        BlockPos pos = toPos(cell);
        return (spirit.onGround() || spirit.isInWater())
                && !pos.equals(feet)
                && !pos.equals(feet.above())
                && SpiritReach.reachable(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), cell);
    }

    // В клетке можно стоять: твёрдый пол под ней, она и клетка над ней свободны.
    private boolean floorUnder(BlockCoord cell) {
        BlockPos pos = toPos(cell);
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above())
                        .getCollisionShape(level, pos.above())
                        .isEmpty();
    }

    // Под ногами духа твёрдый пол: столбом под себя он поднимется, выходить из ямы не нужно.
    private boolean onFloor() {
        BlockPos below = spirit.blockPosition().below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // На участке поселения подсыпка не встаёт в пещеру без неба (INC-72: засыпка пустот под участком уводила духа в
    // карман (-165,64,-489) в 5-й раз): закрытую пустоту не засыпать, а не лезть в неё. Шахта и ямы с небом — можно.
    private boolean allowedStand(BlockCoord stand) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        // Небо — без листвы: яма под кроной дерева — не пещера (сцена 56: яма 1×1 под листвой не засыпалась).
        boolean sky = openAbove(stand);
        return SettlementPlotStore.get(server)
                .plot()
                .map(plot -> SettlementLayout.fillStandAllowed(plot.anchor(), stand, sky))
                .orElse(true);
    }

    private void approachTick() {
        BlockPos feet = spirit.blockPosition();
        if (currentStand != null
                && feet.getX() == currentStand.x()
                && feet.getZ() == currentStand.z()
                && Math.abs(feet.getY() - currentStand.y()) <= 1
                && (spirit.onGround() || spirit.isInWater())) {
            // Уже в клетке стоянки (в воде пруда дух всплывает на блок выше неё): подход к центру с допуском не
            // заканчивался до тайм-аута, и дух сотни тиков стоял в пруду (сцена 56).
            approach.cancel();
            placeCurrent();
            return;
        }
        if (current != null && inReach(current)) {
            // По дороге к стоянке клетка оказалась в досягаемости — ставится с места: подход к стоянке в соседней
            // клетке воды не заканчивался, и дух сотни тиков плавал в пруду (сцена 56).
            approach.cancel();
            placeCurrent();
            return;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> placeCurrent();
            case STUCK, TIMEOUT -> {
                noStand++;
                skipped++;
                tally.skipped();
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void placeCurrent() {
        if (current.equals(spiritCoord())) {
            pillar = pillarHere(current);
            state = State.PILLAR;
            return;
        }
        BlockPos pos = toPos(current);
        BlockPlacer.Begin begin = placer.begin(pos, wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            tally.placed(blockId);
            releaseAndAdvance();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            protectedSkipped++;
            skipped++;
            tally.skipped();
            releaseAndAdvance();
            state = State.PICK;
            if (placed == 0 && cursor >= order.size()) {
                fail("строить запрещено: " + single.reason() + "; поставлено 0 из " + order.size());
            }
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        switch (refused.reason()) {
            case NO_MATERIAL -> {
                failed++;
                tally.failed();
                releaseAndAdvance();
                int have = placed + BlockItems.countIn(spirit.getInventory(), blockId);
                tally.supplies(have, Math.max(order.size() - prebuilt - have, 0));
                fail("не хватило материала '" + blockId + "': нужно " + order.size() + ", есть " + have
                        + "; поставлено " + placed + " из " + order.size());
            }
            case OCCUPIED, NO_SUPPORT -> {
                if (deferred.merge(current, 1, Integer::sum) <= MAX_DEFERRALS) {
                    // Клетку занял дух соседней части или под ней ещё не засыпано: в конец очереди, а не в пропуск —
                    // пропуск оставлял яму на следующий круг выравнивания (сцена 56).
                    order.add(current);
                    releaseAndAdvance();
                    state = State.PICK;
                    return;
                }
                if (skipped < NO_STAND_LOGGED) {
                    LOGGER.info(
                            "[fill] {}: {} не поставить: {} (дух в {})",
                            spirit.getName().getString(),
                            current,
                            refused.message(),
                            spirit.blockPosition().toShortString());
                }
                skipped++;
                tally.skipped();
                releaseAndAdvance();
                state = State.PICK;
            }
            case CHUNK_NOT_LOADED -> {
                if (skipped < NO_STAND_LOGGED) {
                    LOGGER.info(
                            "[fill] {}: {} не поставить: {} (дух в {})",
                            spirit.getName().getString(),
                            current,
                            refused.message(),
                            spirit.blockPosition().toShortString());
                }
                skipped++;
                tally.skipped();
                releaseAndAdvance();
                state = State.PICK;
            }
            case OUT_OF_REACH -> {
                noStand++;
                skipped++;
                tally.skipped();
                releaseAndAdvance();
                state = State.PICK;
            }
        }
    }

    private void finish() {
        if (outcome != Outcome.RUNNING) {
            return;
        }
        releaseAll();
        if (failed > 0) {
            return;
        }
        if (placed == 0) {
            fail(summary() + "; ни один блок не поставлен");
            return;
        }
        tally.supplies(placed, 0);
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        // Провал подсыпки — в лог с клетками: подсыпка внутри fallback не валит задание, и без строки повтор по кругу
        // «подсыпка 3 блоков» не разобрать (сцена 56).
        LOGGER.info(
                "[fill] {}: {} клеток {} от {} не засыпаны: {}",
                spirit.getName().getString(),
                blockId,
                region.size(),
                region.stream().min(Comparator.naturalOrder()).orElse(null),
                reason);
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }

    private String summary() {
        return "поставлено " + placed + " из " + order.size() + "; пропущено: " + skipped + " (без стоянки: " + noStand
                + ", защищено: " + protectedSkipped + ", занято другим духом: " + foreignSkipped + ")";
    }

    private Set<BlockCoord> spiritCells() {
        BlockPos feet = spirit.blockPosition();
        Set<BlockCoord> cells = new HashSet<>();
        BlockCoord legs = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        BlockCoord head = new BlockCoord(feet.getX(), feet.getY() + 1, feet.getZ());
        if (region.contains(legs)) {
            cells.add(legs);
        }
        if (region.contains(head)) {
            cells.add(head);
        }
        return cells;
    }

    private boolean insideBox(BlockPos pos) {
        return region.contains(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    private BlockCoord exitStand() {
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<BlockCoord> candidates = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
                    if (region.contains(cell)) {
                        continue;
                    }
                    if (MiningSafety.canStand(cell, safetyView())) {
                        candidates.add(cell);
                    }
                }
            }
        }
        candidates.sort((first, second) -> {
            long firstDistance = distanceSquared(from, first);
            long secondDistance = distanceSquared(from, second);
            return Long.compare(firstDistance, secondDistance);
        });
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private SafetyView safetyView() {
        return safety;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private void release(BlockCoord cell) {
        reservations.release(cell, spirit.getUUID());
    }

    private void releaseAndAdvance() {
        if (current != null) {
            release(current);
            current = null;
        }
        cursor++;
    }

    private void releaseAll() {
        reservations.releaseAll(spirit.getUUID());
        current = null;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
