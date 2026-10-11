package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SectionCursorTests {
    @Test
    void next_CubeInsideOneSection_ReturnsOneSlice() {
        BlockBox box = new BlockBox(new BlockCoord(2, 2, 2), new BlockCoord(10, 10, 10));
        SectionCursor cursor = SectionCursor.of(box, new BlockCoord(6, 6, 6));

        List<SectionSlice> slices = cursor.next(10);

        assertEquals(1, slices.size(),
                "Куб внутри одной секции 16×16×16 обязан резаться на один срез: лишние срезы удваивают чтение блоков");
        assertEquals(box, slices.get(0).bounds(),
                "Срез единственной секции обязан совпадать с кубом: иначе часть куба не будет просмотрена");
        assertTrue(cursor.isDone(), "После выдачи единственного среза курсор обязан быть пуст");
    }

    @Test
    void next_CubeAcrossSectionBorder_ReturnsTwoSlicesCoveringCube() {
        BlockBox box = new BlockBox(new BlockCoord(12, 0, 0), new BlockCoord(19, 3, 3));
        SectionCursor cursor = SectionCursor.of(box, new BlockCoord(12, 0, 0));

        List<SectionSlice> slices = cursor.next(10);

        assertEquals(2, slices.size(), "Куб через границу секций по X обязан резаться на два среза");
        assertEquals(new BlockBox(new BlockCoord(12, 0, 0), new BlockCoord(15, 3, 3)), slices.get(0).bounds(),
                "Первым идёт срез с центром: его границы обрезаются по границе секции x=15");
        assertEquals(new BlockBox(new BlockCoord(16, 0, 0), new BlockCoord(19, 3, 3)), slices.get(1).bounds(),
                "Второй срез начинается ровно с x=16: щель или нахлёст дают пропуск или двойной подсчёт блоков");
    }

    @Test
    void next_NegativeCoordinates_UseFloorSections() {
        BlockBox box = new BlockBox(new BlockCoord(-2, 0, 0), new BlockCoord(1, 0, 0));
        SectionCursor cursor = SectionCursor.of(box, new BlockCoord(-2, 0, 0));

        List<SectionSlice> slices = cursor.next(10);

        assertEquals(List.of(-1, 0), slices.stream().map(SectionSlice::sectionX).toList(),
                "Блоки x=-2 и x=1 лежат в секциях -1 и 0: деление к нулю вместо floor склеило бы их в одну секцию");
    }

    @Test
    void next_CenterSliceTiesAndDistances_AreOrderedByDistanceThenXyz() {
        BlockBox box = new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(31, 0, 31));
        SectionCursor cursor = SectionCursor.of(box, new BlockCoord(15, 0, 15));

        List<SectionSlice> slices = cursor.next(10);

        assertEquals(List.of(0, 0, 1, 1), slices.stream().map(SectionSlice::sectionX).toList(),
                "Центр у угла четырёх секций: расстояния 0, 1, 1, 2, ничья двух средних решается по x");
        assertEquals(List.of(0, 1, 0, 1), slices.stream().map(SectionSlice::sectionZ).toList(),
                "При равном x ничья решается по z: порядок обязан быть стабильным между запусками");
        assertEquals(0L, slices.get(0).distanceSquared(new BlockCoord(15, 0, 15)),
                "Срез, содержащий центр, обязан идти первым с расстоянием 0");
    }

    @Test
    void next_PortionLimit_SplitsSlicesIntoPortions() {
        BlockBox box = new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(47, 0, 15));
        SectionCursor cursor = SectionCursor.of(box, new BlockCoord(0, 0, 0));

        assertEquals(2, cursor.next(2).size(), "Первая порция обязана отдать maxSlices срезов");
        assertEquals(1, cursor.remaining(), "После порции из двух в курсоре из трёх срезов остаётся один");
        assertEquals(1, cursor.next(2).size(), "Последняя порция обязана отдать остаток, а не пустое место");
        assertEquals(0, cursor.next(2).size(), "Пустой курсор обязан отдавать пустую порцию, а не падать");
    }

    @Test
    void next_ZeroPortionSize_IsRejected() {
        SectionCursor cursor = SectionCursor.of(new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(1, 1, 1)),
                new BlockCoord(0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> cursor.next(0),
                "Порция в 0 срезов обязана отвергаться: цикл скана с ней не закончился бы никогда");
    }

    @Test
    void blockBox_MinBeyondMax_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockBox(new BlockCoord(5, 0, 0), new BlockCoord(4, 0, 0)),
                "Куб с min правее max обязан отвергаться: его объём отрицателен и скан просмотрел бы ничего");
    }

    @Test
    void clampY_RangeOutsideCube_ReturnsEmpty() {
        BlockBox box = new BlockBox(new BlockCoord(0, 100, 0), new BlockCoord(3, 110, 3));

        assertEquals(true, box.clampY(-64, 50).isEmpty(),
                "Куб целиком выше высоты мира обязан давать пустой результат: иначе курсор получит куб с min > max");
        assertEquals(new BlockBox(new BlockCoord(0, 100, 0), new BlockCoord(3, 105, 3)),
                box.clampY(-64, 105).orElseThrow(),
                "Куб, выступающий над высотой мира, обязан обрезаться по ней");
    }
}
