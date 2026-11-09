package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;

final class ChopReplant {
    private final SpiritEntity spirit;
    private final TreeSpecies species;
    private final TreeView view;
    private final boolean areaReplant;
    private Replanter replanter;
    private int planted;
    private int skipped;
    private boolean lackedSaplings;

    ChopReplant(SpiritEntity spirit, TreeSpecies species, TreeView view, boolean areaReplant) {
        this.spirit = spirit;
        this.species = species;
        this.view = view;
        this.areaReplant = areaReplant;
    }

    void begin(Tree tree, int baseY) {
        List<BlockPos> cells = tree.logs().stream()
                .filter(log -> log.y() == baseY)
                .map(ChopReplant::toPos)
                .toList();
        replanter = new Replanter(spirit, species, cells, view, areaReplant);
    }

    boolean tick() {
        if (replanter.tick() == Replanter.State.RUNNING) {
            return false;
        }
        planted += replanter.planted();
        skipped += replanter.skipped();
        lackedSaplings |= replanter.lackedSaplings();
        replanter = null;
        return true;
    }

    void cancel() {
        if (replanter != null) {
            replanter.cancel();
        }
    }

    void suspend() {
        if (replanter != null) {
            replanter.suspend();
        }
    }

    int planted() {
        return planted;
    }

    int skipped() {
        return skipped;
    }

    boolean lackedSaplings() {
        return lackedSaplings;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
