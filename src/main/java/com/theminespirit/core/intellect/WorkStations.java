package com.theminespirit.core.intellect;

/**
 * Стойла и станции, без которых автозадача невыполнима: свободные кровати, места плавки,
 * верстак и запас на печь. Пустое поселение задают {@link #NONE}.
 */
public record WorkStations(int freeBeds, int smeltStations, int craftingTables, int furnaceItems, int cobblestone) {
    public static final WorkStations NONE = new WorkStations(0, 0, 0, 0, 0);
    public static final int FURNACE_COST = 8;

    public WorkStations {
        requireNonNegative("свободных кроватей", freeBeds);
        requireNonNegative("мест плавки", smeltStations);
        requireNonNegative("верстаков", craftingTables);
        requireNonNegative("печей в инвентаре", furnaceItems);
        requireNonNegative("булыжника", cobblestone);
    }

    public boolean canSleep() {
        return freeBeds > 0;
    }

    public boolean canSmelt() {
        return smeltStations > 0 || canCraftFurnace() || (furnaceItems > 0 && craftingTables > 0);
    }

    public boolean mustCraftFurnace() {
        return smeltStations == 0 && furnaceItems == 0 && canCraftFurnace();
    }

    private boolean canCraftFurnace() {
        return craftingTables > 0 && cobblestone >= FURNACE_COST;
    }

    private static void requireNonNegative(String what, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    what + " = " + value + ": счёт станций не может быть отрицательным, иначе гейт задач врёт");
        }
    }
}
