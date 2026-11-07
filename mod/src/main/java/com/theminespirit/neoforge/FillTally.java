package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import java.util.Map;

final class FillTally {
    private final BuildResult result = new BuildResult();
    private int placed;
    private int prebuilt;
    private int skipped;
    private int failed;
    private int noStand;
    private int protectedSkipped;
    private int foreignSkipped;

    void placed(String blockId) {
        placed++;
        result.placed(blockId);
    }

    void prebuilt() {
        prebuilt++;
    }

    void skipped() {
        skipped++;
        result.skipped();
    }

    void failed() {
        failed++;
        result.failed();
    }

    void noStand() {
        noStand++;
    }

    void protectedSkipped() {
        protectedSkipped++;
    }

    void foreignSkipped() {
        foreignSkipped++;
    }

    int placedCount() {
        return placed;
    }

    int prebuiltCount() {
        return prebuilt;
    }

    int skippedCount() {
        return skipped;
    }

    int failedCount() {
        return failed;
    }

    int noStandCount() {
        return noStand;
    }

    void supplies(int have, int need) {
        result.supplies(have, need);
    }

    Map<String, Object> data() {
        return result.toData();
    }

    Map<String, Object> save(boolean running) {
        return new BuildState(placed, 0, skipped, failed, running).toMap();
    }

    void restore(Map<String, Object> saved, String blockId) {
        BuildState state = BuildState.fromMap(saved);
        placed = Math.max(placed, state.placed());
        skipped = Math.max(skipped, state.skipped());
        failed = Math.max(failed, state.failed());
        for (int index = 0; index < state.placed(); index++) {
            result.placed(blockId);
        }
        for (int index = 0; index < state.skipped(); index++) {
            result.skipped();
        }
        for (int index = 0; index < state.failed(); index++) {
            result.failed();
        }
    }

    String summary(int total) {
        return "не все клетки засыпаны: поставлено " + placed + " из " + total + "; пропущено: " + skipped
                + " (без стоянки: " + noStand + ", защищено: " + protectedSkipped + ", занято другим духом: "
                + foreignSkipped + ")";
    }
}
