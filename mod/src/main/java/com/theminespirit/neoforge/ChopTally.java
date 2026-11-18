package com.theminespirit.neoforge;

import com.theminespirit.core.script.ChopTreeParams;
import java.util.Map;

final class ChopTally {
    private int chopped;
    private int standingLogs;
    private int protectedLogs;
    private int plantedSaplings;
    private int skippedCells;
    private boolean lackedSaplings;
    private int leavesRemoved;

    int chopped() {
        return chopped;
    }

    int protectedLogs() {
        return protectedLogs;
    }

    void choppedTree(ChopOutcome outcome) {
        chopped++;
        absorb(outcome);
    }

    void protectedTree() {
        protectedLogs++;
    }

    boolean failed(ChopOutcome outcome) {
        absorb(outcome);
        return outcome.standingLogs() > 0;
    }

    Map<String, Object> save() {
        return ChopTreeSaved.save(values());
    }

    void restore(Map<String, Object> state, int countBound) {
        ChopTreeSaved.Values saved = ChopTreeSaved.restore(state, countBound, values());
        chopped = saved.chopped();
        standingLogs = saved.standingLogs();
        protectedLogs = saved.protectedLogs();
        plantedSaplings = saved.plantedSaplings();
        skippedCells = saved.skippedCells();
        lackedSaplings = saved.lackedSaplings();
        leavesRemoved = saved.leavesRemoved();
    }

    String summary(ChopTreeParams params) {
        return ChopTreeText.summary(
                params,
                chopped,
                standingLogs,
                protectedLogs,
                plantedSaplings,
                skippedCells,
                lackedSaplings,
                leavesRemoved);
    }

    private ChopTreeSaved.Values values() {
        return new ChopTreeSaved.Values(
                chopped, standingLogs, protectedLogs, plantedSaplings, skippedCells, lackedSaplings, leavesRemoved);
    }

    private void absorb(ChopOutcome outcome) {
        standingLogs += outcome.standingLogs();
        plantedSaplings += outcome.plantedSaplings();
        skippedCells += outcome.skippedCells();
        lackedSaplings |= outcome.lackedSaplings();
        leavesRemoved += outcome.leavesRemoved();
    }
}
