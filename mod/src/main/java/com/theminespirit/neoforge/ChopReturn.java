package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import org.slf4j.Logger;

final class ChopReturn {
    private enum Stage {
        DESCENT,
        COLLECT
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final ChopScaffold scaffold;
    private final ChopHarvest harvest;
    private Tree tree;
    private TreeSpecies species;
    private int standingLogs;
    private Stage stage;

    ChopReturn(SpiritEntity spirit, ChopScaffold scaffold, ChopHarvest harvest) {
        this.spirit = spirit;
        this.scaffold = scaffold;
        this.harvest = harvest;
    }

    void begin(Tree target, TreeSpecies harvested, int logs) {
        tree = target;
        species = harvested;
        standingLogs = logs;
        if (scaffold.hasScaffold()) {
            scaffold.startDescent();
            stage = Stage.DESCENT;
            return;
        }
        startCollect();
    }

    ChopOutcome tick() {
        return switch (stage) {
            case DESCENT -> descend();
            case COLLECT -> collect();
        };
    }

    void cancel() {
        harvest.cancel();
    }

    void suspend() {
        harvest.suspend();
    }

    private ChopOutcome descend() {
        return switch (scaffold.tickDescent()) {
            case RUNNING -> ChopOutcome.running();
            case DONE -> startCollect();
            case FAILED -> {
                LOGGER.warn(
                        "[chop] {} у дерева {}: подмости не разобраны — {}",
                        spirit.getName().getString(),
                        tree.base(),
                        scaffold.failureReason());
                yield startCollect();
            }
        };
    }

    private ChopOutcome startCollect() {
        harvest.begin(tree, species);
        stage = Stage.COLLECT;
        return ChopOutcome.running();
    }

    private ChopOutcome collect() {
        return switch (harvest.tick()) {
            case RUNNING -> ChopOutcome.running();
            case DONE -> ChopOutcome.done(standingLogs, harvest.planted(), harvest.skipped(), harvest.lacked());
            case FAILED -> ChopOutcome.failed("подбор дропа после рубки: " + harvest.failureReason());
        };
    }
}
