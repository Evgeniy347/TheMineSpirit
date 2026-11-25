package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TownDrawioTests {
    private static TownMap map(String plotId) {
        return new TownMap(
                new TownMap.MapPoint(0, 0),
                List.of(new TownMap.MapPlot(plotId, "hut", -10, 20, 5, 35, "south")),
                List.of(new TownMap.MapStreet("main", "main", -20, 0, 20, 4)),
                List.of(new TownMap.WallSegment(-20, 0, 20, 0)));
    }

    @Test
    void encode_MapWithPlotStreetWall_EmbedsPngAndGeometry() {
        String xml = TownDrawio.encode(map("hut"), 9, -20, -10, 20, 50, 2, new byte[] {1, 2, 3});

        assertTrue(
                xml.contains("data:image/png;base64,AQID")
                        && xml.contains("tmKind=\"plot\" tmId=\"hut\"")
                        && xml.contains("<mxGeometry x=\"20\" y=\"60\" width=\"32\" height=\"32\" as=\"geometry\"/>")
                        && xml.contains("tmKind=\"street\" tmId=\"main\"")
                        && xml.contains("tmKind=\"wall\" tmX1=\"-20\"")
                        && xml.contains("tmKind=\"meta\" tmFormat=\"1\" tmRevision=\"9\""),
                "Выгрузка .drawio обязана нести PNG-подложку и фигуры в пикселях рамки: без этого diagrams.net "
                        + "покажет пустой лист, а координаты участков разъедутся с картой мира; получено "
                        + xml.length()
                        + " символов Иначе тест encode_MapWithPlotStreetWall_EmbedsPngAndGeometry перестанет ловить "
                        + "эту поломку. При смене encode_MapWithPlotStreetWall_EmbedsPngAndGeometry проверь подложку, "
                        + "геометрию участка, улицу, стену и ревизию в скрытой ячейке meta");
    }

    @Test
    void encode_PlotIdWithXmlSymbols_EscapesAttribute() {
        String xml = TownDrawio.encode(map("hut & \"a\" <b>"), 1, -20, -10, 20, 50, 2, new byte[] {1});

        assertTrue(
                xml.contains("value=\"hut &amp; &quot;a&quot; &lt;b&gt;\""),
                "Символы XML в id участка обязаны экранироваться: иначе файл .drawio не разберётся и карта не "
                        + "откроется в diagrams.net; получено "
                        + xml.length()
                        + " символов Иначе тест encode_PlotIdWithXmlSymbols_EscapesAttribute перестанет ловить эту "
                        + "поломку. При смене encode_PlotIdWithXmlSymbols_EscapesAttribute проверь экранирование id "
                        + "участка в атрибуте value");
    }

    @Test
    void encode_RingAndSideStreets_WriteRingLabelAndVerticalLine() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(0, 0),
                List.of(),
                List.of(
                        new TownMap.MapStreet("ring", "ring", 0, 0, 0, 0),
                        new TownMap.MapStreet("side", "side", 0, 0, 4, 30)),
                List.of(new TownMap.WallSegment(0, 0, 10, 10)));

        String xml = TownDrawio.encode(map, 2, -20, -20, 40, 40, 2, new byte[] {1});

        assertTrue(
                xml.contains("кольцо: ring")
                        && xml.contains("tmKindValue=\"side\"")
                        && xml.contains("tmKind=\"wall\" tmX1=\"0\" tmZ1=\"0\" tmX2=\"10\" tmZ2=\"10\""),
                "Кольцевая улица обязана подписываться, а боковая и диагональная стена — рисоваться отдельными "
                        + "фигурами: без этого в .drawio пропадут улица без координат и косая стена, и план "
                        + "разойдётся с миром; получено "
                        + xml.length()
                        + " символов Иначе тест encode_RingAndSideStreets_WriteRingLabelAndVerticalLine перестанет "
                        + "ловить эту поломку. При смене encode_RingAndSideStreets_WriteRingLabelAndVerticalLine "
                        + "проверь подпись кольца, цвет боковой улицы и отрезок стены");
    }
}
