package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SchematicDataGuardTests {
    private static final Path ROOT = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

    @Test
    void realSchematics_EachFile_ParsesAndPassesRules() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : files()) {
            String id = id(file);
            try {
                SchematicDocument document = SchematicTextParser.parse(Files.readString(file), file.toString());
                problems.addAll(SchematicCheck.check(id, document).problems());
            } catch (IllegalStateException error) {
                problems.add(error.getMessage());
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Каждая постройка обязана разбираться и проходить правила качества (стены, вход, крыша, высота, окна, "
                        + "дверь, опора, сундук, свет) или иметь исключение с причиной в своём файле; устаревшее "
                        + "исключение — тоже ошибка: оно молча ослабляет сторож. Проверка вне тестов — "
                        + "scripts/schematic.sh check. Нарушения");
    }

    @Test
    void demoBuildings_EachLevel_IsTypedPreset() {
        List<String> untyped = new ArrayList<>();
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                String path = PresetLibrary.SCHEMATICS_ROOT + level.scheme() + SchematicTextParser.EXTENSION;
                SchematicDocument document = SchematicTextParser.parse(JsonResource.readClasspath(path), path);
                if (document.header().type().isEmpty()
                        || document.header().entrance().isEmpty()) {
                    untyped.add(level.scheme());
                }
            }
        }

        assertEquals(
                List.of(),
                untyped,
                "Каждая постройка демо-каталога обязана нести type и entrance: без них сторожа качества её не "
                        + "проверяют, и демо покажет игроку дом без двери или крыши. Без типа");
    }

    @Test
    void check_StaleExemption_IsReported() {
        SchematicDocument document = SchematicTextParser.parse("""
                type: storage
                size: 3x1x1
                entrance: 1,0,0
                exemptions:
                  - rule: support
                    reason: "так надо"
                legend:
                  '#': oak_planks
                layers:
                  - y0: |
                      #.#
                """, "тест");

        SchematicCheck.Report report = SchematicCheck.check("probe", document);

        assertTrue(
                report.stale().stream().anyMatch(problem -> problem.contains("probe|support")),
                "Исключение для уже проходящего правила обязано считаться устаревшим: иначе сторож ослабляют "
                        + "навсегда; фактически: " + report.stale());
        assertFalse(report.ok(), "Отчёт с устаревшим исключением не может быть зелёным");
    }

    @Test
    void parse_ExemptionWithUnknownRule_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SchematicTextParser.parse("""
                size: 1x1x1
                exemptions:
                  - rule: ghost
                    reason: "так надо"
                legend:
                  '#': oak_planks
                layers:
                  - y0: |
                      #
                """, "тест"),
                "Исключение с несуществующим правилом обязано отвергаться: опечатка иначе молча не применится");

        assertTrue(
                error.getMessage().contains("ghost") && error.getMessage().contains("строка 3"),
                "Ошибка обязана называть неизвестное правило и строку: без них непонятно, что чинить; фактически: "
                        + error.getMessage());
    }

    @Test
    void parse_ExemptionWithoutReason_IsRejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> SchematicTextParser.parse("""
                size: 1x1x1
                exemptions:
                  - rule: door
                legend:
                  '#': oak_planks
                layers:
                  - y0: |
                      #
                """, "тест"),
                "Исключение без причины обязано отвергаться: иначе правило ослабляется молча");

        assertTrue(
                error.getMessage().contains("reason"),
                "Ошибка обязана называть поле reason; фактически: " + error.getMessage());
    }

    private static List<Path> files() throws IOException {
        assertTrue(Files.isDirectory(ROOT), "Сторож не нашёл " + ROOT + ": тесты запускаются из корня проекта");
        try (Stream<Path> stream = Files.list(ROOT)) {
            List<Path> files = stream.filter(path -> path.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList();
            assertFalse(files.isEmpty(), "В " + ROOT + " нет ни одной постройки: сторож проверял бы пустоту");
            return files;
        }
    }

    private static String id(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - SchematicTextParser.EXTENSION.length());
    }
}
