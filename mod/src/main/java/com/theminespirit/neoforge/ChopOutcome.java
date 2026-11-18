package com.theminespirit.neoforge;

record ChopOutcome(
        State state,
        int standingLogs,
        int plantedSaplings,
        int skippedCells,
        boolean lackedSaplings,
        int leavesRemoved,
        String reason) {
    enum State {
        RUNNING,
        DONE,
        PROTECTED,
        UNREACHABLE,
        FAILED
    }

    static ChopOutcome running() {
        return new ChopOutcome(State.RUNNING, 0, 0, 0, false, 0, null);
    }

    static ChopOutcome done(
            int standingLogs, int plantedSaplings, int skippedCells, boolean lackedSaplings, int leavesRemoved) {
        return new ChopOutcome(
                State.DONE, standingLogs, plantedSaplings, skippedCells, lackedSaplings, leavesRemoved, null);
    }

    static ChopOutcome simple(State state) {
        return new ChopOutcome(state, 0, 0, 0, false, 0, null);
    }

    static ChopOutcome failed(String reason) {
        return new ChopOutcome(State.FAILED, 0, 0, 0, false, 0, reason);
    }

    static ChopOutcome leftStanding(int logs, int plantedSaplings, int skippedCells, boolean lackedSaplings) {
        return new ChopOutcome(
                State.FAILED,
                logs,
                plantedSaplings,
                skippedCells,
                lackedSaplings,
                0,
                "оставлено " + logs + " брёвен: дерево не срублено целиком");
    }
}
