package com.theminespirit.core.repair;

public final class RepairCalculator {
    private RepairCalculator() {
    }

    public static final int GRINDSTONE_BONUS_PERCENT = 5;

    public static int grindstoneResult(int durabilityA, int durabilityB, int maxDurability) {
        if (durabilityA < 0 || durabilityB < 0) {
            throw new IllegalArgumentException("Прочность не может быть отрицательной: "
                    + durabilityA + ", " + durabilityB + " — сломанный предмет даёт ноль, а не минус");
        }
        if (maxDurability <= 0) {
            throw new IllegalArgumentException("Максимальная прочность должна быть положительной: " + maxDurability
                    + " — у предмета без прочности точило бессмысленно");
        }
        int bonus = (maxDurability * GRINDSTONE_BONUS_PERCENT + 99) / 100;
        long combined = (long) durabilityA + durabilityB + bonus;
        return (int) Math.min(combined, maxDurability);
    }

    public static boolean damagedEnoughToRepair(int durability, int maxDurability, int maxDurabilityPercent) {
        if (maxDurability <= 0) {
            throw new IllegalArgumentException("Максимальная прочность должна быть положительной: " + maxDurability);
        }
        return (long) durability * 100 <= (long) maxDurabilityPercent * maxDurability;
    }

    public static RepairMethod chooseMethod(int identicalDamagedCount, boolean enchanted,
            int bestDurability, int maxDurability, int maxDurabilityPercent) {
        if (enchanted) {
            return RepairMethod.NONE;
        }
        if (identicalDamagedCount < 2) {
            return RepairMethod.NONE;
        }
        if (!damagedEnoughToRepair(bestDurability, maxDurability, maxDurabilityPercent)) {
            return RepairMethod.NONE;
        }
        return RepairMethod.GRINDSTONE;
    }
}
