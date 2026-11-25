package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WallRegionTests {
    @Test
    void of_ClosedRectangle_CoversInteriorAndWall() {
        ColumnMask region = WallRegion.of(List.of(
                new TownMap.WallSegment(0, 0, 10, 0),
                new TownMap.WallSegment(10, 0, 10, 10),
                new TownMap.WallSegment(10, 10, 0, 10),
                new TownMap.WallSegment(0, 10, 0, 0)));

        assertTrue(
                region.contains(5, 5) && region.contains(0, 0) && region.contains(10, 10) && !region.contains(11, 5),
                "Граница поселения — внутренняя область стены вместе с её линией: без этого участки и улицы "
                        + "внутри забора окажутся вне контура; получено " + region.count()
                        + " клеток Иначе тест of_ClosedRectangle_CoversInteriorAndWall перестанет ловить эту "
                        + "поломку. При смене of_ClosedRectangle_CoversInteriorAndWall проверь центр, угол и внешнюю клетку");
    }

    @Test
    void of_EmptyWall_Rejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> WallRegion.of(List.of()),
                "План без стены обязан отклоняться: границу поселения задаёт только забор; Иначе тест "
                        + "of_EmptyWall_Rejected перестанет ловить эту поломку. При смене of_EmptyWall_Rejected "
                        + "проверь отказ пустой стены");

        assertTrue(
                failure.getMessage().contains("нет стены"),
                "Отказ пустой стены обязан объяснять причину: иначе правку не исправить по тексту; получено "
                        + failure.getMessage()
                        + " Иначе тест of_EmptyWall_Rejected перестанет ловить эту поломку. При смене "
                        + "of_EmptyWall_Rejected проверь текст ошибки");
    }

    @Test
    void of_WallWithGap_Rejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> WallRegion.of(List.of(
                        new TownMap.WallSegment(0, 0, 10, 0),
                        new TownMap.WallSegment(10, 0, 10, 10),
                        new TownMap.WallSegment(10, 10, 0, 10))),
                "Стена с дырой обязана отклоняться: иначе внешняя вода считается поселением; Иначе тест "
                        + "of_WallWithGap_Rejected перестанет ловить эту поломку. При смене of_WallWithGap_Rejected "
                        + "проверь отказ незамкнутого контура");

        assertTrue(
                failure.getMessage().contains("не замкнута"),
                "Отказ дыры обязан называть незамкнутость: иначе правку не исправить по тексту; получено "
                        + failure.getMessage()
                        + " Иначе тест of_WallWithGap_Rejected перестанет ловить эту поломку. При смене "
                        + "of_WallWithGap_Rejected проверь текст ошибки");
    }

    @Test
    void of_DiamondWall_KeepsInsideSeparate() {
        ColumnMask region = WallRegion.of(List.of(
                new TownMap.WallSegment(5, 0, 10, 5),
                new TownMap.WallSegment(10, 5, 5, 10),
                new TownMap.WallSegment(5, 10, 0, 5),
                new TownMap.WallSegment(0, 5, 5, 0)));

        assertTrue(
                region.contains(5, 5) && !region.contains(1, 1) && !region.contains(9, 9),
                "Диагональный забор тоже держит границу: поток не должен протекать между клетками под 45°; "
                        + "получено " + region.count()
                        + " клеток Иначе тест of_DiamondWall_KeepsInsideSeparate перестанет ловить эту поломку. "
                        + "При смене of_DiamondWall_KeepsInsideSeparate проверь центр и внешние углы");
    }

    @Test
    void of_ObliqueSegment_Rejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> WallRegion.of(List.of(new TownMap.WallSegment(0, 0, 4, 7))),
                "Отрезок не по осям и не под 45° обязан отклоняться: из него не собрать границу; Иначе тест "
                        + "of_ObliqueSegment_Rejected перестанет ловить эту поломку. При смене of_ObliqueSegment_Rejected "
                        + "проверь отказ косого отрезка");

        assertTrue(
                failure.getMessage().contains("не прямой"),
                "Отказ косого отрезка обязан объяснять форму: иначе правку не исправить по тексту; получено "
                        + failure.getMessage()
                        + " Иначе тест of_ObliqueSegment_Rejected перестанет ловить эту поломку. При смене "
                        + "of_ObliqueSegment_Rejected проверь текст ошибки");
    }
}
