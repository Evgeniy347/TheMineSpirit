package com.theminespirit.neoforge;

import java.util.Map;

final class ChopTreeSaved {
    private static final String CHOPPED_KEY = "chopped";
    private static final String STANDING_KEY = "standingLogs";
    private static final String PROTECTED_KEY = "protectedLogs";
    private static final String PLANTED_KEY = "plantedSaplings";
    private static final String SKIPPED_KEY = "skippedCells";
    private static final String LACKED_KEY = "lackedSaplings";

    record Values(
            int chopped,
            int standingLogs,
            int protectedLogs,
            int plantedSaplings,
            int skippedCells,
            boolean lackedSaplings) {}

    private ChopTreeSaved() {}

    static Map<String, Object> save(Values values) {
        return Map.of(
                CHOPPED_KEY,
                values.chopped(),
                STANDING_KEY,
                values.standingLogs(),
                PROTECTED_KEY,
                values.protectedLogs(),
                PLANTED_KEY,
                values.plantedSaplings(),
                SKIPPED_KEY,
                values.skippedCells(),
                LACKED_KEY,
                values.lackedSaplings());
    }

    static Values restore(Map<String, Object> state, int countBound, Values previous) {
        int chopped = readCount(state, CHOPPED_KEY, countBound, previous.chopped());
        int standingLogs = readCount(state, STANDING_KEY, Integer.MAX_VALUE, previous.standingLogs());
        int protectedLogs = readCount(state, PROTECTED_KEY, Integer.MAX_VALUE, previous.protectedLogs());
        int plantedSaplings = readCount(state, PLANTED_KEY, Integer.MAX_VALUE, previous.plantedSaplings());
        int skippedCells = readCount(state, SKIPPED_KEY, Integer.MAX_VALUE, previous.skippedCells());
        boolean lackedSaplings = previous.lackedSaplings();
        Object lacked = state.get(LACKED_KEY);
        if (lacked != null) {
            if (!(lacked instanceof Boolean flag)) {
                throw new IllegalArgumentException("Состояние chop_tree: '" + LACKED_KEY + "' = " + lacked
                        + ", а ожидалось булево значение: снимок задачи повреждён или записан другой версией мода, "
                        + "проверьте его");
            }
            lackedSaplings = flag;
        }
        return new Values(chopped, standingLogs, protectedLogs, plantedSaplings, skippedCells, lackedSaplings);
    }

    private static int readCount(Map<String, Object> state, String key, int bound, int previous) {
        Object saved = state.get(key);
        if (saved == null) {
            return previous;
        }
        if (!(saved instanceof Number number) || number.intValue() < 0 || number.intValue() > bound) {
            throw new IllegalArgumentException("Состояние chop_tree: '" + key + "' = " + saved
                    + ", а ожидалось целое число от 0 до " + bound
                    + ": снимок задачи повреждён или записан другой версией мода, проверьте его");
        }
        return number.intValue();
    }
}
