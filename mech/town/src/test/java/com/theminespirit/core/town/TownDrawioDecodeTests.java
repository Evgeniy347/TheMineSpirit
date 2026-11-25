package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TownDrawioDecodeTests {
    private static final String META = "<mxCell id=\"meta\" tmKind=\"meta\" tmFormat=\"2\" tmRevision=\"4\" "
            + "tmAnchorX=\"0\" tmAnchorZ=\"0\" tmX1=\"-20\" tmZ1=\"-10\" tmX2=\"20\" tmZ2=\"50\" tmScale=\"2\"/>";

    private static String doc(String cells) {
        return "<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/>" + META + cells
                + "</root></mxGraphModel></diagram></mxfile>";
    }

    @Test
    void decode_CellsWithoutGeometry_UsesTmCoordinates() {
        String cells = "<mxCell id=\"p\" tmKind=\"plot\" tmId=\"hut\" tmBuilding=\"hut\" tmFacing=\"south\" "
                + "tmX1=\"-10\" tmZ1=\"20\" tmX2=\"5\" tmZ2=\"35\"/>"
                + "<mxCell id=\"s\" tmKind=\"street\" tmId=\"main\" tmKindValue=\"main\" "
                + "tmX1=\"-20\" tmZ1=\"0\" tmX2=\"20\" tmZ2=\"4\"/>"
                + "<mxCell id=\"w\" tmKind=\"wall\" tmX1=\"-20\" tmZ1=\"0\" tmX2=\"20\" tmZ2=\"0\"/>";

        TownMap map = TownDrawio.decode(doc(cells)).map();

        assertEquals(
                List.of(new TownMap.MapPlot("hut", "hut", -10, 20, 5, 35, "south")),
                map.plots(),
                "Участок без геометрии читается по tm-координатам: иначе ранее выгруженный план нельзя "
                        + "применить; получено " + map.plots()
                        + " Иначе тест decode_CellsWithoutGeometry_UsesTmCoordinates перестанет ловить эту поломку. "
                        + "При смене decode_CellsWithoutGeometry_UsesTmCoordinates проверь участок");
        assertEquals(
                List.of(new TownMap.MapStreet("main", "main", -20, 0, 20, 4)),
                map.streets(),
                "Старый .drawio без геометрии читается по tm-координатам: иначе ранее выгруженный план нельзя "
                        + "применить; получено " + map.streets()
                        + " Иначе тест decode_CellsWithoutGeometry_UsesTmCoordinates перестанет ловить эту поломку. "
                        + "При смене decode_CellsWithoutGeometry_UsesTmCoordinates проверь улицу");
        assertEquals(
                List.of(new TownMap.WallSegment(-20, 0, 20, 0)),
                map.wall(),
                "Стена без геометрии тоже берётся из tm-атрибутов: иначе старый файл теряет ограду; получено "
                        + map.wall()
                        + " Иначе тест decode_CellsWithoutGeometry_UsesTmCoordinates перестанет ловить эту поломку. "
                        + "При смене decode_CellsWithoutGeometry_UsesTmCoordinates проверь стену");
    }

    @Test
    void decode_UnknownKind_RejectsFigure() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(doc("<mxCell tmKind=\"tree\" tmId=\"oak\"/>")),
                "Неизвестный tmKind обязан отклоняться, а не пропускаться молча: иначе опечатка в фигуре "
                        + "применяет неполный план; Иначе тест decode_UnknownKind_RejectsFigure перестанет ловить "
                        + "эту поломку. При смене decode_UnknownKind_RejectsFigure проверь текст ошибки");

        assertTrue(
                failure.getMessage().contains("tree"),
                "Ошибка обязана называть неизвестный вид: иначе правку .drawio не найти; получено "
                        + failure.getMessage()
                        + " Иначе тест decode_UnknownKind_RejectsFigure перестанет ловить эту поломку. При смене "
                        + "decode_UnknownKind_RejectsFigure проверь имя вида");
    }

    @Test
    void decode_StreetWithoutCoordinates_Rejected() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(doc("<mxCell tmKind=\"street\" tmId=\"main\" tmKindValue=\"main\"/>")),
                "Улица без геометрии и tm-координат обязана отклоняться: иначе проезд исчезает без ошибки; "
                        + "Иначе тест decode_StreetWithoutCoordinates_Rejected перестанет ловить эту поломку. "
                        + "При смене decode_StreetWithoutCoordinates_Rejected проверь отказ");

        assertTrue(
                failure.getMessage().contains("tmX1"),
                "Ошибка обязана называть отсутствующий атрибут tmX1: получено " + failure.getMessage()
                        + " Иначе тест decode_StreetWithoutCoordinates_Rejected перестанет ловить эту поломку. При "
                        + "смене decode_StreetWithoutCoordinates_Rejected проверь атрибут");
    }

    @Test
    void decode_RingStreet_RejectsKind() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(doc("<mxCell tmKind=\"street\" tmId=\"ring\" tmKindValue=\"ring\" "
                        + "tmX1=\"0\" tmZ1=\"0\" tmX2=\"4\" tmZ2=\"4\"/>")),
                "Кольцевая улица в .drawio обязана отклоняться: вид удалён из кода, и принимать его значило бы "
                        + "молча потерять полосу; Иначе тест decode_RingStreet_RejectsKind перестанет ловить эту "
                        + "поломку. При смене decode_RingStreet_RejectsKind проверь отказ");

        assertTrue(
                failure.getMessage().contains("main"),
                "Ошибка обязана перечислять допустимые виды main/side: получено " + failure.getMessage()
                        + " Иначе тест decode_RingStreet_RejectsKind перестанет ловить эту поломку. При смене "
                        + "decode_RingStreet_RejectsKind проверь текст");
    }

    @Test
    void decode_DuplicateMeta_RejectsFile() {
        String xml = doc("").replace("</root>", META + "</root>");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(xml),
                "Две meta обязаны отклоняться, а не выбирать ревизию наугад: иначе применение затрёт чужую "
                        + "правку; Иначе тест decode_DuplicateMeta_RejectsFile перестанет ловить эту поломку. При "
                        + "смене decode_DuplicateMeta_RejectsFile проверь отказ");

        assertTrue(
                failure.getMessage().contains("meta"),
                "Две ячейки meta обязаны отклоняться: иначе ревизия и рамка берутся наугад; получено "
                        + failure.getMessage()
                        + " Иначе тест decode_DuplicateMeta_RejectsFile перестанет ловить эту поломку. При смене "
                        + "decode_DuplicateMeta_RejectsFile проверь отказ");
    }

    @Test
    void decode_BrokenCompressedDiagram_RejectsFile() {
        String xml = "<mxfile><diagram>not-base64!</diagram></mxfile>";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(xml),
                "Битая сжатая диаграмма обязана отклоняться, а не читаться как пустая карта: иначе случайный "
                        + "файл сносит план; Иначе тест decode_BrokenCompressedDiagram_RejectsFile перестанет "
                        + "ловить эту поломку. При смене decode_BrokenCompressedDiagram_RejectsFile проверь отказ");

        assertTrue(
                failure.getMessage().contains("сжатия"),
                "Битая сжатая диаграмма обязана отклоняться с подсказкой сохранить без сжатия: получено "
                        + failure.getMessage()
                        + " Иначе тест decode_BrokenCompressedDiagram_RejectsFile перестанет ловить эту поломку. "
                        + "При смене decode_BrokenCompressedDiagram_RejectsFile проверь текст ошибки");
    }

    @Test
    void decode_BadWallRotation_RejectsWall() {
        String cells = "<mxCell tmKind=\"wall\" style=\"rotation=abc;\" tmX1=\"0\" tmZ1=\"0\" tmX2=\"4\" tmZ2=\"4\">"
                + "<mxGeometry x=\"0\" y=\"0\" width=\"8\" height=\"4\" as=\"geometry\"/></mxCell>";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(doc(cells)),
                "Нечисловой поворот стены обязан отклоняться, а не угадываться: иначе ограда уезжает в "
                        + "случайную сторону; Иначе тест decode_BadWallRotation_RejectsWall перестанет ловить эту "
                        + "поломку. При смене decode_BadWallRotation_RejectsWall проверь отказ");

        assertTrue(
                failure.getMessage().contains("Поворот"),
                "Нечисловой поворот стены обязан отклоняться: иначе отрезок уезжает в случайную сторону; получено "
                        + failure.getMessage()
                        + " Иначе тест decode_BadWallRotation_RejectsWall перестанет ловить эту поломку. При смене "
                        + "decode_BadWallRotation_RejectsWall проверь текст ошибки");
    }

    @Test
    void decode_RotatedStreet_SwapsGeometry() {
        String cells = "<mxCell tmKind=\"street\" tmId=\"road\" tmKindValue=\"side\" style=\"rotation=90;\">"
                + "<mxGeometry x=\"0\" y=\"0\" width=\"12\" height=\"120\" as=\"geometry\"/></mxCell>";

        TownMap map = TownDrawio.decode(doc(cells)).map();

        assertEquals(
                List.of(new TownMap.MapStreet("road", "side", -47, 17, 12, 22)),
                map.streets(),
                "Улица, повёрнутая в diagrams.net на 90°, обязана читаться с обменом сторон: иначе дорога из "
                        + "выгрузки владельца ложится поперёк и перекрывает участки; получено " + map.streets()
                        + " Иначе тест decode_RotatedStreet_SwapsGeometry перестанет ловить эту поломку. При смене "
                        + "decode_RotatedStreet_SwapsGeometry проверь прямоугольник улицы");
    }

    @Test
    void decode_StreetRotatedBy45_Rejected() {
        String cells = "<mxCell tmKind=\"street\" tmId=\"road\" tmKindValue=\"side\" style=\"rotation=45;\">"
                + "<mxGeometry x=\"0\" y=\"0\" width=\"12\" height=\"120\" as=\"geometry\"/></mxCell>";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> TownDrawio.decode(doc(cells)),
                "Косая улица обязана отклоняться: участки и дороги выравниваются по осям, иначе блоки улицы "
                        + "не совпадут с миром; Иначе тест decode_StreetRotatedBy45_Rejected перестанет ловить эту "
                        + "поломку. При смене decode_StreetRotatedBy45_Rejected проверь отказ");

        assertTrue(
                failure.getMessage().contains("поворот только на 90°"),
                "Отказ косой улицы обязан объяснять допустимый поворот: иначе правку не исправить по тексту; "
                        + "получено " + failure.getMessage()
                        + " Иначе тест decode_StreetRotatedBy45_Rejected перестанет ловить эту поломку. При смене "
                        + "decode_StreetRotatedBy45_Rejected проверь текст ошибки");
    }
}
