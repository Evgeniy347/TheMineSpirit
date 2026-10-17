package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeCandidates;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.neoforge.fortify.DismantleExecutor;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.ChopTreeParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class ChopTreeScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHOPPED_KEY = "chopped";
    private static final String STANDING_KEY = "standingLogs";
    private static final String PROTECTED_KEY = "protectedLogs";
    private static final String PLANTED_KEY = "plantedSaplings";
    private static final String SKIPPED_KEY = "skippedCells";
    private static final String LACKED_KEY = "lackedSaplings";

    private enum Phase {
        FIND,
        APPROACH,
        CHOP,
        APPROACH_LOG,
        COLLECT,
        REPLANT,
        PILLAR_BUILD,
        CHOP_HIGH,
        COLLECT_HIGH,
        DISMANTLE_PILLAR
    }

    private final SpiritEntity spirit;
    private final ChopTreeParams params;
    private final Set<Block> logBlocks;
    private final IdFilter logFilter;
    private final BlockLocator.Search search;
    private final LevelTreeView treeView;
    private final Approach approach;
    private final BlockBreaker breaker;
    private final TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);
    private Phase phase = Phase.FIND;
    private Tree tree;
    private ChopPlan plan;
    private DropCollector collector;
    private Replanter replanter;
    private List<BlockCoord> highLogs = List.of();
    private int pillarBaseY;
    private int pillarBuilt;
    private int pillarTargetHeight;
    private final List<BlockCoord> pillarCells = new ArrayList<>();
    private DismantleExecutor dismantler;
    private DropCollector highCollector;
    private boolean highBreaking;
    private int highLogIndex;
    private boolean movedCloserHigh;
    private TreeSpecies treeSpecies;
    private int standingLogs;
    private int protectedLogs;
    private int plantedSaplings;
    private int skippedCells;
    private boolean lackedSaplings;
    private int planIndex;
    private int chopped;
    private int rejected;
    private int unreachable;
    private int consecutiveUnreachable;
    private boolean breaking;
    private boolean movedCloser;
    private String failureReason;

    public ChopTreeScript(SpiritEntity spirit, ChopTreeParams params) {
        this.spirit = spirit;
        this.params = params;
        this.logBlocks = logBlocks(params.species());
        this.logFilter = IdFilter.of(params.species().stream().map(TreeSpecies::logId).toList());
        BlockPos center = spirit.blockPosition();
        this.search = BlockLocator.search(spirit.level(), center, params.radius(), Set.copyOf(logBlocks));
        this.treeView = new LevelTreeView(spirit.level(), logBlocks);
        this.approach = new Approach(spirit);
        this.breaker = new BlockBreaker(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время рубки");
        }
        return switch (phase) {
            case FIND -> find();
            case APPROACH -> approach();
            case CHOP -> chop();
            case APPROACH_LOG -> approachLog();
            case COLLECT -> collect();
            case REPLANT -> replant();
            case PILLAR_BUILD -> pillarBuild();
            case CHOP_HIGH -> chopHigh();
            case COLLECT_HIGH -> collectHigh();
            case DISMANTLE_PILLAR -> dismantlePillar();
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(chopped, params.count()), params.count()));
    }

    @Override
    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (highCollector != null) {
            highCollector.cancel();
        }
        if (replanter != null) {
            replanter.cancel();
        }
        if (dismantler != null) {
            dismantler.cancel();
        }
    }

    @Override
    public void suspend() {
        approach.suspend();
        if (breaking) {
            breaker.cancel();
            breaking = false;
        }
        if (collector != null) {
            collector.suspend();
        }
        if (highCollector != null) {
            highCollector.suspend();
        }
        if (replanter != null) {
            replanter.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(CHOPPED_KEY, chopped,
                STANDING_KEY, standingLogs,
                PROTECTED_KEY, protectedLogs,
                PLANTED_KEY, plantedSaplings,
                SKIPPED_KEY, skippedCells,
                LACKED_KEY, lackedSaplings);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        chopped = readCount(state, CHOPPED_KEY, params.count(), chopped);
        standingLogs = readCount(state, STANDING_KEY, Integer.MAX_VALUE, standingLogs);
        protectedLogs = readCount(state, PROTECTED_KEY, Integer.MAX_VALUE, protectedLogs);
        plantedSaplings = readCount(state, PLANTED_KEY, Integer.MAX_VALUE, plantedSaplings);
        skippedCells = readCount(state, SKIPPED_KEY, Integer.MAX_VALUE, skippedCells);
        Object lacked = state.get(LACKED_KEY);
        if (lacked != null) {
            if (!(lacked instanceof Boolean flag)) {
                throw new IllegalArgumentException("Состояние chop_tree: '" + LACKED_KEY + "' = " + lacked
                        + ", а ожидалось булево значение: снимок задачи повреждён или записан другой версией мода, "
                        + "проверьте его");
            }
            lackedSaplings = flag;
        }
    }

    private static int readCount(Map<String, Object> state, String key, int bound, int previous) {
        Object saved = state.get(key);
        if (saved == null) {
            return previous;
        }
        if (!(saved instanceof Number number) || number.intValue() < 0 || number.intValue() > bound) {
            throw new IllegalArgumentException("Состояние chop_tree: '" + key + "' = " + saved
                    + ", а ожидалось целое число от 0 до " + bound
                    + ": снимок задачи повреждён или записан другой версией мода, проверьте его");
        }
        return number.intValue();
    }

    public String summary() {
        StringBuilder text = new StringBuilder("срублено ").append(chopped).append(" из ").append(params.count());
        if (standingLogs > 0) {
            text.append("; оставлено брёвен выше досягаемости: ").append(standingLogs);
        }
        if (protectedLogs > 0) {
            text.append("; пропущено защищённых: ").append(protectedLogs);
        }
        if (params.replant()) {
            text.append("; replant: ").append(replantText());
        }
        return text.toString();
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Text(summary()));
    }

    public long treeBlockReads() {
        return treeView.blockReads();
    }

    public int searchExaminedPositions() {
        return search.examinedPositions();
    }

    public int searchMaxExaminedPositionsPerTick() {
        return search.maxExaminedPerStep();
    }

    private ScriptStatus find() {
        if (chopped >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        locate();
        Optional<BlockCoord> start = candidates.nearest(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        if (start.isEmpty()) {
            return search.done() ? outOfTrees() : ScriptStatus.RUNNING;
        }
        if (!treeView.isLog(start.get())) {
            candidates.markKnown(List.of(start.get()));
            return ScriptStatus.RUNNING;
        }
        TreeDetection detection = TreeShape.detect(start.get(), treeView);
        if (detection instanceof NotATree notATree) {
            return reject(notATree);
        }
        return startChop((Tree) detection);
    }

    private void locate() {
        if (candidates.isFull()) {
            return;
        }
        for (BlockPos pos : search.step(ScanTuning.SLICES_PER_TICK)) {
            candidates.offer(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private ScriptStatus reject(NotATree notATree) {
        rejected++;
        candidates.markKnown(notATree.logs());
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus startChop(Tree found) {
        tree = found;
        treeSpecies = speciesOf(found);
        plan = ChopPlan.of(tree, ChopTuning.REACH_ABOVE_BASE);
        highLogs = tree.logs().stream()
                .filter(coord -> coord.y() > tree.base().y() + ChopTuning.REACH_ABOVE_BASE)
                .toList();
        planIndex = 0;
        approach.begin(Vec3.atCenterOf(toPos(tree.base())), ChopTuning.STAND_DISTANCE);
        phase = Phase.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approach() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> resumeChopPhase();
            case STUCK, TIMEOUT -> unreachableTree();
        };
    }

    private boolean highApproach;

    private ScriptStatus approachLog() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> resumeChopPhase();
            case STUCK, TIMEOUT -> {
                if (highApproach) {
                    highLogIndex++;
                } else {
                    leaveStanding();
                }
                movedCloserHigh = false;
                movedCloser = false;
                yield resumeChopPhase();
            }
        };
    }

    private ScriptStatus resumeChopPhase() {
        phase = highApproach ? Phase.CHOP_HIGH : Phase.CHOP;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus unreachableTree() {
        unreachable++;
        consecutiveUnreachable++;
        candidates.markKnown(tree.logs());
        tree = null;
        plan = null;
        if (consecutiveUnreachable > ChopTuning.MAX_REJECTED_TREES) {
            return fail("больше " + ChopTuning.MAX_REJECTED_TREES + " недоступных деревьев подряд: срублено "
                    + chopped + " из " + params.count() + ", недоступных деревьев " + unreachable);
        }
        phase = Phase.FIND;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus chop() {
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return ScriptStatus.RUNNING;
            }
            breaking = false;
            skipLog();
        }
        while (planIndex < plan.reachable().size()) {
            BlockCoord log = plan.reachable().get(planIndex);
            BlockBreaker.Begin begin = breaker.begin(toPos(log));
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return ScriptStatus.RUNNING;
            }
            if (begin instanceof BlockBreaker.Protected) {
                return protectedTree();
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !movedCloser) {
                movedCloser = true;
                highApproach = false;
                approach.begin(standingPointUnder(log), ChopTuning.CLOSE_DISTANCE);
                phase = Phase.APPROACH_LOG;
                return ScriptStatus.RUNNING;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                skipLog();
            } else {
                leaveStanding();
            }
        }
        return startCollect();
    }

    private ScriptStatus protectedTree() {
        protectedLogs++;
        candidates.markKnown(tree.logs());
        tree = null;
        plan = null;
        phase = Phase.FIND;
        return ScriptStatus.RUNNING;
    }

    private void leaveStanding() {
        standingLogs++;
        skipLog();
    }

    private void skipLog() {
        planIndex++;
        movedCloser = false;
    }

    private Vec3 standingPointUnder(BlockCoord log) {
        return Vec3.atBottomCenterOf(new BlockPos(log.x(), tree.base().y(), log.z()));
    }

    private ScriptStatus startPillarBuild() {
        pillarBaseY = spirit.blockPosition().getY();
        int topY = highLogs.stream().mapToInt(BlockCoord::y).max().orElse(pillarBaseY + 1);
        pillarTargetHeight = Math.max(1, topY - pillarBaseY - 1);
        pillarBuilt = 0;
        pillarCells.clear();
        phase = Phase.PILLAR_BUILD;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus pillarBuild() {
        if (pillarBuilt >= pillarTargetHeight) {
            phase = Phase.CHOP_HIGH;
            highLogIndex = 0;
            highBreaking = false;
            movedCloserHigh = false;
            return ScriptStatus.RUNNING;
        }
        String blockId = cheapBlock();
        if (blockId == null) {
            return fail("нет материала для столба: нужны земля или булыжник");
        }
        BlockPos target = new BlockPos(tree.base().x(), pillarBaseY + pillarBuilt, tree.base().z());
        if (!levelIsAir(target)) {
            return fail("клетка " + target.toShortString() + " занята: столб не строится");
        }
        if (levelFluid(target)) {
            return fail("в клетке " + target.toShortString() + " жидкость: столб не строится");
        }
        if (spirit.onGround() && pillarBuilt > 0 && spirit.getY() < target.getY() + 0.5) {
            spirit.getJumpControl().jump();
            return ScriptStatus.RUNNING;
        }
        BlockPlacer.Begin begin = new BlockPlacer(spirit).begin(target, BlockStates.stateFor(blockId), blockId);
        if (begin instanceof BlockPlacer.Started) {
            pillarCells.add(new BlockCoord(target.getX(), target.getY(), target.getZ()));
            pillarBuilt++;
            return ScriptStatus.RUNNING;
        }
        if (begin instanceof BlockPlacer.Protected p) {
            return fail("ставить запрещено: " + p.reason());
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> fail("нет материала '" + blockId + "': нужно 1, есть 0");
            case OCCUPIED -> fail("клетка " + target.toShortString() + " занята");
            case NO_SUPPORT -> fail("под клеткой " + target.toShortString() + " нет опоры");
            case OUT_OF_REACH -> fail("клетка " + target.toShortString() + " вне досягаемости");
            case CHUNK_NOT_LOADED -> fail("чанк с клеткой " + target.toShortString() + " не загружен");
        };
    }

    private ScriptStatus chopHigh() {
        if (highBreaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return ScriptStatus.RUNNING;
            }
            highBreaking = false;
            highLogIndex++;
        }
        while (highLogIndex < highLogs.size()) {
            BlockCoord log = highLogs.get(highLogIndex);
            BlockBreaker.Begin begin = breaker.begin(toPos(log));
            if (begin instanceof BlockBreaker.Started) {
                highBreaking = true;
                return ScriptStatus.RUNNING;
            }
            if (begin instanceof BlockBreaker.Protected) {
                return protectedTree();
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !movedCloserHigh) {
                movedCloserHigh = true;
                highApproach = true;
                approach.begin(standingPointUnder(log), ChopTuning.CLOSE_DISTANCE);
                phase = Phase.APPROACH_LOG;
                return ScriptStatus.RUNNING;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                highLogIndex++;
                movedCloserHigh = false;
            } else {
                highLogIndex++;
                movedCloserHigh = false;
            }
        }
        return collectHigh();
    }

    private ScriptStatus collectHigh() {
        if (highCollector == null) {
            highCollector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS,
                    PickupTuning.DEFAULT, OverflowMode.FAIL);
        }
        return switch (highCollector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> startDismantle();
            case FAILED -> fail("подбор дропа после рубки верха: " + highCollector.failureReason());
        };
    }

    private ScriptStatus startDismantle() {
        highCollector = null;
        List<BlockCoord> topDown = new ArrayList<>(pillarCells);
        Collections.reverse(topDown);
        dismantler = new DismantleExecutor(spirit, topDown);
        phase = Phase.DISMANTLE_PILLAR;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus dismantlePillar() {
        if (dismantler == null || dismantler.done()) {
            dismantler = null;
            return params.replant() ? startReplant() : finishTree();
        }
        dismantler.tick();
        return ScriptStatus.RUNNING;
    }

    private String cheapBlock() {
        if (spirit.getInventory().countItem(Items.DIRT) > 0) {
            return "minecraft:dirt";
        }
        if (spirit.getInventory().countItem(Items.COBBLESTONE) > 0) {
            return "minecraft:cobblestone";
        }
        return null;
    }

    private boolean levelIsAir(BlockPos pos) {
        return spirit.level().getBlockState(pos).isAir();
    }

    private boolean levelFluid(BlockPos pos) {
        return !spirit.level().getFluidState(pos).isEmpty();
    }

    private ScriptStatus startCollect() {
        collector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS,
                PickupTuning.DEFAULT, OverflowMode.FAIL);
        phase = Phase.COLLECT;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus collect() {
        return switch (collector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> highLogs.isEmpty()
                    ? (params.replant() ? startReplant() : finishTree())
                    : (cheapBlock() == null ? finishWithStanding() : startPillarBuild());
            case FAILED -> fail("подбор дропа после рубки: " + collector.failureReason());
        };
    }

    private ScriptStatus startReplant() {
        collector = null;
        int baseY = tree.base().y();
        List<BlockPos> cells = tree.logs().stream().filter(log -> log.y() == baseY).map(ChopTreeScript::toPos).toList();
        replanter = new Replanter(spirit, treeSpecies, cells);
        phase = Phase.REPLANT;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus replant() {
        if (replanter.tick() == Replanter.State.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        plantedSaplings += replanter.planted();
        skippedCells += replanter.skipped();
        lackedSaplings |= replanter.lackedSaplings();
        replanter = null;
        return finishTree();
    }

    private ScriptStatus finishWithStanding() {
        standingLogs += plan.leftStanding();
        return params.replant() ? startReplant() : finishTree();
    }

    private ScriptStatus finishTree() {
        collector = null;
        chopped++;
        consecutiveUnreachable = 0;
        candidates.markKnown(tree.logs());
        tree = null;
        plan = null;
        if (chopped >= params.count()) {
            LOGGER.info("Дух {} завершил chop_tree: {}", spirit.getUUID(), summary());
            return ScriptStatus.SUCCEEDED;
        }
        phase = Phase.FIND;
        return ScriptStatus.RUNNING;
    }

    private String replantText() {
        if (lackedSaplings) {
            return "нет саженцев" + (plantedSaplings > 0 ? ", посажено " + plantedSaplings : "");
        }
        String text = "посажено " + plantedSaplings;
        return skippedCells > 0 ? text + ", клеток пропущено " + skippedCells : text;
    }

    private TreeSpecies speciesOf(Tree found) {
        BlockCoord base = found.base();
        String blockId = BuiltInRegistries.BLOCK.getKey(spirit.level().getBlockState(toPos(base)).getBlock()).toString();
        return TreeSpecies.ofLog(blockId).orElseThrow(() -> new IllegalStateException("Основание дерева " + base
                + " — блок '" + blockId + "', а не бревно известной породы: поиск брал только брёвна из TreeSpecies"));
    }

    private ScriptStatus outOfTrees() {
        return fail("срублено " + chopped + " из " + params.count() + ": деревья породы " + speciesText()
                + " в радиусе " + params.radius() + " закончились (отвергнуто не-деревьев: " + rejected
                + ", недоступных: " + unreachable + ", пропущено защищённых: " + protectedLogs + ")");
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private String speciesText() {
        return params.species().size() == 1 ? params.species().get(0).id() : TreeSpecies.ANY;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static Set<Block> logBlocks(List<TreeSpecies> species) {
        Set<Block> blocks = new LinkedHashSet<>();
        for (TreeSpecies entry : species) {
            blocks.add(BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(entry.logId()))
                    .orElseThrow(() -> new IllegalStateException("Блок брёвен '" + entry.logId() + "' породы '"
                            + entry.id() + "' отсутствует в реестре: сторож treeSpecies_AllIds_ExistInRegistries "
                            + "обязан был поймать опечатку до запуска рубки")));
        }
        return blocks;
    }
}
