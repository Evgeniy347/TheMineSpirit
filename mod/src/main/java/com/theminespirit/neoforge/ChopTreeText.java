package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.script.ChopTreeParams;

final class ChopTreeText {
    private ChopTreeText() {}

    static String summary(
            ChopTreeParams params,
            int chopped,
            int standingLogs,
            int protectedLogs,
            int plantedSaplings,
            int skippedCells,
            boolean lackedSaplings) {
        StringBuilder text = new StringBuilder("срублено деревьев ")
                .append(chopped)
                .append(" из ")
                .append(params.count());
        if (standingLogs > 0) {
            text.append("; оставлено брёвен: ").append(standingLogs);
        }
        if (protectedLogs > 0) {
            text.append("; пропущено защищённых: ").append(protectedLogs);
        }
        if (params.replant()) {
            text.append("; replant: ").append(replantText(plantedSaplings, skippedCells, lackedSaplings));
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
