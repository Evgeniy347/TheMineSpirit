package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchematicRulesTests {
    private static final String ROOT = "schematic-rules/";
    private static final String INDEX = ROOT + "fixtures.json";

    record Fixture(String file, List<SchematicRules.RuleId> expected) {
    }

    @Test
    void fixtures_EachNegative_IsRejectedByItsOwnRule() {
        for (Fixture fixture : fixtures()) {
            if (fixture.expected().isEmpty()) {
                continue;
            }
            assertEquals(fixture.expected(), ruleIds(fixture),
                    "Фикстура '" + fixture.file() + "' обязана нарушать только своё правило " + fixture.expected()
                            + ": иначе непонятно, какое правило сломалось, а тест зеленеет не на том");
        }
    }

    @Test
    void fixtures_ValidSchematics_ProduceNoViolations() {
        for (Fixture fixture : fixtures()) {
            if (!fixture.expected().isEmpty()) {
                continue;
            }
            assertEquals(List.of(), ruleIds(fixture),
                    "Корректная фикстура '" + fixture.file() + "' не обязана давать ни одного нарушения: "
                            + "иначе правило слишком строгое и забракует годную постройку");
        }
    }

    @Test
    void chestOpenable_ChestAboveChest_IsAllowedButPlankAboveChestIsNot() {
        Fixture stack = new Fixture("chest_stack", List.of());
        Fixture plank = new Fixture("chest_under_plank", List.of(SchematicRules.RuleId.CHEST_OPENABLE));

        assertEquals(List.of(), ruleIds(stack),
                "Сундук над сундуком (стопка склада) не мешает открыть нижний: в ванили крышку держит только "
                        + "блок, проводящий редстоун, а сундук его не проводит");
        assertEquals(List.of(SchematicRules.RuleId.CHEST_OPENABLE), ruleIds(plank),
                "Доска над сундуком обязана браковаться: сундук под полным блоком не открыть");
    }

    @Test
    void ruleIds_AllValues_HaveUniqueNames() {
        List<String> ids = new ArrayList<>();
        for (SchematicRules.RuleId rule : SchematicRules.RuleId.values()) {
            ids.add(rule.id());
        }
        assertEquals(ids.size(), ids.stream().distinct().count(),
                "id правил обязаны быть уникальными: по id выбирают исключение, и повтор склеил бы два правила");
    }

    @Test
    void ruleId_UnknownId_IsRejected() {
        assertEquals(List.of(), SchematicRules.RuleId.byId("no_such_rule").stream().toList(),
                "Неизвестный id правила обязан не находиться: исключение с опечаткой не должно молча ослаблять сторож");
    }

    private static List<SchematicRules.RuleId> ruleIds(Fixture fixture) {
        return SchematicRules.check(structure(fixture)).stream().map(SchematicRules.Violation::rule).toList();
    }

    private static SchematicRules.Structure structure(Fixture fixture) {
        String path = ROOT + fixture.file() + SchematicTextParser.EXTENSION;
        SchematicDocument document = SchematicTextParser.parse(JsonResource.readClasspath(path), path);
        return new SchematicRules.Structure(fixture.file(), document.header().type().orElseThrow(),
                document.schematic(), document.header().entrance().orElseThrow());
    }

    private static List<Fixture> fixtures() {
        JsonNode root = JsonResource.parse(JsonResource.readClasspath(INDEX), INDEX);
        List<Fixture> fixtures = new ArrayList<>();
        for (JsonNode item : JsonResource.array(root.get("fixtures"), INDEX, "списка fixtures")) {
            String file = JsonResource.text(item.get("file"), INDEX, "поля file фикстуры");
            List<SchematicRules.RuleId> expected = new ArrayList<>();
            for (String id : JsonResource.optionalStrings(item.get("expect"), INDEX, "ожидаемых правил")) {
                expected.add(SchematicRules.RuleId.byId(id).orElseThrow(() -> new IllegalStateException(
                        "У фикстуры '" + file + "' ожидается неизвестное правило '" + id + "'")));
            }
            fixtures.add(new Fixture(file, expected));
        }
        return fixtures;
    }
}
