package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
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
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.ChopTreeParams;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class ChopTreeScript implements ScriptInstance {
    private static final String CHOPPED_KEY = "chopped";

    private enum Phase {
        FIND,
        APPROACH,
        CHOP,
        APPROACH_LOG,
        COLLECT
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
        };
    }

    @Override
    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(CHOPPED_KEY, chopped);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        Object saved = state.get(CHOPPED_KEY);
        if (saved == null) {
            return;
        }
        if (!(saved instanceof Number number) || number.intValue() < 0 || number.intValue() > params.count()) {
            throw new IllegalArgumentException("Состояние chop_tree: '" + CHOPPED_KEY + "' = " + saved
                    + ", а ожидалось целое число от 0 до count=" + params.count()
                    + ": снимок задачи повреждён или записан другой версией мода, проверьте его");
        }
        chopped = number.intValue();
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
        plan = ChopPlan.of(tree, ChopTuning.REACH_ABOVE_BASE);
        planIndex = 0;
        approach.begin(Vec3.atCenterOf(toPos(tree.base())), ChopTuning.STAND_DISTANCE);
        phase = Phase.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approach() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> unreachableTree();
        };
    }

    private ScriptStatus approachLog() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> resumeChop();
            case STUCK, TIMEOUT -> {
                skipLog();
                yield resumeChop();
            }
        };
    }

    private ScriptStatus resumeChop() {
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
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !movedCloser) {
                movedCloser = true;
                approach.begin(standingPointUnder(log), ChopTuning.CLOSE_DISTANCE);
                phase = Phase.APPROACH_LOG;
                return ScriptStatus.RUNNING;
            }
            skipLog();
        }
        return startCollect();
    }

    private void skipLog() {
        planIndex++;
        movedCloser = false;
    }

    private Vec3 standingPointUnder(BlockCoord log) {
        return Vec3.atBottomCenterOf(new BlockPos(log.x(), tree.base().y(), log.z()));
    }

    private ScriptStatus startCollect() {
        collector = new DropCollector(spirit, logFilter, tree.base(), ChopTuning.COLLECT_RADIUS, PickupTuning.DEFAULT);
        phase = Phase.COLLECT;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus collect() {
        return switch (collector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> finishTree();
            case FAILED -> fail("подбор дропа после рубки: " + collector.failureReason());
        };
    }

    private ScriptStatus finishTree() {
        collector = null;
        chopped++;
        consecutiveUnreachable = 0;
        candidates.markKnown(tree.logs());
        tree = null;
        plan = null;
        if (chopped >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        phase = Phase.FIND;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus outOfTrees() {
        return fail("срублено " + chopped + " из " + params.count() + ": деревья породы " + speciesText()
                + " в радиусе " + params.radius() + " закончились (отвергнуто не-деревьев: " + rejected
                + ", недоступных: " + unreachable + ")");
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
