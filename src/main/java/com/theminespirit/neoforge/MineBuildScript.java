package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MineCellPlan;
import com.theminespirit.core.mining.MineCellStands;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MineDetour;
import com.theminespirit.core.mining.MineDigOrder;
import com.theminespirit.core.mining.MineFloor;
import com.theminespirit.core.mining.MineFluids;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.mining.MinePlan;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineBuildScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SETTLE_TICKS = 5;
    private static final int MAX_ACTIONS_PER_COLUMN = 64;
    private static final List<String> FLOOR_MATERIALS =
            List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:stone", "minecraft:dirt");

    private enum Phase {
        PICK,
        WALK,
        DIG,
        AIM,
        SETTLE,
        TORCH,
        EXIT,
        DONE
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final SafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final Approach approach;
    private final MineCellAim aim;
    private final MineFinish finish;
    private final List<MineColumn> plan;
    private final Set<MineColumn> open = new HashSet<>();
    private final Set<MineColumn> closed = new HashSet<>();
    private final Set<BlockCoord> openCells = new HashSet<>();
    private final Set<BlockCoord> leftOres = new HashSet<>();
    private final Map<MineColumn, MineColumn> parents = new HashMap<>();
    private final List<Schematic.PlacedEntry> torches;
    private final MineCells cells;
    private final MineEntranceOpening entranceOpening;

    private Phase phase = Phase.PICK;
    private @Nullable MineColumn current;
    private final MineStepWalker stepWalker;
    private boolean breaking;
    private boolean dugHere;
    private int actions;
    private int settle;
    private int dug;
    private int plugs;
    private int floors;
    private int detours;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private @Nullable String failureReason;

    MineBuildScript(SpiritEntity spirit, MineLayout layout, BuildRotation rotation) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.aim = new MineCellAim(spirit);
        this.stepWalker = new MineStepWalker(approach, safety);
        this.plan = new ArrayList<>(MinePlan.order(MinePlan.columns(layout.floors(), layout.air()), layout.entrance()));
        this.torches = new ArrayList<>(layout.optional());
        Set<BlockCoord> walls = new HashSet<>(layout.floors());
        plan.forEach(column -> walls.remove(column.floor()));
        this.cells = new MineCells(level, walls);
        this.finish = new MineFinish(spirit, placer, approach, rotation, torches, parents, open);
        this.entranceOpening = MineEntranceOpening.of(plan, layout, safety);
        if (plan.isEmpty()) {
            LOGGER.info(
                    "[mine] {}: вход {} уже на глубине цели спуска — 0 ступеней, уровень считается достигнутым",
                    spirit.displayName(),
                    layout.entrance());
        }
    }

    static MineBuildScript of(
            SpiritEntity spirit, Preset preset, BlockCoord origin, BuildRotation rotation, MineLayout.Extent extent) {
        Schematic schematic = preset.schematic();
        return new MineBuildScript(
                spirit, MineLayout.place(schematic, preset.entry(), origin, rotation, extent), rotation);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время проходки шахты");
        }
        if (breaking) {
            breakTick();
            return outcome;
        }
        switch (phase) {
            case PICK -> pick();
            case WALK -> walk();
            case DIG -> dig();
            case AIM -> aimTick();
            case SETTLE -> settle();
            case TORCH -> torch();
            case EXIT -> exit();
            case DONE -> outcome = ScriptStatus.SUCCEEDED;
        }
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(open.size(), Math.max(1, plan.size() - closed.size())));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        return Optional.of(new ScriptResult.Data(Map.of(
                "columns", open.size(),
                "blocks_dug", dug,
                "floors_placed", floors,
                "plugs", plugs,
                "ores_left", leftOres.size(),
                "detours", detours)));
    }

    @Override
    public void cancel() {
        breaker.cancel();
        approach.cancel();
        aim.reset();
        stepWalker.reset();
    }

    @Override
    public void suspend() {
        approach.suspend();
        aim.reset();
        stepWalker.reset();
    }

    @Override
    public @Nullable String failureReason() {
        return failureReason;
    }

    private void pick() {
        Optional<MineColumn> next = MinePlan.next(plan, open, closed);
        if (next.isEmpty()) {
            List<MineColumn> unreached = plan.stream()
                    .filter(column -> !open.contains(column) && !closed.contains(column))
                    .toList();
            if (!unreached.isEmpty()) {
                fail("до " + unreached.size() + " колонок шахты не дойти пешком (первая "
                        + unreached.get(0).floor() + "): обход оставленной руды не соединил ход");
                return;
            }
            phase = Phase.TORCH;
            return;
        }
        current = next.get();
        actions = 0;
        dugHere = false;
        phase = Phase.WALK;
    }

    private void walk() {
        MineColumn column = current();
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        if (entranceOpening.waiting(
                column, feet, cells::open, cell -> startBreak(cell, OptionalInt.empty()), cells::removeWalls)) {
            return;
        }
        Optional<MineColumn> parent = MinePlan.parent(column, open);
        MineStepWalker.State state =
                stepWalker.tick(column, parent.map(MineColumn::stand).orElse(null), feet);
        if (state == MineStepWalker.State.ARRIVED || state == MineStepWalker.State.VISIBLE) {
            parent.ifPresentOrElse(found -> parents.put(column, found), () -> parents.remove(column));
            phase = Phase.DIG;
        } else if (state == MineStepWalker.State.FAILED) {
            fail("к ступени " + column.stand() + " не подойти: " + stepWalker.refusal());
        }
    }

    private void dig() {
        MineColumn column = current();
        if (actions++ > MAX_ACTIONS_PER_COLUMN) {
            fail("колонка " + column.floor() + " не открывается за " + MAX_ACTIONS_PER_COLUMN
                    + " действий: сверху сыплется гравий или песок, проход не держится");
            return;
        }
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        for (BlockCoord cell : MineDigOrder.column(column, cells.wallsOf(column), feet, safety)) {
            boolean ready = cell.equals(column.floor())
                    ? solidReady(cell, "пол")
                    : column.contains(cell) ? cellReady(column, cell) : solidReady(cell, "стенка");
            if (!ready) {
                return;
            }
        }
        settle = dugHere ? SETTLE_TICKS : 0;
        phase = Phase.SETTLE;
    }

    private void settle() {
        if (settle-- > 0) {
            return;
        }
        MineColumn column = current();
        BlockCoord floor = column.floor();
        AABB box = new AABB(
                floor.x(), floor.y() + 1, floor.z(), floor.x() + 1, floor.y() + column.height() + 2, floor.z() + 1);
        boolean falling =
                !level.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty();
        if (falling || column.cells().stream().anyMatch(cell -> !cells.open(cell))) {
            phase = Phase.DIG;
            return;
        }
        open.add(column);
        openCells.addAll(column.cells());
        phase = Phase.PICK;
    }

    private boolean solidReady(BlockCoord floor, String what) {
        if (leftOres.contains(floor)) {
            return true;
        }
        BlockPos pos = toPos(floor);
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        MineFloor.Action action = MineFloor.decide(new MineFloor.Seen(
                BlockStates.idOf(state),
                state.isAir(),
                !state.getFluidState().isEmpty(),
                state.canBeReplaced(),
                below.isFaceSturdy(level, pos.below(), Direction.UP)));
        return switch (action) {
            case KEEP -> true;
            case PLACE -> {
                if (place(floor, what)) {
                    floors++;
                }
                yield false;
            }
            case REPLACE -> {
                startBreak(floor, OptionalInt.empty());
                yield false;
            }
            case ORE -> oreInFloor(floor, state);
        };
    }

    private boolean oreInFloor(BlockCoord floor, BlockState state) {
        MineOres.Decision decision = MineOres.decide(BlockStates.idOf(state), picks(state));
        if (decision instanceof MineOres.Mine mine) {
            startBreak(floor, mine.slot());
            return false;
        }
        remember(floor, state, ((MineOres.Leave) decision).reason());
        return true;
    }

    private boolean cellReady(MineColumn column, BlockCoord cell) {
        if (cells.open(cell)) {
            return true;
        }
        List<BlockCoord> plugsNeeded = MineFluids.plugsBefore(cell, openCells, cells::fluid);
        if (!plugsNeeded.isEmpty()) {
            if (place(plugsNeeded.get(0), "пробка")) {
                plugs++;
            }
            return false;
        }
        BlockState state = level.getBlockState(toPos(cell));
        MineOres.Decision decision = MineOres.decide(BlockStates.idOf(state), picks(state));
        if (decision instanceof MineOres.Leave leave) {
            remember(cell, state, leave.reason());
            closeAndDetour(column);
            return false;
        }
        startBreak(cell, ((MineOres.Mine) decision).slot());
        return false;
    }

    private void closeAndDetour(MineColumn column) {
        closed.add(column);
        phase = Phase.PICK;
        MineColumn parent = parents.get(column);
        if (parent == null) {
            fail("руда в колонке входа " + column.floor() + ": обходить не от чего, вход перенести");
            return;
        }
        MineColumn forward = MinePlan.forward(parent, column, plan).orElse(null);
        Optional<List<MineColumn>> detour = MineDetour.around(parent, column, forward, this::undiggable);
        if (detour.isEmpty()) {
            fail("руда в " + column.floor() + " оставлена, но обход справа и слева тоже упирается в руду");
            return;
        }
        int at = plan.indexOf(column) + 1;
        for (MineColumn side : detour.get()) {
            cells.removeWalls(side.cells());
            if (!plan.contains(side)) {
                plan.add(at++, side);
            }
        }
        detours++;
        LOGGER.info(
                "Шахта: дух {} обходит руду в {} колонками {}", spirit.getStringUUID(), column.floor(), detour.get());
    }

    private boolean undiggable(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        if (leftOres.contains(cell) || state.getDestroySpeed(level, toPos(cell)) < 0) {
            return true;
        }
        return MineOres.decide(BlockStates.idOf(state), picks(state)) instanceof MineOres.Leave;
    }

    private void remember(BlockCoord cell, BlockState state, String reason) {
        if (leftOres.add(cell) && level instanceof ServerLevel server) {
            BlockPos feet = spirit.blockPosition();
            MineOreLedger.get(server)
                    .remember(cell, BlockStates.idOf(state), new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
            LOGGER.info(
                    "Шахта: дух {} оставил {} в {}: {}", spirit.getStringUUID(), BlockStates.idOf(state), cell, reason);
        }
    }

    private boolean place(BlockCoord cell, String what) {
        String material = FLOOR_MATERIALS.stream()
                .filter(id -> BlockItems.countIn(spirit.getInventory(), id) > 0)
                .findFirst()
                .orElse(null);
        if (material == null) {
            fail("нет булыжника на " + what + " в " + cell + ": проход без него затопит или дух провалится");
            return false;
        }
        if (MineCellStands.occupies(new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()), cell)) {
            aimAt(cell, new MineCellPlan.Seen(occupantText(cell), true, true, true));
            return false;
        }
        BlockPlacer.Begin begin = placer.beginWithoutSupport(toPos(cell), BlockStates.stateFor(material), material);
        if (begin instanceof BlockPlacer.Started) {
            return true;
        }
        if (begin instanceof BlockPlacer.Refused refused) {
            if (refused.reason() == BlockPlacer.RefuseReason.OCCUPIED) {
                String other = occupantText(cell);
                if (other != null) {
                    aimAt(cell, new MineCellPlan.Seen(other, false, true, true));
                    return false;
                }
            }
            if (refused.reason() == BlockPlacer.RefuseReason.OUT_OF_REACH) {
                aimAt(cell, new MineCellPlan.Seen(occupantText(cell), false, true, false));
                return false;
            }
            fail(what + " в " + cell + " не поставить: " + refused.message()
                    + MineCellAim.context(level, spirit, plan, cell));
            return false;
        }
        fail(what + " в " + cell + " не поставить: " + ((BlockPlacer.Protected) begin).reason()
                + MineCellAim.context(level, spirit, plan, cell));
        return false;
    }

    private void startBreak(BlockCoord cell, OptionalInt slot) {
        BlockPos pos = toPos(cell);
        BlockBreaker.Begin begin = slot.isPresent() ? breaker.beginWithSlot(pos, slot.getAsInt()) : breaker.begin(pos);
        if (begin instanceof BlockBreaker.Started) {
            breaking = true;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH) {
                aimAt(cell, new MineCellPlan.Seen(occupantText(cell), false, true, false));
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.NO_SIGHT) {
                aimAt(cell, new MineCellPlan.Seen(occupantText(cell), false, false, true));
                return;
            }
            fail("клетку шахты " + cell + " не выломать: " + refused.message()
                    + MineCellAim.context(level, spirit, plan, cell));
            return;
        }
        fail("клетку шахты " + cell + " не выломать: " + ((BlockBreaker.Protected) begin).reason()
                + MineCellAim.context(level, spirit, plan, cell));
    }

    private void aimTick() {
        switch (aim.tick()) {
            case RUNNING -> {}
            case RETRY -> phase = Phase.DIG;
            case FAILED -> {
                String reason = aim.failure();
                BlockCoord cell = aim.cell();
                String context = cell == null ? "" : MineCellAim.context(level, spirit, plan, cell);
                fail((reason == null ? "подход к клетке шахты провалился" : reason) + context);
            }
        }
    }

    private void aimAt(BlockCoord cell, MineCellPlan.Seen seen) {
        MineCellPlan.Plan cellPlan = MineCellPlan.of(
                cell,
                new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()),
                safety,
                seen,
                stand -> !aim.tried(stand) && MineCellAim.occupant(level, spirit, stand) == null);
        switch (cellPlan.kind()) {
            case READY -> phase = Phase.DIG;
            case WAIT -> aim.beginWait(cell, cellPlan.reason());
            case APPROACH -> aim.beginApproach(cell, cellPlan.stands(), cellPlan.reason());
            case REFUSE ->
                fail("к клетке шахты " + cell + " не подойти: " + cellPlan.reason()
                        + MineCellAim.context(level, spirit, plan, cell));
        }
    }

    private @Nullable String occupantText(BlockCoord cell) {
        LivingEntity other = MineCellAim.occupant(level, spirit, cell);
        return other == null ? null : MineCellAim.describe(other);
    }

    private void breakTick() {
        BlockBreaker.Progress progress = breaker.tick();
        if (progress == BlockBreaker.Progress.BREAKING) {
            return;
        }
        breaking = false;
        if (progress == BlockBreaker.Progress.BROKEN) {
            dug++;
            dugHere = true;
        }
    }

    private void torch() {
        switch (finish.torchTick()) {
            case RUNNING -> {}
            case DONE -> phase = Phase.EXIT;
            case FAILED -> fail(finish.failure());
        }
    }

    private void exit() {
        switch (finish.exitTick()) {
            case RUNNING -> {}
            case DONE -> phase = Phase.DONE;
            case FAILED -> fail(finish.failure());
        }
    }

    private List<MineOres.Pick> picks(BlockState state) {
        return PickOptions.of(spirit, state);
    }

    private MineColumn current() {
        MineColumn column = current;
        if (column == null) {
            throw new IllegalStateException("Колонка проходки не выбрана: фаза " + phase + " идёт после PICK");
        }
        return column;
    }

    private ScriptStatus fail(String reason) {
        if (outcome == ScriptStatus.RUNNING) {
            failureReason = reason;
            outcome = ScriptStatus.FAILED;
            cancel();
            LOGGER.info("Шахта: дух {} остановил проходку: {}", spirit.getStringUUID(), reason);
        }
        return outcome;
    }

    private static BlockPos toPos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
