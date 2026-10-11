package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeChooser;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.ChopTreeParams;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

final class ChopTreeScript implements ScriptInstance {
    private enum Phase {
        FIND,
        APPROACH,
        CHOP,
        APPROACH_BASE,
        COLLECT
    }

    private final SpiritEntity spirit;
    private final ChopTreeParams params;
    private final Set<Block> logBlocks;
    private final IdFilter logFilter;
    private final BlockLocator.Search search;
    private final Approach approach;
    private final BlockBreaker breaker;
    private final List<BlockCoord> candidates = new ArrayList<>();
    private final Set<BlockCoord> candidateCoords = new HashSet<>();
    private final Set<BlockCoord> known = new HashSet<>();
    private Phase phase = Phase.FIND;
    private Tree tree;
    private ChopPlan plan;
    private DropCollector collector;
    private int planIndex;
    private int chopped;
    private int rejected;
    private int unreachable;
    private int consecutiveRejections;
    private boolean breaking;
    private boolean distanceRetried;
    private String failureReason;

    ChopTreeScript(SpiritEntity spirit, ChopTreeParams params) {
        this.spirit = spirit;
        this.params = params;
        this.logBlocks = logBlocks(params.species());
        this.logFilter = IdFilter.of(params.species().stream().map(TreeSpecies::logId).toList());
        BlockPos center = spirit.blockPosition();
        this.search = BlockLocator.search(spirit.level(), center, params.radius(), Set.copyOf(logBlocks));
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
            case APPROACH_BASE -> approachBase();
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

    private ScriptStatus find() {
        for (BlockCoord coord : locate()) {
            remember(coord);
        }
        Optional<BlockCoord> start = TreeChooser.next(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ(),
                candidates, known);
        if (start.isEmpty()) {
            return search.done() ? outOfTrees() : ScriptStatus.RUNNING;
        }
        TreeDetection detection = TreeShape.detect(start.get(), view());
        if (detection instanceof NotATree notATree) {
            return reject(notATree);
        }
        return startChop((Tree) detection);
    }

    private List<BlockCoord> locate() {
        List<BlockCoord> found = new ArrayList<>();
        pruneCandidates();
        if (candidates.size() >= ChopTuning.SEARCH_MAX_LOGS) {
            return found;
        }
        for (BlockPos pos : search.step(ScanTuning.SLICES_PER_TICK)) {
            found.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
        return found;
    }

    private void pruneCandidates() {
        if (candidates.isEmpty()) {
            return;
        }
        boolean removed = candidates.removeIf(known::contains);
        if (removed) {
            candidateCoords.clear();
            candidateCoords.addAll(candidates);
        }
    }

    private void remember(BlockCoord coord) {
        if (!known.contains(coord) && candidateCoords.add(coord)) {
            candidates.add(coord);
        }
    }

    private ScriptStatus reject(NotATree notATree) {
        rejected++;
        consecutiveRejections++;
        known.addAll(notATree.logs());
        if (consecutiveRejections > ChopTuning.MAX_REJECTED_TREES) {
            return fail("больше " + ChopTuning.MAX_REJECTED_TREES + " отказов подряд: только постройки и "
                    + "недоступные деревья, срублено " + chopped + " из " + params.count());
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus startChop(Tree found) {
        tree = found;
        plan = ChopPlan.of(tree, ChopTuning.REACH_ABOVE_BASE);
        planIndex = 0;
        consecutiveRejections = 0;
        approach.begin(Vec3.atCenterOf(toPos(tree.base())), ChopTuning.STAND_DISTANCE);
        phase = Phase.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approach() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                if (distanceRetried) {
                    distanceRetried = false;
                    planIndex++;
                    phase = Phase.CHOP;
                } else {
                    phase = Phase.CHOP;
                }
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> {
                if (distanceRetried) {
                    distanceRetried = false;
                    planIndex++;
                    phase = Phase.CHOP;
                } else {
                    unreachableTree();
                }
                yield ScriptStatus.RUNNING;
            }
        };
    }

    private ScriptStatus unreachableTree() {
        unreachable++;
        consecutiveRejections++;
        known.addAll(tree.logs());
        tree = null;
        plan = null;
        if (consecutiveRejections > ChopTuning.MAX_REJECTED_TREES) {
            return fail("больше " + ChopTuning.MAX_REJECTED_TREES + " отказов подряд: срублено " + chopped
                    + " из " + params.count() + ", недоступных деревьев " + unreachable);
        }
        phase = Phase.FIND;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus chop() {
        if (breaking) {
            BlockBreaker.Progress progress = breaker.tick();
            if (progress == BlockBreaker.Progress.BREAKING) {
                return ScriptStatus.RUNNING;
            }
            breaking = false;
            distanceRetried = false;
            planIndex++;
        }
        while (planIndex < plan.reachable().size()) {
            BlockPos pos = toPos(plan.reachable().get(planIndex));
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return ScriptStatus.RUNNING;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH && !distanceRetried) {
                distanceRetried = true;
                BlockCoord base = tree.base();
                BlockPos groundPos = new BlockPos(base.x(), base.y(), base.z());
                approach.begin(Vec3.atCenterOf(groundPos), ChopTuning.STAND_DISTANCE);
                phase = Phase.APPROACH;
                return ScriptStatus.RUNNING;
            }
            distanceRetried = false;
            planIndex++;
        }
        return startApproachBase();
    }

    private ScriptStatus startApproachBase() {
        approach.begin(Vec3.atCenterOf(toPos(tree.base())), ChopTuning.STAND_DISTANCE);
        phase = Phase.APPROACH_BASE;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approachBase() {
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> startCollect();
            case STUCK, TIMEOUT -> startCollect(); // если не дошли — пробуем подбирать всё равно
        };
    }

    private ScriptStatus startCollect() {
        BlockCoord base = tree.base();
        collector = new DropCollector(spirit, logFilter, base, ChopTuning.COLLECT_RADIUS, PickupTuning.DEFAULT);
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
        consecutiveRejections = 0;
        known.addAll(tree.logs());
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

    private TreeView view() {
        return new LevelTreeView(spirit.level(), logBlocks);
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
