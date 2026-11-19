package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.script.ChopTreeParams;

final class ChopTreeText {
    private ChopTreeText() {}

    record Counts(
            int chopped,
            int standingLogs,
            int protectedLogs,
            int plantedSaplings,
            int skippedCells,
            boolean lackedSaplings,
            int leavesRemoved) {}

    static String summary(ChopTreeParams params, Counts counts) {
        StringBuilder text = new StringBuilder("срублено деревьев ")
                .append(counts.chopped())
                .append(" из ")
                .append(params.count());
        if (counts.standingLogs() > 0) {
            text.append("; оставлено брёвен: ").append(counts.standingLogs());
        }
        if (counts.leavesRemoved() > 0) {
            text.append("; снято листвы: ").append(counts.leavesRemoved());
        }
        if (counts.protectedLogs() > 0) {
            text.append("; пропущено защищённых: ").append(counts.protectedLogs());
        }
        if (params.replant()) {
            text.append("; replant: ")
                    .append(replantText(counts.plantedSaplings(), counts.skippedCells(), counts.lackedSaplings()));
        }
        return text.toString();
    }

    static String speciesText(ChopTreeParams params) {
        return params.species().size() == 1 ? params.species().get(0).id() : TreeSpecies.ANY;
    }

    private static String replantText(int plantedSaplings, int skippedCells, boolean lackedSaplings) {
        if (lackedSaplings) {
            return "нет саженцев" + (plantedSaplings > 0 ? ", посажено " + plantedSaplings : "");
        }
        String text = "посажено " + plantedSaplings;
        return skippedCells > 0 ? text + ", клеток пропущено " + skippedCells : text;
    }
}
