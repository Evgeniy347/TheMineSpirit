package com.theminespirit.core.repair;

import java.util.Objects;

public final class RepairCalculator {
    public static final int GRINDSTONE_BONUS_PERCENT = 5;
    public static final int ANVIL_REPAIR_PERCENT = 25;
    public static final int PERCENT_MIN = 1;
    public static final int PERCENT_MAX = 100;

    private RepairCalculator() {}

    public static int anvilResult(int durability, int maxDurability, int repairPercent) {
        if (durability < 0) {
            throw new IllegalArgumentException("Прочность не может быть отрицательной: " + durability);
        }
        if (maxDurability <= 0) {
            throw new IllegalArgumentException("Максимальная прочность должна быть положительной: " + maxDurability
                    + " — у предмета без прочности наковальня бессмысленна");
        }
        if (repairPercent < PERCENT_MIN || repairPercent > PERCENT_MAX) {
            throw new IllegalArgumentException("Доля ремонта " + repairPercent + "% вне диапазона [" + PERCENT_MIN
                    + ", " + PERCENT_MAX + "]: ноль не чинил бы, больше ста переполнило бы предел");
        }
        long restored = (long) maxDurability * repairPercent / 100;
        return (int) Math.min(durability + restored, maxDurability);
    }

    public static int grindstoneResult(int durabilityA, int durabilityB, int maxDurability) {
        if (durabilityA < 0 || durabilityB < 0) {
            throw new IllegalArgumentException("Прочность не может быть отрицательной: " + durabilityA + ", "
                    + durabilityB + " — сломанный предмет даёт ноль, а не минус");
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

    public static RepairMethod chooseMethod(RepairCosts costs, Availability availability) {
        boolean materialAvailable = availability.materialAvailable();
        boolean secondToolAvailable = availability.secondToolAvailable();
        boolean enchanted = availability.enchanted();
        boolean damagedEnough = availability.damagedEnough();
        Objects.requireNonNull(
                costs,
                "Стоимость способов ремонта не задана: без неё не выбрать, "
                        + "что дешевле — материал с опытом или второй инструмент");
        if (!damagedEnough) {
            return RepairMethod.NONE;
        }
        if (enchanted) {
            return materialAvailable ? RepairMethod.ANVIL : RepairMethod.NONE;
        }
        if (!materialAvailable && !secondToolAvailable) {
            return RepairMethod.NONE;
        }
        if (!materialAvailable) {
            return RepairMethod.GRINDSTONE;
        }
        if (!secondToolAvailable) {
            return RepairMethod.ANVIL;
        }
        return costs.anvilCost() <= costs.toolCost() ? RepairMethod.ANVIL : RepairMethod.GRINDSTONE;
    }

    public record Availability(
            boolean materialAvailable, boolean secondToolAvailable, boolean enchanted, boolean damagedEnough) {}
}
