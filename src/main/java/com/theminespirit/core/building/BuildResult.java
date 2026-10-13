package com.theminespirit.core.building;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BuildResult {
    private int placed;
    private int removed;
    private int skipped;
    private int failed;
    private int have;
    private int missing;
    private String block = "";

    public void placed(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: по нему собирается итог");
        placed++;
        note(blockId);
    }

    public void removed() {
        removed++;
    }

    public void skipped() {
        skipped++;
    }

    public void failed() {
        failed++;
    }

    public void supplies(int haveCount, int missingCount) {
        if (haveCount < 0 || missingCount < 0) {
            throw new IllegalArgumentException("Запасы не бывают отрицательными: have=" + haveCount + ", missing=" + missingCount);
        }
        have = haveCount;
        missing = missingCount;
    }

    public Map<String, Object> toData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("placed", placed);
        data.put("removed", removed);
        data.put("skipped", skipped);
        data.put("failed", failed);
        data.put("have", have);
        data.put("missing", missing);
        data.put("block", block);
        return Map.copyOf(data);
    }

    private void note(String blockId) {
        if (block.isEmpty()) {
            block = blockId;
        }
    }
}
