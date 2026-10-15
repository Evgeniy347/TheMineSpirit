package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class PenBoundsTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);

    @Test
    void penLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(2, CatalogLimits.HERD_PEN_RADIUS_MIN,
                "Минимум радиуса загона в production обязан быть 2: теснее стадо давится");
        assertEquals(16, CatalogLimits.HERD_PEN_RADIUS_MAX,
                "Максимум радиуса загона в production обязан быть 16: шире загон не удержать");
    }

    @Test
    void inside_EdgeAndBeyond_MatchesRadius() {
        PenBounds pen = new PenBounds(CENTER, 5);

        assertTrue(pen.inside(new BlockCoord(3, 64, 4)),
                "Точка на краю (3-4-5) обязана считаться внутри: иначе крайних будут вечно возвращать");
        assertTrue(pen.inside(new BlockCoord(5, 64, 0)),
                "Точка ровно на радиусе обязана считаться внутри");
        assertTrue(!pen.inside(new BlockCoord(6, 64, 0)),
                "Точка на радиус+1 по оси обязана считаться вне: иначе отбившихся не заметят");
        assertTrue(!pen.inside(new BlockCoord(4, 64, 4)),
                "Точка за краем по диагонали обязана считаться вне");
    }

    @Test
    void nearestInside_OutsidePoint_ReturnsInsideAtSameHeight() {
        PenBounds pen = new PenBounds(CENTER, 5);
        BlockCoord outside = new BlockCoord(12, 70, 0);

        BlockCoord nearest = pen.nearestInside(outside);

        assertTrue(pen.inside(nearest),
                "Возврат обязан вести внутрь загона: иначе дух уведёт отбившегося ещё дальше");
        assertEquals(70, nearest.y(),
                "Возврат держит высоту животного: загон считается по горизонтали");
        assertEquals(nearest, pen.nearestInside(nearest),
                "Возврат обязан быть идемпотентным: точку внутри он не двигает");
    }

    @Test
    void nearestInside_WithMargin_ReturnsAtLeastMarginInsideEdge() {
        PenBounds pen = new PenBounds(CENTER, 5);
        BlockCoord outside = new BlockCoord(12, 64, 0);

        BlockCoord returned = pen.nearestInside(outside, 2);

        assertEquals(new BlockCoord(3, 64, 0), returned,
                "Возврат с отступом 2 обязан встать на расстоянии 5-2=3 от центра: на кромке radius=5 овца "
                        + "выходит обратно первым шагом, и приманка не заканчивается");
        assertEquals(returned, pen.nearestInside(returned, 2),
                "Точка, уже лежащая глубже отступа, не двигается: иначе возврат дёргает животное по кругу");
    }

    @Test
    void lureTarget_SmallestPenWithInsetOverRadius_StaysInsideWithoutThrowing() {
        PenBounds pen = new PenBounds(CENTER, CatalogLimits.HERD_PEN_RADIUS_MIN);
        BlockCoord outside = new BlockCoord(12, 64, 0);

        BlockCoord returned = pen.lureTarget(outside, 2);

        assertEquals(new BlockCoord(1, 64, 0), returned,
                "В самом тесном загоне отступ 2 не помещается: точка возврата обязана сжаться до radius-1 от "
                        + "кромки, а не уронить приманку исключением");
    }

    @Test
    void nearestInside_MarginOutOfRange_IsRejected() {
        PenBounds pen = new PenBounds(CENTER, 5);
        assertThrows(IllegalArgumentException.class, () -> pen.nearestInside(new BlockCoord(12, 64, 0), -1),
                "Отрицательный отступ отвергается: он двигал бы цель наружу загона");
        assertThrows(IllegalArgumentException.class, () -> pen.nearestInside(new BlockCoord(12, 64, 0), 5),
                "Отступ во весь радиус отвергается: внутри загона не остаётся места");
    }

    @Test
    void escapedOf_MixedHerd_ListsOnlyOutsiders() {
        PenBounds pen = new PenBounds(CENTER, 5);
        AnimalRef inside = new AnimalRef("in", Species.COW, false, true, false, new BlockCoord(1, 64, 1), true);
        AnimalRef edge = new AnimalRef("edge", Species.SHEEP, false, true, true, new BlockCoord(3, 64, 4), true);
        AnimalRef out = new AnimalRef("out", Species.PIG, false, true, false, new BlockCoord(8, 64, 8), false);

        List<AnimalRef> escaped = pen.escapedOf(List.of(inside, edge, out));

        assertEquals(List.of(out), escaped,
                "Вне загона обязан числиться только вышедший: край загона — ещё дом, иначе приманка не кончится");
    }

    @Test
    void escapedOf_EmptyHerd_ReturnsEmpty() {
        assertEquals(List.of(), new PenBounds(CENTER, 5).escapedOf(List.of()),
                "Пустое стадо обязано давать пустой список беглецов: иначе дух погонится за призраками");
    }

    @Test
    void pen_RadiusOutsideLimits_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PenBounds(CENTER, 1),
                "Загон радиуса 1 обязан отвергаться: стадо в нём давится");
        assertThrows(IllegalArgumentException.class, () -> new PenBounds(CENTER, 17),
                "Загон радиуса 17 обязан отвергаться: такой загон духу не удержать");
    }

    @Property
    void nearestInside_AnyOutsidePoint_LandsInside(@ForAll @IntRange(min = -40, max = 40) int dx,
            @ForAll @IntRange(min = -40, max = 40) int dz) {
        PenBounds pen = new PenBounds(CENTER, 5);

        BlockCoord nearest = pen.nearestInside(new BlockCoord(dx, 64, dz));

        assertTrue(pen.inside(nearest),
                "Возврат из (" + dx + ", " + dz + ") обязан приземлиться внутри загона: иначе дух уведёт "
                        + "отбившегося ещё дальше");
    }

    @Property
    void nearestInside_AnyOutsidePointWithMargin_LandsAtLeastMarginInside(@ForAll @IntRange(min = -40, max = 40) int dx,
            @ForAll @IntRange(min = -40, max = 40) int dz) {
        PenBounds pen = new PenBounds(CENTER, 5);

        BlockCoord returned = pen.nearestInside(new BlockCoord(dx, 64, dz), 2);

        long offsetX = (long) returned.x() - CENTER.x();
        long offsetZ = (long) returned.z() - CENTER.z();
        assertTrue(offsetX * offsetX + offsetZ * offsetZ <= 3L * 3L,
                "Возврат с отступом 2 из (" + dx + ", " + dz + ") обязан лежать не дальше 5-2=3 от центра: "
                        + "точка на кромке позволила бы животному снова выйти, а приманка не завершилась бы");
    }
}
