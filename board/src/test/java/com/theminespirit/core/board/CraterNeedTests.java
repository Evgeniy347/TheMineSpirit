package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class CraterNeedTests {

    @Test
    void estimate_ZeroRadius_ZeroBlocks() {
        Map<String, Integer> estimate = CraterNeed.estimate(new Point(0, 0), 0);

        assertEquals(
                Map.of(),
                estimate,
                "Нулевой радиус — ноль блоков: взрыв без радиуса ничего не выкопал, "
                        + "и задание о засыпке не ставится");
    }

    @Test
    void estimate_CreeperRadius_DirtBlocks() {
        Map<String, Integer> estimate = CraterNeed.estimate(new Point(100, -50), 3);

        assertEquals(
                Map.of("dirt", 29),
                estimate,
                "Воронка крипера радиусом 3 — 29 блоков земли (ceil(π·9)): доска ставит "
                        + "гейт ровно на это число, не больше и не меньше");
    }

    @Test
    void estimate_Monotone_ByRadius() {
        Map<String, Integer> small = CraterNeed.estimate(new Point(0, 0), 2);
        Map<String, Integer> large = CraterNeed.estimate(new Point(0, 0), 4);

        assertTrue(
                large.get("dirt") >= small.get("dirt"),
                "Оценка обязана быть монотонной по радиусу: большая воронка требует не "
                        + "меньше земли, иначе засыпка оставляет яму");
    }

    @Test
    void estimate_NullBlast_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CraterNeed.estimate(null, 3),
                "Точка взрыва обязана быть задана: без неё неизвестен центр воронки");
    }

    @Test
    void estimate_NegativeRadius_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CraterNeed.estimate(new Point(0, 0), -1),
                "Отрицательный радиус обязан отвергаться: он прошёл бы как «взрыв есть, "
                        + "а воронки нет» и скрыл бы реальный радиус от наблюдателя");
    }

    @Test
    void outsideTerritory_BlastInside_NoTask() {
        List<Point> homes = List.of(new Point(0, 0), new Point(32, 32));
        boolean outside = CraterNeed.outsideTerritory(new Point(10, 10), homes, 64);

        assertFalse(
                outside,
                "Взрыв в центре поселения — внутри территории: задания доски не ставится, "
                        + "воронка в зоне работ координатора");
    }

    @Test
    void outsideTerritory_BlastOutside_Task() {
        List<Point> homes = List.of(new Point(0, 0), new Point(32, 32));
        boolean outside = CraterNeed.outsideTerritory(new Point(200, 200), homes, 64);

        assertTrue(
                outside,
                "Взрыв за радиусом от всех домов — задание есть: доска ставит FILL_CRATER "
                        + "с числом блоков, иначе воронка за территорией живёт вечно");
    }

    @Test
    void outsideTerritory_EmptyHomes_Task() {
        boolean outside = CraterNeed.outsideTerritory(new Point(500, 500), List.of(), 64);

        assertTrue(outside, "Нет домов — нет территории: любая воронка за её пределами, задание ставится");
    }

    @Test
    void outsideTerritory_NullHomes_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CraterNeed.outsideTerritory(new Point(0, 0), null, 64),
                "Список домов=null обязан отвергаться: пустую территорию задают пустым списком");
    }

    @Test
    void outsideTerritory_Is_Deterministic() {
        List<Point> homes = List.of(new Point(0, 0));

        assertEquals(
                CraterNeed.outsideTerritory(new Point(100, 0), homes, 64),
                CraterNeed.outsideTerritory(new Point(100, 0), homes, 64),
                "Признак «за территорией» обязан быть детерминирован: по нему доска "
                        + "ставит или не ставит задание, нестабильность — случайное решение");
    }

    @Property
    void estimate_NonNegative_Monotone(
            @ForAll("blastPoints") Point blast,
            @ForAll("radii") double smallRadius,
            @ForAll("radii") double largeRadius) {
        double first = Math.min(smallRadius, largeRadius);
        double second = Math.max(smallRadius, largeRadius);
        Map<String, Integer> small = CraterNeed.estimate(blast, first);
        Map<String, Integer> large = CraterNeed.estimate(blast, second);

        assertTrue(
                small.getOrDefault("dirt", 0) >= 0,
                "Оценка обязана быть неотрицательной: отрицательное число блоков сломало бы гейт");
        assertTrue(
                large.getOrDefault("dirt", 0) >= small.getOrDefault("dirt", 0),
                "Оценка обязана быть монотонной по радиусу: иначе меньшая воронка "
                        + "потребовала бы больше земли, чем большая");
    }

    @Property
    void outsideTerritory_Deterministic_HomeSensitive(
            @ForAll("blastPoints") Point blast, @ForAll("homes") List<Point> homes, @ForAll("radii") double radius) {
        assertEquals(
                CraterNeed.outsideTerritory(blast, homes, radius),
                CraterNeed.outsideTerritory(blast, homes, radius),
                "Признак «за территорией» детерминирован: иначе наблюдатель видит "
                        + "разное решение при одних и тех же домах и взрыве");
    }

    @Provide
    Arbitrary<Point> blastPoints() {
        return Arbitraries.of(new Point(0, 0), new Point(10, 10), new Point(200, 200), new Point(-300, 150));
    }

    @Provide
    Arbitrary<List<Point>> homes() {
        return Arbitraries.of(
                List.of(), List.of(new Point(0, 0)), List.of(new Point(0, 0), new Point(32, 32), new Point(-50, 20)));
    }

    @Provide
    Arbitrary<Double> radii() {
        return Arbitraries.doubles().between(0, 200);
    }
}
