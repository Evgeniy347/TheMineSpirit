package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
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
                xml.contains("image=data:image/png,AQID;")
                        && !xml.contains(";base64,")
                        && xml.contains("tmKind=\"plot\" tmId=\"hut\"")
                        && xml.contains("tmX1=\"-10\" tmZ1=\"20\" tmX2=\"5\" tmZ2=\"35\"")
                        && xml.contains("<mxGeometry x=\"20\" y=\"60\" width=\"32\" height=\"32\" as=\"geometry\"/>")
                        && xml.contains("tmKind=\"street\" tmId=\"main\"")
                        && xml.contains("fillColor=#333333;")
                        && xml.contains("x=\"0\" y=\"20\" width=\"82\" height=\"10\" as=\"geometry\"")
                        && xml.contains("fillColor=#000000;rotation=0;")
                        && xml.contains("<mxGeometry x=\"1\" y=\"19\" width=\"80\" height=\"4\" as=\"geometry\"/>")
                        && !xml.contains("edge=\"1\" parent=\"1\" tmKind=\"wall\"")
                        && xml.contains("tmKind=\"meta\" tmFormat=\"2\" tmRevision=\"9\""),
                "Выгрузка .drawio обязана нести PNG-подложку и фигуры в пикселях рамки: drawio режет style по «;», "
                        + "поэтому data URI идёт без «;base64» и картинка стоит последней; иначе подложка не "
                        + "отрисуется, а координаты участков разъедутся с миром; получено "
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
    void encode_SideStreet_WritesColoredLine() {
        TownMap map = new TownMap(
                new TownMap.MapPoint(0, 0),
                List.of(),
                List.of(new TownMap.MapStreet("side", "side", 0, 0, 4, 30)),
                List.of(new TownMap.WallSegment(0, 0, 10, 10)));

        String xml = TownDrawio.encode(map, 2, -20, -20, 40, 40, 2, new byte[] {1});

        assertTrue(
                xml.contains("tmKindValue=\"side\"")
                        && xml.contains("fillColor=#000000;rotation=45;")
                        && xml.contains("tmKind=\"wall\" tmX1=\"0\" tmZ1=\"0\" tmX2=\"10\" tmZ2=\"10\""),
                "Боковая улица и диагональная стена обязаны рисоваться отдельными фигурами: без этого в .drawio "
                        + "пропадут улица без координат и косая стена, и план разойдётся с миром; получено "
                        + xml.length()
                        + " символов Иначе тест encode_SideStreet_WritesColoredLine перестанет ловить эту поломку. "
                        + "При смене encode_SideStreet_WritesColoredLine проверь цвет боковой улицы и отрезок стены");
    }

    @Test
    void decode_EncodedDrawio_RestoresMapAndRevision() {
        TownMap source = map("hut");
        String xml = TownDrawio.encode(source, 7, -20, -10, 20, 50, 2, new byte[] {1, 2, 3});

        TownDrawio.Decoded decoded = TownDrawio.decode(xml);

        assertEquals(
                source,
                decoded.map(),
                "Круг «выгрузка → повторное чтение» обязан вернуть ту же карту: иначе после правки в diagrams.net "
                        + "применение сдвигает участки; получено " + decoded.map()
                        + " Иначе тест decode_EncodedDrawio_RestoresMapAndRevision перестанет ловить эту поломку. "
                        + "При смене decode_EncodedDrawio_RestoresMapAndRevision проверь участки, улицы и стену");
        assertEquals(
                7,
                decoded.revision(),
                "Ревизия из скрытой meta обязана пережить круг: без неё применение не отличит старый файл от "
                        + "нового; получено " + decoded.revision()
                        + " Иначе тест decode_EncodedDrawio_RestoresMapAndRevision перестанет ловить эту поломку. "
                        + "При смене decode_EncodedDrawio_RestoresMapAndRevision проверь tmRevision");
    }

    @Test
    void encodeStored_WithoutPng_KeepsMapForWorld() {
        TownMap source = map("hut");

        String xml = TownDrawio.encodeStored(source, 5, -20, -10, 20, 50, 2);
        TownMap decoded = TownDrawio.decode(xml).map();

        assertFalse(
                xml.contains("image=data:image/png"),
                "Служебная карта в мире хранится без PNG-подложки: иначе NBT раздувается на сотни килобайт "
                        + "на каждое сохранение; получено " + xml.length()
                        + " Иначе тест encodeStored_WithoutPng_KeepsMapForWorld перестанет ловить эту поломку. При "
                        + "смене encodeStored_WithoutPng_KeepsMapForWorld проверь отсутствие картинки");
        assertEquals(
                source,
                decoded,
                "Служебная карта без подложки читается обратно один в один: иначе мир теряет участки после "
                        + "перезапуска; получено " + decoded
                        + " Иначе тест encodeStored_WithoutPng_KeepsMapForWorld перестанет ловить эту поломку. При "
                        + "смене encodeStored_WithoutPng_KeepsMapForWorld проверь участки и стену");
    }

    @Test
    void decode_MovedPlot_UsesGeometryNotAttributes() {
        String xml = TownDrawio.encode(map("hut"), 7, -20, -10, 20, 50, 2, new byte[] {1})
                .replace(
                        "x=\"20\" y=\"60\" width=\"32\" height=\"32\"", "x=\"40\" y=\"80\" width=\"32\" height=\"32\"");

        TownMap decoded = TownDrawio.decode(xml).map();

        assertEquals(
                new TownMap.MapPlot("hut", "hut", 0, 30, 15, 45, "south"),
                decoded.plots().get(0),
                "Перемещённый в diagrams.net участок обязан читаться по новой геометрии, а не по старым tmX*: "
                        + "иначе правка карты молча применяет прежние координаты; получено " + decoded.plots()
                        + " Иначе тест decode_MovedPlot_UsesGeometryNotAttributes перестанет ловить эту поломку. "
                        + "При смене decode_MovedPlot_UsesGeometryNotAttributes проверь сдвиг участка");
    }

    @Test
    void decode_CompressedDiagram_RestoresMap() {
        TownMap source = map("hut");
        String xml = TownDrawio.encode(source, 3, -20, -10, 20, 50, 2, new byte[] {1});
        int open = xml.indexOf("<diagram");
        String inner = xml.substring(xml.indexOf('>', open) + 1, xml.indexOf("</diagram>"));
        String packed = Base64.getEncoder().encodeToString(deflate(inner));

        TownDrawio.Decoded decoded =
                TownDrawio.decode("<mxfile><diagram id=\"town\">" + packed + "</diagram></mxfile>");

        assertEquals(
                source,
                decoded.map(),
                "Сжатая диаграмма diagrams.net обязана читаться: иначе обычное сохранение из редактора ломает "
                        + "применение плана; получено " + decoded.map()
                        + " Иначе тест decode_CompressedDiagram_RestoresMap перестанет ловить эту поломку. При "
                        + "смене decode_CompressedDiagram_RestoresMap проверь распаковку и координаты");
    }

    @Test
    void decode_WithoutMeta_RejectsFile() {
        String xml =
                "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/>" + "</root></mxGraphModel></diagram></mxfile>";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(xml),
                "Файл без meta обязан отклоняться, а не применяться как пустая карта: иначе произвольный "
                        + "рисунок молча сносит план поселения; Иначе тест decode_WithoutMeta_RejectsFile "
                        + "перестанет ловить эту поломку. При смене decode_WithoutMeta_RejectsFile проверь отказ");

        assertTrue(
                failure.getMessage().contains("meta"),
                "Файл без скрытой meta обязан отклоняться с объяснением: иначе произвольный рисунок молча "
                        + "применяется как карта; получено " + failure.getMessage()
                        + " Иначе тест decode_WithoutMeta_RejectsFile перестанет ловить эту поломку. При смене "
                        + "decode_WithoutMeta_RejectsFile проверь текст ошибки");
    }

    private static byte[] deflate(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, deflater)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Тест не сжал диаграмму", error);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }
}
