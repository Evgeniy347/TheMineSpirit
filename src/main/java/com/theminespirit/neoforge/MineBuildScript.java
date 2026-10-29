package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineBuildScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double STAND_ARRIVAL = 0.6;
    private static final int SETTLE_TICKS = 5;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int MAX_ACTIONS_PER_COLUMN = 64;
    private static final List<String> FLOOR_MATERIALS =
            List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:stone", "minecraft:dirt");

    private enum Phase {
        PICK,
        WALK,
        DIG,
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
    private final List<MineColumn> plan;
    private final Set<MineColumn> open = new HashSet<>();
    private final Set<MineColumn> closed = new HashSet<>();
    private final Set<BlockCoord> openCells = new HashSet<>();
    private final Set<BlockCoord> leftOres = new HashSet<>();
    private final Map<MineColumn, MineColumn> parents = new HashMap<>();
    private final List<MineColumn> exitPath = new ArrayList<>();
    private final List<Schematic.PlacedEntry> torches;
    private final Set<BlockCoord> walls;
    private final BuildRotation rotation;

    private Phase phase = Phase.PICK;
    private @Nullable MineColumn current;
    private boolean walking;
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
        this.stepWalker = new MineStepWalker(approach, safety);
        this.plan = new ArrayList<>(MinePlan.order(MinePlan.columns(layout.floors(), layout.air()), layout.entrance()));
        this.torches = new ArrayList<>(layout.optional());
        this.walls = new HashSet<>(layout.floors());
        plan.forEach(column -> walls.remove(column.floor()));
        this.rotation = rotation;
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
        stepWalker.reset();
    }

    @Override
    public void suspend() {
        approach.suspend();
        walking = false;
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
            walking = false;
            phase = Phase.TORCH;
            return;
        }
        current = next.get();
        actions = 0;
        dugHere = false;
        walking = false;
        phase = Phase.WALK;
    }

    private void walk() {
        MineColumn column = current();
        Optional<MineColumn> parent = MinePlan.parent(column, open);
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
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
        for (BlockCoord cell : MineDigOrder.column(column, wallsOf(column), feet, safety)) {
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
        boolean falling = !level.getEntitiesOfClass(FallingBlockEntity.class, columnBox(column))
                .isEmpty();
        if (falling || column.cells().stream().anyMatch(cell -> !open(cell))) {
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

    private List<BlockCoord> wallsOf(MineColumn column) {
        List<BlockCoord> found = new ArrayList<>();
        for (BlockCoord cell : column.cells()) {
            for (int[] side : SIDES) {
                BlockCoord wall = new BlockCoord(cell.x() + side[0], cell.y(), cell.z() + side[1]);
                if (walls.contains(wall)) {
                    found.add(wall);
                }
            }
        }
        return found;
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
        if (open(cell)) {
            return true;
        }
        List<BlockCoord> plugsNeeded = MineFluids.plugsBefore(cell, openCells, this::fluid);
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
            side.cells().forEach(walls::remove);
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
        BlockPlacer.Begin begin = placer.beginWithoutSupport(toPos(cell), BlockStates.stateFor(material), material);
        if (begin instanceof BlockPlacer.Started) {
            return true;
        }
        String reason = begin instanceof BlockPlacer.Refused refused
                ? refused.message()
                : ((BlockPlacer.Protected) begin).reason();
        fail(what + " в " + cell + " не поставить: " + reason);
        return false;
    }

    private void startBreak(BlockCoord cell, OptionalInt slot) {
        BlockPos pos = toPos(cell);
        BlockBreaker.Begin begin = slot.isPresent() ? breaker.beginWithSlot(pos, slot.getAsInt()) : breaker.begin(pos);
        if (begin instanceof BlockBreaker.Started) {
            breaking = true;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.AIR) {
            return;
        }
        String reason = begin instanceof BlockBreaker.Refused refused
                ? refused.message()
                : ((BlockBreaker.Protected) begin).reason();
        fail("клетку шахты " + cell + " не выломать: " + reason);
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
        Schematic.PlacedEntry entry = torches.isEmpty() ? null : torches.get(0);
        if (entry == null || BlockItems.countIn(spirit.getInventory(), entry.block()) < 1) {
            torches.clear();
            beginExit();
            return;
        }
        BlockPos pos = toPos(entry.pos());
        MineColumn near = nearestOpen(entry.pos());
        if (!level.getBlockState(pos).isAir() || near == null) {
            torches.remove(0);
            return;
        }
        BlockCoord stand = near.stand();
        if (!walking) {
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), STAND_ARRIVAL);
            walking = true;
        }
        BlockState wanted = BlockStates.stateFor(entry.block(), entry.state());
        if (rotation != BuildRotation.NONE) {
            wanted = wanted.rotate(level, pos, SchematicSite.mcRotation(rotation));
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED, STUCK, TIMEOUT -> {
                walking = false;
                torches.remove(0);
                if (placer.begin(pos, wanted, entry.block()) instanceof BlockPlacer.Refused refused) {
                    LOGGER.info("Шахта: факел в {} пропущен: {}", entry.pos(), refused.message());
                }
            }
        }
    }

    private void beginExit() {
        exitPath.clear();
        BlockPos feet = spirit.blockPosition();
        MineColumn step = nearestOpen(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
        while (step != null) {
            exitPath.add(step);
            step = parents.get(step);
        }
        walking = false;
        phase = Phase.EXIT;
    }

    private void exit() {
        if (exitPath.isEmpty()) {
            phase = Phase.DONE;
            return;
        }
        BlockCoord stand = exitPath.get(0).stand();
        if (!walking) {
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), STAND_ARRIVAL);
            walking = true;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                walking = false;
                exitPath.remove(0);
            }
            case STUCK, TIMEOUT ->
                fail("выход наверх застрял у ступени " + stand + ": "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal()));
        }
    }

    private @Nullable MineColumn nearestOpen(BlockCoord near) {
        MineColumn best = null;
        long bestDistance = Long.MAX_VALUE;
        for (MineColumn column : open) {
            BlockCoord stand = column.stand();
            long dx = (long) stand.x() - near.x();
            long dy = (long) stand.y() - near.y();
            long dz = (long) stand.z() - near.z();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = column;
            }
        }
        return best;
    }

    private List<MineOres.Pick> picks(BlockState state) {
        return PickOptions.of(spirit, state);
    }

    private boolean open(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    private boolean fluid(BlockCoord cell) {
        return !level.getBlockState(toPos(cell)).getFluidState().isEmpty();
    }

    private MineColumn current() {
        MineColumn column = current;
        if (column == null) {
            throw new IllegalStateException("Колонка проходки не выбрана: фаза " + phase + " идёт после PICK");
        }
        return column;
    }

    private static AABB columnBox(MineColumn column) {
        BlockCoord floor = column.floor();
        return new AABB(
                floor.x(), floor.y() + 1, floor.z(), floor.x() + 1, floor.y() + column.height() + 2, floor.z() + 1);
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
