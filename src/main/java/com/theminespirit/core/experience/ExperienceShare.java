package com.theminespirit.core.experience;

/**
 * Делит опыт духа между реальными игроками поровну.
 *
 * <p>Остаток от деления раздаётся по одному первым игрокам в стабильном порядке (порядок задаёт
 * вызывающий — сортировка по UUID). Сумма долей всегда равна исходной величине: опыт не теряется.
 */
public final class ExperienceShare {
    private ExperienceShare() {
    }

    public static int[] split(int amount, int players) {
        if (amount < 0) {
            throw new IllegalArgumentException("Опыт " + amount + " отрицательный: делить нечего, "
                    + "нулевой опыт задают нулём");
        }
        if (players < 0) {
            throw new IllegalArgumentException("Число игроков " + players + " отрицательное: "
                    + "пустой список игроков задают нулём");
        }
        if (players == 0) {
            return new int[0];
        }
        int base = amount / players;
        int remainder = amount % players;
        int[] shares = new int[players];
        for (int i = 0; i < players; i++) {
            shares[i] = base + (i < remainder ? 1 : 0);
        }
        return shares;
    }
}