package com.theminespirit.neoforge;

import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmTally;

final class FarmCounters {
    private final FarmJob job;
    private final int count;
    private final int radius;

    private int ticks;
    private int done;
    private int ineligible;
    private int dry;
    private int noStand;
    private int noSeeds;
    private int rejected;
    private int unreachable;
    private int planCalls;
    private int collectRuns;
    private int entityQueries;
    private int maxEntityQueriesPerTick;
    private int actionTick = -1;
    private int actionsThisTick;
    private int maxActionsPerTick;

    FarmCounters(FarmJob job, int count, int radius) {
        this.job = job;
        this.count = count;
        this.radius = radius;
    }

    boolean firstTick() {
        return ticks == 0;
    }

    void beginTick() {
        ticks++;
    }

    int done() {
        return done;
    }

    void restoreDone(int saved) {
        done = Math.max(done, saved);
    }

    void addDone() {
        done++;
    }

    void addIneligible() {
        ineligible++;
    }

    void addIneligible(int amount) {
        ineligible += amount;
    }

    void addDry(int amount) {
        dry += amount;
    }

    void addNoStand() {
        noStand++;
    }

    void addNoStand(int amount) {
        noStand += amount;
    }

    void addNoSeeds() {
        noSeeds++;
    }

    void addRejected() {
        rejected++;
    }

    void addUnreachable() {
        unreachable++;
    }

    void planCall() {
        planCalls++;
    }

    void collectRun() {
        collectRuns++;
    }

    void recordAction(int now) {
        actionsThisTick = now == actionTick ? actionsThisTick + 1 : 1;
        actionTick = now;
        maxActionsPerTick = Math.max(maxActionsPerTick, actionsThisTick);
    }

    void observeEntityQueries(int total, int previous) {
        maxEntityQueriesPerTick = Math.max(maxEntityQueriesPerTick, total - previous);
    }

    void addEntityQueries(int total) {
        entityQueries += total;
    }

    int planCalls() {
        return planCalls;
    }

    int collectRuns() {
        return collectRuns;
    }

    int entityQueries() {
        return entityQueries;
    }

    int maxEntityQueriesPerTick() {
        return maxEntityQueriesPerTick;
    }

    int maxActionsPerTick() {
        return maxActionsPerTick;
    }

    String withoutSuppliesReason() {
        FarmTally.Outcome refusal = new FarmTally(0, 0, 0, 0, 0, 1, 0, 0, 0).outcome(job);
        return ((FarmTally.Failed) refusal).text();
    }

    String lostSuppliesReason() {
        int missingTool = job == FarmJob.TILL ? 1 : 0;
        int missingSeeds = job == FarmJob.TILL ? 0 : 1;
        FarmTally tally =
                new FarmTally(done, count, ineligible, noStand, missingSeeds, missingTool, dry, unreachable, radius);
        return ((FarmTally.Failed) tally.outcome(job)).text();
    }

    FarmTally.Outcome conclusion() {
        int eligibleAndBlocking = done + noStand + noSeeds + rejected + unreachable;
        int requested = Math.min(count, eligibleAndBlocking);
        FarmTally tally =
                new FarmTally(done, requested, ineligible + rejected, noStand, noSeeds, 0, dry, unreachable, radius);
        return tally.outcome(job);
    }
}
