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
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.ChopTreeParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class ChopTreeScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PickupTuning HIGH_PICKUP = new PickupTuning(PickupTuning.REFRESH_TICKS,
            PickupTuning.PICKUP_REACH, 200, PickupTuning.MAX_DELAY_WAIT_TICKS);
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
        PILLAR_APPROACH,
        PILLAR_BUILD,
        CHOP_HIGH,
        DESCEND_PILLAR,
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
    private PillarColumn pillar;
    private final List<BlockCoord> pillarRemaining = new ArrayList<>();
    private PillarDismantler dismantler;
    private DropCollector highCollector;
    private boolean highBreaking;
    private int highLogIndex;
    private int descendStall;
    private String pillarFailure;
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
            case PILLAR_APPROACH -> pillarApproach();
            case PILLAR_BUILD -> pillarBuild();
            case CHOP_HIGH -> chopHigh();
            case DESCEND_PILLAR -> descendPillar();
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
        if (pillarFailure != null) {
            text.append("; столб: ").append(pillarFailure);
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
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed())
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

    private ScriptStatus approachLog() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> resumeChopPhase();
            case STUCK, TIMEOUT -> {
                leaveStanding();
                movedCloser = false;
                yield resumeChopPhase();
            }
        };
    }

    private ScriptStatus resumeChopPhase() {
        phase = Phase.CHOP;
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
        BlockPos stand = standingCellNearTrunk();
        if (stand == null) {
            return leaveHigh("рядом со стволом нет места для столба");
        }
        if (!stand.equals(spirit.blockPosition())) {
            approach.begin(Vec3.atBottomCenterOf(stand), ChopTuning.CLOSE_DISTANCE);
            phase = Phase.PILLAR_APPROACH;
            return ScriptStatus.RUNNING;
        }
        return beginPillar();
    }

    private ScriptStatus pillarApproach() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> beginPillar();
            case STUCK, TIMEOUT -> leaveHigh("не подошёл к месту столба рядом со стволом");
        };
    }

    private ScriptStatus beginPillar() {
        int topY = highLogs.stream().mapToInt(BlockCoord::y).max().orElse(spirit.blockPosition().getY());
        int height = topY - 2 - spirit.blockPosition().getY();
        if (height < 1) {
            highLogIndex = 0;
            highBreaking = false;
            phase = Phase.CHOP_HIGH;
            return ScriptStatus.RUNNING;
        }
        String blockId = cheapBlock();
        if (blockId == null) {
            return leaveHigh("нет материала для столба: нужны земля или булыжник");
        }
        int have = PillarColumn.count(spirit, blockId);
        if (have < height) {
            return leaveHigh("нет материала '" + blockId + "': нужно " + height + ", есть " + have);
        }
        pillar = new PillarColumn(spirit, blockId, height);
        phase = Phase.PILLAR_BUILD;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus pillarBuild() {
        return switch (pillar.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case BUILT -> {
                pillarRemaining.clear();
                pillarRemaining.addAll(topDown(pillar.cells()));
                highLogIndex = 0;
                highBreaking = false;
                phase = Phase.CHOP_HIGH;
                yield ScriptStatus.RUNNING;
            }
            case FAILED -> leaveHigh("столб не построен: " + pillar.failureReason());
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
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                highLogIndex++;
                continue;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !pillarRemaining.isEmpty()) {
                BlockCoord cell = pillarRemaining.remove(0);
                dismantler = new PillarDismantler(spirit, List.of(cell));
                descendStall = 0;
                phase = Phase.DESCEND_PILLAR;
                return ScriptStatus.RUNNING;
            }
            standingLogs++;
            highLogIndex++;
        }
        return startDismantle();
    }

    private ScriptStatus descendPillar() {
        if (dismantler == null) {
            phase = Phase.CHOP_HIGH;
            return ScriptStatus.RUNNING;
        }
        PillarDismantler.Outcome result = dismantler.tick();
        if (result == PillarDismantler.Outcome.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        if (result == PillarDismantler.Outcome.FAILED) {
            dismantler = null;
            standingLogs++;
            highLogIndex++;
            phase = Phase.CHOP_HIGH;
            return ScriptStatus.RUNNING;
        }
        if (!spirit.onGround()) {
            if (++descendStall > 40) {
                descendStall = 0;
                dismantler = null;
                phase = Phase.CHOP_HIGH;
            }
            return ScriptStatus.RUNNING;
        }
        descendStall = 0;
        dismantler = null;
        phase = Phase.CHOP_HIGH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus collectHigh() {
        if (highCollector == null) {
            highCollector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS,
                    PickupTuning.DEFAULT, OverflowMode.FAIL);
        }
        return switch (highCollector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> params.replant() ? startReplant() : finishTree();
            case FAILED -> {
                pillarFailure = "часть дропа после рубки верха не подобрана: " + highCollector.failureReason();
                LOGGER.info("Дух {} срубил верх, но не дотянулся до части дропа: {}", spirit.getUUID(),
                        highCollector.failureReason());
                yield params.replant() ? startReplant() : finishTree();
            }
        };
    }

    private ScriptStatus startDismantle() {
        highCollector = null;
        if (pillarRemaining.isEmpty()) {
            pillar = null;
            return startHighCollect();
        }
        dismantler = new PillarDismantler(spirit, new ArrayList<>(pillarRemaining));
        phase = Phase.DISMANTLE_PILLAR;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus dismantlePillar() {
        if (dismantler == null) {
            return startHighCollect();
        }
        PillarDismantler.Outcome result = dismantler.tick();
        if (result == PillarDismantler.Outcome.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        dismantler = null;
        pillar = null;
        pillarRemaining.clear();
        return startHighCollect();
    }

    private ScriptStatus startHighCollect() {
        if (highLogs.isEmpty()) {
            return params.replant() ? startReplant() : finishTree();
        }
        highCollector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS,
                HIGH_PICKUP, OverflowMode.FAIL);
        phase = Phase.COLLECT_HIGH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus leaveHigh(String reason) {
        standingLogs += highLogs.size();
        pillarFailure = reason;
        pillar = null;
        pillarRemaining.clear();
        return params.replant() ? startReplant() : finishTree();
    }

    private BlockPos standingCellNearTrunk() {
        BlockPos base = toPos(tree.base());
        int feetY = spirit.blockPosition().getY();
        for (int radius = 1; radius <= 2; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(base.getX() + dx, feetY, base.getZ() + dz);
                    if (!isStandable(pos)) {
                        continue;
                    }
                    double distance = spirit.position().distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private boolean isStandable(BlockPos pos) {
        Level level = spirit.level();
        BlockPos below = pos.below();
        return (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())
                && (level.getBlockState(pos.above()).isAir() || level.getBlockState(pos.above()).canBeReplaced())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static List<BlockCoord> topDown(List<BlockCoord> cells) {
        List<BlockCoord> order = new ArrayList<>(cells);
        Collections.reverse(order);
        return order;
    }

    private String cheapBlock() {
        return PillarColumn.cheapBlock(spirit);
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
                    : (cheapBlock() == null
                            ? leaveHigh("нет материала для столба: нужны земля или булыжник")
                            : startPillarBuild());
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
