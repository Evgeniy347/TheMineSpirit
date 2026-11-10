package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import org.jspecify.annotations.Nullable;

final class ChopHarvest {
    enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private final IdFilter logFilter;
    private final boolean replant;
    private final boolean orchard;
    private final TreeView view;
    private @Nullable DropCollector collector;
    private @Nullable ChopReplant planting;
    private @Nullable Tree tree;
    private @Nullable TreeSpecies species;
    private int planted;
    private int skipped;
    private boolean lacked;
    private String failureReason = "";

    ChopHarvest(SpiritEntity spirit, IdFilter logFilter, boolean replant, boolean orchard, TreeView view) {
        this.spirit = spirit;
        this.logFilter = logFilter;
        this.replant = replant;
        this.orchard = orchard;
        this.view = view;
    }

    void begin(Tree target, TreeSpecies harvested) {
        tree = target;
        species = harvested;
        collector = new DropCollector(
                spirit,
                logFilter,
                target.base(),
                ChopTuning.COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
        planting = null;
        planted = 0;
        skipped = 0;
        lacked = false;
        failureReason = "";
    }

    State tick() {
        if (collector != null) {
            switch (collector.tick()) {
                case RUNNING -> {
                    return State.RUNNING;
                }
                case DONE -> {
                    collector = null;
                    if (!replant) {
                        return State.DONE;
                    }
                    Tree target = tree;
                    TreeSpecies harvested = species;
                    if (target == null || harvested == null) {
                        failureReason = "дерево или порода пропали до досадки";
                        return State.FAILED;
                    }
                    planting = new ChopReplant(spirit, harvested, view, !orchard);
                    planting.begin(target, target.base().y());
                }
                case FAILED -> {
                    failureReason = collector.failureReason();
                    collector = null;
                    return State.FAILED;
                }
            }
        }
        ChopReplant replanted = planting;
        if (replanted != null && !replanted.tick()) {
            return State.RUNNING;
        }
        if (replanted != null) {
            planted = replanted.planted();
            skipped = replanted.skipped();
            lacked = replanted.lackedSaplings();
            planting = null;
        }
        return State.DONE;
    }

    void cancel() {
        if (collector != null) {
            collector.cancel();
            collector = null;
        }
        if (planting != null) {
            planting.cancel();
            planting = null;
        }
    }

    void suspend() {
        if (collector != null) {
            collector.suspend();
        }
        if (planting != null) {
            planting.suspend();
        }
    }

    int planted() {
        return planted;
    }

    int skipped() {
        return skipped;
    }

    boolean lacked() {
        return lacked;
    }

    String failureReason() {
        return failureReason;
    }
}
