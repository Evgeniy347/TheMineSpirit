package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class PenBoundsTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);

    @Test
    void penLimits_ProductionConstants_MatchAgreedBounds() {
        assertEquals(
                2,
                CatalogLimits.HERD_PEN_RADIUS_MIN,
                "Минимум радиуса загона в production обязан быть 2: иначе теснее стадо давится, животные не "
                        + "расходятся и кормление проходит впустую. При смене CatalogLimits.HERD_PEN_RADIUS_MIN проверь "
                        + "pen_RadiusOutsideLimits_IsRejected и живой загон минимального размера");
        assertEquals(
                16,
                CatalogLimits.HERD_PEN_RADIUS_MAX,
                "Максимум радиуса загона в production обязан быть 16: иначе шире загон не удержать, дух не "
                        + "успевает обойти стадо и беглецы теряются. При смене HERD_PEN_RADIUS_MAX проверь отказ на 17 и "
                        + "живой обход загона предельного размера");
    }

    @Test
    void inside_EdgeAndBeyond_MatchesRadius() {
        PenBounds pen = new PenBounds(CENTER, 5);

        assertTrue(
                pen.inside(new BlockCoord(3, 64, 4)),
                "Точка на краю (3-4-5) обязана считаться внутри: иначе крайних животных будут вечно возвращать "
                        + "приманкой, и цикл возврата не закончится. При смене PenBounds.inside проверь соседнюю "
                        + "точку ровно на радиусе и диагональ за краем");
        assertTrue(
                pen.inside(new BlockCoord(5, 64, 0)),
                "Точка ровно на радиусе обязана считаться внутри: иначе животное на кромке считается беглецом "
                        + "и приманка тратится впустую. При смене PenBounds.inside проверь пару 5 и 6 по оси");
        assertTrue(
                !pen.inside(new BlockCoord(6, 64, 0)),
                "Точка на радиус+1 по оси обязана считаться вне: иначе отбившихся не заметят, они уйдут из "
                        + "загона и стадо разбредётся. При смене PenBounds.inside проверь соседнюю точку на радиусе");
        assertTrue(
                !pen.inside(new BlockCoord(4, 64, 4)),
                "Точка за краем по диагонали обязана считаться вне: обход угла не должен маскировать беглеца, "
                        + "иначе его не вернут. При смене PenBounds.inside проверь диагональную точку у самого края");
    }

    @Test
    void nearestInside_OutsidePoint_ReturnsInsideAtSameHeight() {
        PenBounds pen = new PenBounds(CENTER, 5);
        BlockCoord outside = new BlockCoord(12, 70, 0);

        BlockCoord nearest = pen.nearestInside(outside);

        assertTrue(
                pen.inside(nearest),
                "Возврат обязан вести внутрь загона: иначе дух уведёт отбившегося ещё дальше, и приманка "
                        + "закончится без результата. При смене PenBounds.nearestInside проверь property "
                        + "nearestInside_AnyOutsidePoint_LandsInside");
        assertEquals(
                70,
                nearest.y(),
                "Возврат держит высоту животного: загон считается по горизонтали, иначе приманка встанет на "
                        + "чужой высоте и животное не пойдёт. При смене PenBounds.nearestInside проверь точку на "
                        + "другой высоте и живую приманку в холмистом загоне");
        assertEquals(
                nearest,
                pen.nearestInside(nearest),
                "Возврат обязан быть идемпотентным: точку внутри он не двигает, иначе дух дёргает животное по "
                        + "кругу и возврат не сходится. При смене PenBounds.nearestInside проверь двойной вызов "
                        + "на одной точке");
    }

    @Test
    void nearestInside_WithMargin_ReturnsAtLeastMarginInsideEdge() {
        PenBounds pen = new PenBounds(CENTER, 5);
        BlockCoord outside = new BlockCoord(12, 64, 0);

        BlockCoord returned = pen.nearestInside(outside, 2);

        assertEquals(
                new BlockCoord(3, 64, 0),
                returned,
                "Возврат с отступом 2 обязан встать на расстоянии 5-2=3 от центра: иначе на кромке radius=5 "
                        + "овца выходит обратно первым шагом, и приманка не заканчивается. При смене отступа в "
                        + "PenBounds.nearestInside проверь property nearestInside_AnyOutsidePointWithMargin");
        assertEquals(
                returned,
                pen.nearestInside(returned, 2),
                "Точка, уже лежащая глубже отступа, не двигается: иначе возврат дёргает животное по кругу и "
                        + "приманка тратится. При смене PenBounds.nearestInside проверь двойной вызов с отступом");
    }

    @Test
    void lureTarget_SmallestPenWithInsetOverRadius_StaysInsideWithoutThrowing() {
        PenBounds pen = new PenBounds(CENTER, CatalogLimits.HERD_PEN_RADIUS_MIN);
        BlockCoord outside = new BlockCoord(12, 64, 0);

        BlockCoord returned = pen.lureTarget(outside, 2);

        assertEquals(
                new BlockCoord(1, 64, 0),
                returned,
                "В самом тесном загоне отступ 2 не помещается: иначе точка возврата обязана сжаться до radius-1 "
                        + "от кромки, а не уронить приманку исключением. При смене PenBounds.lureTarget проверь "
                        + "минимальный радиус 2 и живой возврат овцы в тесном загоне");
    }

    @Test
    void nearestInside_MarginOutOfRange_IsRejected() {
        PenBounds pen = new PenBounds(CENTER, 5);
        assertThrows(
                IllegalArgumentException.class,
                () -> pen.nearestInside(new BlockCoord(12, 64, 0), -1),
                "Отрицательный отступ отвергается: иначе он двигал бы цель наружу загона, и приманка уводила бы "
                        + "животное из загона. При смене проверки отступа в PenBounds.nearestInside проверь "
                        + "границу 0 и -1");
        assertThrows(
                IllegalArgumentException.class,
                () -> pen.nearestInside(new BlockCoord(12, 64, 0), 5),
                "Отступ во весь радиус отвергается: иначе внутри загона не остаётся места, и приманка встала бы "
                        + "в центр без запаса. При смене проверки отступа в PenBounds.nearestInside проверь "
                        + "границы radius и radius-1");
    }

    @Test
    void escapedOf_MixedHerd_ListsOnlyOutsiders() {
        PenBounds pen = new PenBounds(CENTER, 5);
        AnimalRef inside = new AnimalRef("in", Species.COW, false, true, false, new BlockCoord(1, 64, 1), true);
        AnimalRef edge = new AnimalRef("edge", Species.SHEEP, false, true, true, new BlockCoord(3, 64, 4), true);
        AnimalRef out = new AnimalRef("out", Species.PIG, false, true, false, new BlockCoord(8, 64, 8), false);

        List<AnimalRef> escaped = pen.escapedOf(List.of(inside, edge, out));

        assertEquals(
                List.of(out),
                escaped,
                "Вне загона обязан числиться только вышедший: край загона — ещё дом, иначе приманка не кончится "
                        + "и дух будет возвращать всё стадо. При смене PenBounds.escapedOf проверь соседний "
                        + "тест границы края и живой возврат выбежавшей свиньи");
    }

    @Test
    void escapedOf_EmptyHerd_ReturnsEmpty() {
        assertEquals(
                List.of(),
                new PenBounds(CENTER, 5).escapedOf(List.of()),
                "Пустое стадо обязано давать пустой список беглецов: иначе дух погонится за призраками и "
                        + "потратит цикл фермы на пустой загон. При смене PenBounds.escapedOf проверь пустой "
                        + "список и загон без животных на живом сервере");
    }

    @Test
    void pen_RadiusOutsideLimits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PenBounds(CENTER, 1),
                "Загон радиуса 1 обязан отвергаться: иначе стадо в нём давится, животные не расходятся и "
                        + "кормление идёт впустую. При смене проверки в конструкторе PenBounds проверь границы 1 и 2");
        assertThrows(
                IllegalArgumentException.class,
                () -> new PenBounds(CENTER, 17),
                "Загон радиуса 17 обязан отвергаться: иначе такой загон духу не удержать, беглецы теряются и "
                        + "план стада не закрывается. При смене проверки в конструкторе PenBounds проверь границы 16 и 17");
    }

    @Property
    void nearestInside_AnyOutsidePoint_LandsInside(
            @ForAll @IntRange(min = -40, max = 40) int dx, @ForAll @IntRange(min = -40, max = 40) int dz) {
        PenBounds pen = new PenBounds(CENTER, 5);

        BlockCoord nearest = pen.nearestInside(new BlockCoord(dx, 64, dz));

        assertTrue(
                pen.inside(nearest),
                "Возврат из (" + dx + ", " + dz + ") обязан приземлиться внутри загона: иначе дух уведёт "
                        + "отбившегося ещё дальше, и приманка закончится без результата. При смене "
                        + "PenBounds.nearestInside проверь фиксированные точки (12,0) и (5,0) из соседних тестов");
    }

    @Property
    void nearestInside_AnyOutsidePointWithMargin_LandsAtLeastMarginInside(
            @ForAll @IntRange(min = -40, max = 40) int dx, @ForAll @IntRange(min = -40, max = 40) int dz) {
        PenBounds pen = new PenBounds(CENTER, 5);

        BlockCoord returned = pen.nearestInside(new BlockCoord(dx, 64, dz), 2);

        long offsetX = (long) returned.x() - CENTER.x();
        long offsetZ = (long) returned.z() - CENTER.z();
        assertTrue(
                offsetX * offsetX + offsetZ * offsetZ <= 3L * 3L,
                "Возврат с отступом 2 из (" + dx + ", " + dz + ") обязан лежать не дальше 5-2=3 от центра: "
                        + "точка на кромке позволила бы животному снова выйти, иначе приманка не завершилась бы. "
                        + "При смене отступа проверь фиксированный случай (12,0) из соседнего теста");
    }
}
