package com.theminespirit.core.building;

import java.util.List;
import java.util.Objects;

public final class ToolBudget {
    public record Plan(int hits, int durability, int tools, boolean enough) {
        public Plan {
            if (hits < 0 || durability < 0 || tools < 0) {
                throw new IllegalArgumentException("Удары " + hits + ", прочность " + durability + " и инструменты "
                        + tools + ": отрицательных не бывает");
            }
        }

        public int missing() {
            return Math.max(0, hits - durability);
        }
    }

    private ToolBudget() {}

    public static Plan of(int hits, List<Integer> durability) {
        Objects.requireNonNull(durability, "Прочности инструментов не могут быть null: нет инструментов — пусто");
        if (hits < 0) {
            throw new IllegalArgumentException("Ударов " + hits + ": отрицательного запаса не бывает");
        }
        int total = 0;
        int tools = 0;
        for (Integer left : durability) {
            if (left == null || left <= 0) {
                continue;
            }
            total += left;
            tools++;
            if (total >= hits) {
                break;
            }
        }
        return new Plan(hits, Math.min(total, hits), tools, total >= hits);
    }

    public static int toolsFor(int hits, int perTool) {
        if (hits < 0 || perTool <= 0) {
            throw new IllegalArgumentException("Удары " + hits + " и прочность инструмента " + perTool
                    + ": удары не отрицательны, прочность положительна");
        }
        return hits == 0 ? 0 : (hits + perTool - 1) / perTool;
    }
}
