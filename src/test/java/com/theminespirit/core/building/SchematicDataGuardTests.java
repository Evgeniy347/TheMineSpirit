package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SchematicDataGuardTests {
    private static final String EXEMPTIONS_PATH = PresetLibrary.SCHEMATICS_ROOT + "structure_exemptions.json";
    private static final Set<String> EXEMPTION_FIELDS = Set.of("schematic", "rule", "reason");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Exemption(String schematic, SchematicRules.RuleId rule, String reason) {
    }

    private record Audit(List<String> unexcused, List<String> problems) {
    }

    @Test
    void realSchematics_WithExemptions_HaveNoUnexcusedViolations() {
        Audit audit = audit(realStructures(), realExemptions());

        assertEquals(List.of(), audit.unexcused(),
                "Каждая схема из DemoBuildings и presets.json обязана проходить правила или иметь исключение "
                        + "с причиной: дом без стены, крыши или света нельзя ставить игроку. Нарушения: "
                        + audit.unexcused());
    }

    @Test
    void realExemptions_EachOne_CoversFailingRule() {
        Audit audit = audit(realStructures(), realExemptions());

        assertEquals(List.of(), audit.problems(),
                "Исключение обязано ссылаться на существующую схему и правило и покрывать настоящее нарушение: "
                        + "устаревшее исключение скрывает, что правило уже проходит, и ослабляет сторож. Ошибки: "
                        + audit.problems());
    }

    @Test
    void exemption_UnknownRule_IsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parseExemptions("[{\"schematic\":\"house\",\"rule\":\"ghost\",\"reason\":\"так надо\"}]",
                        "тестовые исключения"),
                "Исключение с несуществующим правилом обязано отвергаться: опечатка иначе молча не применится "
                        + "и спрячет нарушение");

        assertTrue(error.getMessage().contains("ghost"),
                "Ошибка обязана называть неизвестное правило: без него непонятно, что чинить; фактически: "
                        + error.getMessage());
    }

    @Test
    void exemption_WithoutReason_IsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parseExemptions("[{\"schematic\":\"house\",\"rule\":\"door\",\"reason\":\"  \"}]",
                        "тестовые исключения"),
                "Исключение без причины обязано отвергаться: иначе правило ослабляется молча, и никто не знает, "
                        + "почему постройка не соответствует требованию");

        assertTrue(error.getMessage().contains("reason"),
                "Ошибка обязана называть поле reason: без него непонятно, что заполнить; фактически: "
                        + error.getMessage());
    }

    @Test
    void exemption_UnknownSchematic_IsReported() {
        Audit audit = audit(realStructures(),
                List.of(new Exemption("ghost_house", SchematicRules.RuleId.SUPPORT, "так надо")));

        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("ghost_house")),
                "Исключение для несуществующей схемы обязано попадать в ошибки: иначе опечатка в имени оставила бы "
                        + "настоящую схему без проверки; фактически: " + audit.problems());
    }

    @Test
    void exemption_StaleRule_IsReported() {
        Audit audit = audit(realStructures(),
                List.of(new Exemption("family_house", SchematicRules.RuleId.SUPPORT, "так надо")));

        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("family_house|support")),
                "Исключение для уже проходящего правила обязано считаться устаревшим: иначе сторожа ослабляют "
                        + "навсегда; фактически: " + audit.problems());
    }

    @Test
    void exemption_DuplicatePair_IsReported() {
        Audit audit = audit(realStructures(), List.of(
                new Exemption("family_house", SchematicRules.RuleId.DOOR, "первая причина"),
                new Exemption("family_house", SchematicRules.RuleId.DOOR, "вторая причина")));

        assertTrue(audit.problems().stream().anyMatch(problem -> problem.contains("дважды")),
                "Дубль пары «схема + правило» обязан попадать в ошибки: иначе одна из причин потеряется молча; "
                        + "фактически: " + audit.problems());
    }

    private static Audit audit(List<SchematicRules.Structure> structures, List<Exemption> exemptions) {
        Map<String, SchematicRules.Structure> structuresById = new LinkedHashMap<>();
        for (SchematicRules.Structure structure : structures) {
            structuresById.put(structure.id(), structure);
        }
        List<String> problems = new ArrayList<>();
        Map<String, Exemption> byKey = new LinkedHashMap<>();
        for (Exemption exemption : exemptions) {
            if (!structuresById.containsKey(exemption.schematic())) {
                problems.add("исключение ссылается на несуществующую схему '" + exemption.schematic()
                        + "' (правило " + exemption.rule().id() + ")");
                continue;
            }
            String key = key(exemption.schematic(), exemption.rule());
            if (byKey.put(key, exemption) != null) {
                problems.add("исключение '" + key + "' объявлено дважды");
            }
        }
        Set<String> used = new HashSet<>();
        List<String> unexcused = new ArrayList<>();
        for (SchematicRules.Structure structure : structures) {
            for (SchematicRules.Violation violation : SchematicRules.check(structure)) {
                String key = key(structure.id(), violation.rule());
                if (byKey.containsKey(key)) {
                    used.add(key);
                } else {
                    unexcused.add(key + ": " + violation.message());
                }
            }
        }
        for (String key : byKey.keySet()) {
            if (!used.contains(key)) {
                problems.add("исключение '" + key + "' устарело: правило уже проходит или не применяется к схеме");
            }
        }
        return new Audit(List.copyOf(unexcused), List.copyOf(problems));
    }

    private static String key(String schematic, SchematicRules.RuleId rule) {
        return schematic + "|" + rule.id();
    }

    private static List<SchematicRules.Structure> realStructures() {
        PresetLibrary library = loadLibrary();
        Map<String, DemoBuildings.Building> demoByScheme = new LinkedHashMap<>();
        Set<String> schemes = new TreeSet<>();
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                schemes.add(level.scheme());
                demoByScheme.putIfAbsent(level.scheme(), building);
            }
        }
        for (Preset preset : library.all()) {
            schemes.add(preset.id());
        }
        List<SchematicRules.Structure> structures = new ArrayList<>();
        for (String scheme : schemes) {
            Optional<Preset> preset = library.byId(scheme);
            if (preset.isPresent()) {
                structures.add(new SchematicRules.Structure(scheme, preset.orElseThrow().type(),
                        preset.orElseThrow().schematic(), preset.orElseThrow().entry()));
                continue;
            }
            DemoBuildings.Building demo = demoByScheme.get(scheme);
            if (demo == null) {
                throw new IllegalStateException("Схема '" + scheme + "' не найдена ни в пресетах, ни в демо-каталоге: "
                        + "сторож не знает, какие правила к ней применять");
            }
            String path = PresetLibrary.SCHEMATICS_ROOT + scheme + ".json";
            Schematic schematic = SchematicParser.parse(JsonResource.readClasspath(path), path);
            structures.add(new SchematicRules.Structure(scheme, demo.type(), schematic,
                    new BlockCoord(schematic.sizeX() / 2, 0, 0)));
        }
        return List.copyOf(structures);
    }

    private static List<Exemption> realExemptions() {
        return parseExemptions(JsonResource.readClasspath(EXEMPTIONS_PATH), EXEMPTIONS_PATH);
    }

    private static List<Exemption> parseExemptions(String json, String origin) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Файл '" + origin + "' — не разобран JSON: "
                    + error.getOriginalMessage() + ". Исправьте синтаксис файла", error);
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "': ожидается непустой список исключений "
                    + "[{schematic, rule, reason}]: пустой список не описывает ни одного исключения");
        }
        List<Exemption> exemptions = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject()) {
                throw new IllegalStateException("Файл '" + origin + "': исключение обязано быть объектом "
                        + "с полями schematic, rule и reason");
            }
            JsonResource.checkFields(item, EXEMPTION_FIELDS, origin, "исключения");
            String schematic = JsonResource.text(item.get("schematic"), origin, "поля schematic исключения");
            String ruleId = JsonResource.text(item.get("rule"), origin, "поля rule исключения");
            SchematicRules.RuleId rule = SchematicRules.RuleId.byId(ruleId).orElseThrow(
                    () -> new IllegalStateException("Файл '" + origin + "': исключение ссылается на несуществующее "
                            + "правило '" + ruleId + "': опечатка ослабила бы сторож"));
            String reason = JsonResource.text(item.get("reason"), origin, "поля reason исключения");
            exemptions.add(new Exemption(schematic, rule, reason));
        }
        return List.copyOf(exemptions);
    }

    private static PresetLibrary loadLibrary() {
        String indexPath = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";
        String indexJson = JsonResource.readClasspath(indexPath);
        List<LoadedContent> contents = new ArrayList<>();
        contents.add(new LoadedContent(indexJson, indexPath, TableStatus.Source.MOD));
        for (String path : PresetLibrary.bodyPaths(indexJson, indexPath)) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path, TableStatus.Source.MOD));
        }
        return PresetLibrary.parse(contents);
    }
}
