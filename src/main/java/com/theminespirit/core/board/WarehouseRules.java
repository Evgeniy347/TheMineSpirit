package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class WarehouseRules {
    public static final int MAX_LEVEL = 4;
    public static final int LAST_LEVEL = 4;
    public static final int MAX_INTERMEDIATE = 3;

    private WarehouseRules() {}

    public static int nextLevel(Collection<Integer> standing) {
        Objects.requireNonNull(standing, "Список стоящих складов не может быть null: уровни берутся из него");
        for (int level = 1; level <= MAX_LEVEL; level++) {
            if (!standing.contains(level)) {
                return level;
            }
        }
        return 0;
    }

    public static boolean stageOpen(int settlementStage, boolean stageOneBusy) {
        if (settlementStage < 1) {
            throw new IllegalArgumentException("Этап " + settlementStage + " вне поселения: этапы считаются с первого");
        }
        return !stageOneBusy || settlementStage >= 2;
    }

    public static boolean canBuild(int level, Collection<Integer> standing) {
        Objects.requireNonNull(standing, "Список стоящих складов не может быть null: лимит считается по нему");
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + MAX_LEVEL + ": другого уровня постройки нет");
        }
        if (level == LAST_LEVEL) {
            return true;
        }
        int intermediate = 0;
        for (int existing : standing) {
            if (existing > 0 && existing < LAST_LEVEL) {
                intermediate++;
            }
        }
        return intermediate < MAX_INTERMEDIATE;
    }

    public static List<Integer> relocateOldLevels(Collection<Integer> standing) {
        Objects.requireNonNull(standing, "Список стоящих складов не может быть null: без него нечего переносить");
        if (standing.isEmpty()) {
            return List.of();
        }
        int highest = standing.stream().max(Comparator.naturalOrder()).orElseThrow();
        List<Integer> old = new ArrayList<>();
        for (int level : new TreeSet<>(standing)) {
            if (level > 0 && level < highest) {
                old.add(level);
            }
        }
        return List.copyOf(old);
    }

    public static BlockCoord site(BlockCoord home, int level) {
        Objects.requireNonNull(home, "Дом не может быть null: склад ставится от него");
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + MAX_LEVEL + ": места для такого уровня нет");
        }
        return home;
    }

    public static String scheme(int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + MAX_LEVEL + ": схемы для него нет");
        }
        return "storage_v" + level;
    }

    public static String buildTicketId(int level) {
        return "warehouse_" + level;
    }

    public static String relocateTicketId(int oldLevel) {
        return "warehouse_relocate_" + oldLevel;
    }
}
