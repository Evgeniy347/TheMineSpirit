package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownStreetIdTests {

    private static final ColumnMask REGION = ColumnMask.rect(-30, -5, 30, 40);

    private static TownStreets streetsOf(TownMap.MapStreet... streets) {
        TownMap map = new TownMap(
                new TownMap.MapPoint(0, 0),
                List.of(new TownMap.MapPlot("hut", "hut", -10, 20, 5, 35, "south")),
                List.of(streets),
                List.of());
        return TownMapRules.streets(map, REGION);
    }

    @Test
    void townStreets_TwoRectsPerKind_CellIdResolvesAndMasksStayUnion() {
        TownStreets streets = streetsOf(
                new TownMap.MapStreet("s-1", TownMapRules.MAIN, -20, 0, 20, 4),
                new TownMap.MapStreet("s-2", TownMapRules.SIDE, 0, 10, 4, 30));

        assertEquals(
                List.of("s-1", "s-2"),
                streets.ids(),
                "План обязан помнить id улиц в порядке карты: по ним аудит дробит улицу на группы по чанкам, "
                        + "иначе «улица до склада» и «улица до сада» слипнутся в один узел графа и порядок "
                        + "лестницы рассыплется. При смене порядка улиц перепроверь TownDrawioParser и веса "
                        + "graph.d/audit.json (block 8).");
        assertEquals(
                "s-1",
                streets.idAt(0, 2).orElseThrow(),
                "Клетка плитки отвечает своим id, а не «главная улица»: без этого группа улицы потеряет адрес, "
                        + "и приоритет дороги к складу не выдержать. При смене разрешения клетки перепроверь "
                        + "порядок обхода named в TownStreets.idAt.");
        assertEquals(
                "s-2",
                streets.idAt(2, 15).orElseThrow(),
                "Боковой проулок остаётся отдельной улицей: иначе его плитки попадут в группу главной улицы, "
                        + "и дух бросит дорогу на полпути. При смене вида улицы перепроверь Street.isMain и "
                        + "разбиение масок в TownMapRules.mask.");
        assertTrue(
                streets.main().contains(0, 2) && !streets.side().contains(0, 2),
                "Маски main/side остаются объединением именованных прямоугольников: на них держатся "
                        + "Walkways, StreetLayout и ворота стены, иначе проёмы в ограде встанут не там, где "
                        + "кончались раньше. При смене построения масок перепроверь TownMapRules.wallLine.");
    }

    @Test
    void townStreets_RectangleOutsideRegion_ClipsCellsAndKeepsOutsideUnknown() {
        TownStreets streets = streetsOf(new TownMap.MapStreet("s-9", TownMapRules.SIDE, 25, 20, 40, 25));

        assertEquals(
                "s-9",
                streets.idAt(26, 22).orElseThrow(),
                "Улица, упирающаяся в границу посёлка, существует только внутри неё: без обрезки группа "
                        + "запросит клетки за контуром, иначе дух "
                        + "уйдёт строить дорогу в чужом регионе. При смене обрезки "
                        + "перепроверь TownMapRules.clip.");
        assertFalse(
                streets.idAt(35, 22).isPresent(),
                "Клетка вне контура не имеет улицы: иначе аудит выдаст BREAK за границей поселения, и чужая "
                        + "постройка игрока пойдёт под снос. При смене контура перепроверь ColumnMask.contains "
                        + "и TownMapCheck.");
    }

    @Test
    void townStreets_LegacyMasksWithoutIds_FallBackToKindNames() {
        TownStreets legacy = new TownStreets(ColumnMask.rect(0, 0, 5, 5), ColumnMask.rect(6, 0, 10, 10));

        assertEquals(
                TownMapRules.MAIN,
                legacy.idAt(1, 1).orElseThrow(),
                "Старый план без именованных улиц читается: иначе единственный снимок плана в SaveData после "
                        + "обновления мода станет пустотой, и посёлок потеряет задачу целиком. При смене "
                        + "совместимости перепроверь конструктор TownStreets из двух масок.");
        assertEquals(
                TownMapRules.SIDE,
                legacy.idAt(7, 1).orElseThrow(),
                "Боковая маска отвечает именем side, а не пустотой: иначе группы боковых улиц не соберутся, и "
                        + "дух будет стоять перед непостроенной дорогой. При смене совместимости перепроверь "
                        + "TownPlanCodec.decode.");
        assertTrue(
                legacy.ids().isEmpty(),
                "У безымянного плана нет списка улиц: иначе пустые маски начнут притвораться именованными "
                        + "участками, и в граф уйдут фантомные группы. При смене формата перепроверь поле "
                        + "street в TownPlanCodec.encode.");
    }

    @Test
    void townPlanCodec_NamedStreets_SurviveRoundTrip() {
        TownStreets streets = streetsOf(
                new TownMap.MapStreet("s-1", TownMapRules.MAIN, -20, 0, 20, 4),
                new TownMap.MapStreet("s-2", TownMapRules.SIDE, 0, 10, 4, 30));
        TownPlan plan = new TownPlan(
                new BlockCoord(0, 63, 0), REGION, streets, List.of(), new FenceLine(List.of(), List.of()), List.of());

        TownPlan back = TownPlanCodec.decode(TownPlanCodec.encode(plan));

        assertEquals(
                streets.ids(),
                back.streets().ids(),
                "Кодек плана носит id улиц: доска хранит именно текст кодека, иначе после перезапуска сервера "
                        + "группы аудита пересоберутся без имён, и стратег потеряет связь задачи с дорогой. "
                        + "При смене формата перепроверь поле street в TownPlanCodec и тест round-trip плана "
                        + "(TownPlanTests).");
        assertEquals(
                streets.main().cells(),
                back.streets().main().cells(),
                "Маски главной улицы восстанавливаются клетка в клетку: иначе ворота ограды встанут не против "
                        + "дороги, и дух упрётся в забор. При смене кодека перепроверь ColumnMask.decode.");
        assertEquals(
                "s-2",
                back.streets().idAt(2, 15).orElseThrow(),
                "После чтения сохранённого плана клетка отвечает тем же id: иначе снимок группы и билет "
                        + "разойдутся по адресам, и AUDIT-MISS посыплется на ровном месте. При смене кодека "
                        + "перепроверь OrderSave и TownSnapshot.");
    }
}
