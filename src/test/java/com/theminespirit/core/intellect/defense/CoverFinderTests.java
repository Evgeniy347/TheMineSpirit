package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoverFinderTests {
    private static final Point SELF = new Point(0, 64, 0);
    private static final Point THREAT = new Point(10, 64, 0);
    private static final double CORRIDOR = 1.5;

    @Test
    void between_MobOnLine_Chosen() {
        Optional<Point> cover = CoverFinder.between(SELF, THREAT,
                List.of(new Point(5, 64, 0), new Point(5, 64, 5)), CORRIDOR);

        assertEquals(new Point(5, 64, 0), cover.orElseThrow(),
                "Моб на линии выбирается укрытием: иначе приём «зомби между» не сработает и стрелы пойдут в духа");
    }

    @Test
    void between_OffCorridor_Rejected() {
        assertEquals(Optional.empty(), CoverFinder.between(SELF, THREAT, List.of(new Point(5, 64, 5)), CORRIDOR),
                "Кандидат вне коридора не укрытие: иначе укрытием посчитается моб мимо линии обстрела");
    }

    @Test
    void between_BehindThreat_Rejected() {
        assertEquals(Optional.empty(), CoverFinder.between(SELF, THREAT, List.of(new Point(12, 64, 0)), CORRIDOR),
                "Кандидат за целью не укрытие: иначе дух прикроется тем, кто стоит за спиной у скелета");
    }

    @Test
    void between_BehindSelf_Rejected() {
        assertEquals(Optional.empty(), CoverFinder.between(SELF, THREAT, List.of(new Point(-2, 64, 0)), CORRIDOR),
                "Кандидат за спиной духа не укрытие: иначе проекция вырождается и линия обстрела открыта");
    }

    @Test
    void between_EmptyBlockers_ReturnsEmpty() {
        assertEquals(Optional.empty(), CoverFinder.between(SELF, THREAT, List.of(), CORRIDOR),
                "Без кандидатов укрытия нет: иначе боец решит, что прикрыт, стоя в чистом поле");
    }

    @Test
    void between_NullBlockers_Throws() {
        assertThrows(IllegalArgumentException.class, () -> CoverFinder.between(SELF, THREAT, null, CORRIDOR),
                "Поиск без списка кандидатов обязан падать: иначе молчаливое отсутствие укроет от проверки");
    }

    @Test
    void between_NullSelf_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CoverFinder.between(null, THREAT, List.of(new Point(5, 64, 0)), CORRIDOR),
                "Поиск без позиции духа обязан падать: иначе линия строится из ничего");
    }

    @Test
    void between_SmallerAcross_Wins() {
        Optional<Point> cover = CoverFinder.between(SELF, THREAT,
                List.of(new Point(5, 64, 1), new Point(6, 64, 0.5)), CORRIDOR);

        assertEquals(new Point(6, 64, 0.5), cover.orElseThrow(),
                "Из подходящих выбирается ближайшее к линии: иначе стрела пройдёт мимо дальнего укрытия");
    }

    @Test
    void between_EqualAcross_NearerSelfWins() {
        Optional<Point> cover = CoverFinder.between(SELF, THREAT,
                List.of(new Point(8, 64, 1), new Point(5, 64, 1)), CORRIDOR);

        assertEquals(new Point(5, 64, 1), cover.orElseThrow(),
                "При равном сносе выбирается ближнее к духу: иначе боец тянется к дальнему укрытию под обстрелом");
    }

    @Test
    void between_SymmetricCandidates_LowerCoordinatesWin() {
        Optional<Point> cover = CoverFinder.between(SELF, THREAT,
                List.of(new Point(5, 64, 1), new Point(5, 64, -1)), CORRIDOR);

        assertEquals(new Point(5, 64, -1), cover.orElseThrow(),
                "Полная ничья решается координатами: иначе выбор укрытия мигает между пересборками");
    }

    @Test
    void between_SelfEqualsThreat_ReturnsEmpty() {
        assertEquals(Optional.empty(),
                CoverFinder.between(SELF, SELF, List.of(new Point(5, 64, 0)), CORRIDOR),
                "Без направления линии укрытия нет: иначе деление на ноль даст ложного кандидата");
    }
}
