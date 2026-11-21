package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchematicMineFormatTests {
    private static final String DESCENT = """
            type: underground
            size: 1x4x1
            entrance: 0,3,0
            repeat:
              step: [1, -1, 0]
              until: "y=50"
            legend:
              '#': @floor
            layers:
              - y0: |
                  #
              - y1: |
                  .
              - y2: |
                  .
              - y3: |
                  .
            """;

    private static final String TORCH = """
            type: underground
            size: 3x4x1
            entrance: 1,1,0
            legend:
              '#': @floor
              'T': wall_torch[facing=east] ~
            layers:
              - y0: |
                  ###
              - y1: |
                   .\s
              - y2: |
                   T\s
              - y3: |
                   .\s
            """;

    private static SchematicDocument parse(String text) {
        return SchematicTextParser.parse(text, "тест.txt");
    }

    @Test
    void parse_FloorLegend_KeepsClassMarker() {
        SchematicDocument document = parse(DESCENT);

        assertEquals(
                "@floor",
                document.schematic().blocks().get(0).block(),
                "легенда '#': @floor обязана сохранить класс пола, а не подставить один точный блок: "
                        + "иначе аудит отвергнет камень и землю под воздухом"
                        + " При смене parse_FloorLegend_KeepsClassMarker проверь это утверждение.");
        assertTrue(
                SchematicBlocks.isFloorClass("minecraft:deepslate"),
                "глубинный сланец — пол: класс принимает любой дешёвый твёрдый блок"
                        + " Иначе тест parse_FloorLegend_KeepsClassMarker перестанет ловить эту поломку. При смене parse_FloorLegend_KeepsClassMarker проверь это утверждение.");
        assertTrue(
                SchematicBlocks.isFloorClass("minecraft:cobblestone"),
                "булыжник — пол"
                        + " Иначе тест parse_FloorLegend_KeepsClassMarker перестанет ловить эту поломку. При смене parse_FloorLegend_KeepsClassMarker проверь это утверждение.");
        assertTrue(
                SchematicBlocks.isFloorClass("minecraft:grass_block"),
                "трава — пол: строитель шахты оставляет её у входа, а аудит без неё считал спуск недостроенным и "
                        + "шахта переоткрывалась по кругу (сцена 56)"
                        + " При смене parse_FloorLegend_KeepsClassMarker проверь это утверждение.");
        assertFalse(
                SchematicBlocks.isFloorClass("minecraft:oak_planks"),
                "доски не пол шахты: класс не должен прощать дорогой блок"
                        + " Иначе тест parse_FloorLegend_KeepsClassMarker перестанет ловить эту поломку. При смене parse_FloorLegend_KeepsClassMarker проверь это утверждение.");
    }

    @Test
    void parse_FloorLegendWithState_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> parse("""
                        size: 1x1x1
                        legend:
                          '#': @floor[facing=north]
                        layers:
                          - y0: |
                              #
                        """),
                "@floor не блок с состоянием: свойства у класса пола не задаются"
                        + " Иначе тест parse_FloorLegendWithState_IsRejected перестанет ловить эту поломку. При смене parse_FloorLegendWithState_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("@floor"),
                "ошибка называет класс пола: " + error.getMessage()
                        + " Иначе тест parse_FloorLegendWithState_IsRejected перестанет ловить эту поломку. При смене parse_FloorLegendWithState_IsRejected проверь это утверждение.");
    }

    @Test
    void expand_RepeatUntilY_RepeatsSegmentToTarget() {
        SchematicDocument document = parse(DESCENT);

        Schematic.Expanded expanded = document.schematic().expand(64);

        assertEquals(
                15,
                expanded.schematic().blocks().size(),
                "от y=64 до y=50 шагом -1 получается 15 ступеней: каждая обязана иметь пол"
                        + " Иначе тест expand_RepeatUntilY_RepeatsSegmentToTarget перестанет ловить эту поломку. При смене expand_RepeatUntilY_RepeatsSegmentToTarget проверь это утверждение.");
        assertEquals(
                45,
                expanded.schematic().air().size(),
                "у каждой из 15 ступеней три клетки прохода: воздух тоже разворачивается"
                        + " Иначе тест expand_RepeatUntilY_RepeatsSegmentToTarget перестанет ловить эту поломку. При смене expand_RepeatUntilY_RepeatsSegmentToTarget проверь это утверждение.");
        assertEquals(
                new BlockCoord(0, 14, 0),
                expanded.offset(),
                "развёрнутая схема сдвигается вверх, чтобы низ стал y=0: сдвиг нужен для входа"
                        + " Иначе тест expand_RepeatUntilY_RepeatsSegmentToTarget перестанет ловить эту поломку. При смене expand_RepeatUntilY_RepeatsSegmentToTarget проверь это утверждение.");
        assertEquals(
                new BlockCoord(0, 17, 0),
                document.expanded(64).header().entrance().orElseThrow(),
                "вход переезжает вместе со схемой: он остаётся верхней клеткой спуска"
                        + " Иначе тест expand_RepeatUntilY_RepeatsSegmentToTarget перестанет ловить эту поломку. При смене expand_RepeatUntilY_RepeatsSegmentToTarget проверь это утверждение.");
    }

    @Test
    void expand_RepeatTimes_RepeatsAlongStep() {
        SchematicDocument document = parse("""
                size: 2x1x1
                repeat:
                  step: [2, 0, 0]
                  times: 3
                legend:
                  '#': stone
                layers:
                  - y0: |
                      ##
                """);

        Schematic.Expanded expanded = document.schematic().expand(64);

        assertEquals(
                6,
                expanded.schematic().blocks().size(),
                "три копии сегмента по два блока дают шесть блоков: times разворачивает ровно столько копий"
                        + " Иначе тест expand_RepeatTimes_RepeatsAlongStep перестанет ловить эту поломку. При смене expand_RepeatTimes_RepeatsAlongStep проверь это утверждение.");
        assertEquals(
                6,
                expanded.schematic().sizeX(),
                "шаг по x удлиняет схему на 2 за копию"
                        + " Иначе тест expand_RepeatTimes_RepeatsAlongStep перестанет ловить эту поломку. При смене expand_RepeatTimes_RepeatsAlongStep проверь это утверждение.");
    }

    @Test
    void parse_OptionalLegend_MarksSoftCell() {
        SchematicDocument document = parse(TORCH);

        assertTrue(
                document.schematic().blocks().stream().anyMatch(Schematic.Entry::optional),
                "суффикс ~ в легенде делает клетку необязательной: без неё аудит не валит постройку"
                        + " Иначе тест parse_OptionalLegend_MarksSoftCell перестанет ловить эту поломку. При смене parse_OptionalLegend_MarksSoftCell проверь это утверждение.");
    }

    @Test
    void check_UndergroundWithoutFloor_IsRejected() {
        SchematicDocument document = parse("""
                type: underground
                size: 1x4x1
                entrance: 0,1,0
                legend:
                  '#': @floor
                  'P': oak_planks
                layers:
                  - y0: |
                      P
                  - y1: |
                      .
                  - y2: |
                      .
                  - y3: |
                      .
                """);

        SchematicCheck.Report report = SchematicCheck.check("probe", document);

        assertTrue(
                report.problems().stream().anyMatch(problem -> problem.contains("probe|floor")),
                "под воздушной колонкой обязан быть пол класса @floor: " + report.problems()
                        + " Иначе тест check_UndergroundWithoutFloor_IsRejected перестанет ловить эту поломку. При смене check_UndergroundWithoutFloor_IsRejected проверь это утверждение.");
    }

    @Test
    void check_UndergroundPassageLowerThanThree_IsRejected() {
        SchematicDocument document = parse("""
                type: underground
                size: 1x3x1
                entrance: 0,1,0
                legend:
                  '#': @floor
                layers:
                  - y0: |
                      #
                  - y1: |
                      .
                  - y2: |
                      .
                """);

        SchematicCheck.Report report = SchematicCheck.check("probe", document);

        assertTrue(
                report.problems().stream().anyMatch(problem -> problem.contains("probe|passage_height")),
                "проход ниже 3 блоков не проходит: дух не выйдет наверх; " + report.problems()
                        + " Иначе тест check_UndergroundPassageLowerThanThree_IsRejected перестанет ловить эту поломку. При смене check_UndergroundPassageLowerThanThree_IsRejected проверь это утверждение.");
    }

    @Test
    void check_UndergroundStepHigherThanOne_IsRejected() {
        SchematicDocument document = parse("""
                type: underground
                size: 2x6x1
                entrance: 0,1,0
                legend:
                  '#': @floor
                  'P': oak_planks
                layers:
                  - y0: |
                      #P
                  - y1: |
                      .
                  - y2: |
                      .#
                  - y3: |
                      ..
                  - y4: |
                       .
                  - y5: |
                       .
                """);

        SchematicCheck.Report report = SchematicCheck.check("probe", document);

        assertTrue(
                report.problems().stream().anyMatch(problem -> problem.contains("probe|passage_step")),
                "ступень выше 1 блока не проходится пешком: " + report.problems()
                        + " Иначе тест check_UndergroundStepHigherThanOne_IsRejected перестанет ловить эту поломку. При смене check_UndergroundStepHigherThanOne_IsRejected проверь это утверждение.");
    }

    @Test
    void check_UndergroundDescentEntranceNotTop_IsRejected() {
        SchematicDocument document = parse(DESCENT);
        SchematicDocument lower = new SchematicDocument(
                document.schematic(),
                new SchematicDocument.Header(
                        document.header().name(),
                        document.header().type(),
                        document.header().stage(),
                        document.header().level(),
                        document.header().requires(),
                        Optional.of(new BlockCoord(0, 1, 0))),
                List.of());

        SchematicCheck.Report report = SchematicCheck.check("probe", lower);

        assertTrue(
                report.problems().stream().anyMatch(problem -> problem.contains("probe|entrance")),
                "вход спуска — верхняя клетка, иначе дух начнёт с середины; " + report.problems()
                        + " При смене check_UndergroundDescentEntranceNotTop_IsRejected проверь это утверждение.");
    }

    @Test
    void check_UndergroundWithoutOptionalTorch_Passes() {
        SchematicDocument document = parse(TORCH);

        SchematicCheck.Report report = SchematicCheck.check("probe", document);

        assertTrue(
                report.ok(),
                "без факела подземная постройка проходит: свет не обязателен, пока нет угля; " + report.problems()
                        + " Иначе тест check_UndergroundWithoutOptionalTorch_Passes перестанет ловить эту поломку. При смене check_UndergroundWithoutOptionalTorch_Passes проверь это утверждение.");
    }
}
