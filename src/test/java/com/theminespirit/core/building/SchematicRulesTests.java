package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchematicRulesTests {
    private static final String ROOT = "schematic-rules/";
    private static final String INDEX = ROOT + "fixtures.json";

    record Fixture(String file, List<SchematicRules.RuleId> expected) {}

    @Test
    void fixtures_EachNegative_IsRejectedByItsOwnRule() {
        for (Fixture fixture : fixtures()) {
            if (fixture.expected().isEmpty()) {
                continue;
            }
            assertEquals(
                    fixture.expected(),
                    ruleIds(fixture),
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
            assertEquals(
                    List.of(),
                    ruleIds(fixture),
                    "Корректная фикстура '" + fixture.file() + "' не обязана давать ни одного нарушения: "
                            + "иначе правило слишком строгое и забракует годную постройку");
        }
    }

    @Test
    void doorsFireplacePorch_OwnerAnswers_EachCaughtByOwnRule() {
        assertEquals(
                List.of(),
                ruleIds(new Fixture("valid_double_door", List.of())),
                "Двойная дверь с петлями по краям и крыльцом из трёх ступенек обязана проходить сторожа");
        assertEquals(
                List.of(SchematicRules.RuleId.DOUBLE_DOOR),
                ruleIds(new Fixture("double_door_hinges_inner", List.of())),
                "Петли посередине проёма: открытые створки встают в проходе — сторож double_door обязан браковать");
        assertEquals(
                List.of(SchematicRules.RuleId.FIREPLACE),
                ruleIds(new Fixture("fireplace_in_wall", List.of())),
                "Костёр в наружной стене виден снаружи (владелец 20.10: камин внутри на 1 блок) — сторож fireplace");
        assertEquals(
                List.of(SchematicRules.RuleId.ENTRANCE_STEP),
                ruleIds(new Fixture("porch_missing", List.of())),
                "Жильё с уровнем без крыльца из трёх ступенек обязано браковаться сторожем entrance_step");
    }

    @Test
    void chestOpenable_ChestAboveChest_IsAllowedButPlankAboveChestIsNot() {
        Fixture stack = new Fixture("chest_stack", List.of());
        Fixture plank = new Fixture("chest_under_plank", List.of(SchematicRules.RuleId.CHEST_OPENABLE));

        assertEquals(
                List.of(),
                ruleIds(stack),
                "Сундук над сундуком (стопка склада) не мешает открыть нижний: в ванили крышку держит только "
                        + "блок, проводящий редстоун, а сундук его не проводит");
        assertEquals(
                List.of(SchematicRules.RuleId.CHEST_OPENABLE),
                ruleIds(plank),
                "Доска над сундуком обязана браковаться: сундук под полным блоком не открыть");
    }

    @Test
    void torchOnFence_StandsOnCenterPost_IsNoSupportViolation() {
        String file = "pen_torch_on_fence";
        String path = ROOT + file + SchematicTextParser.EXTENSION;
        SchematicDocument document = SchematicTextParser.parse(JsonResource.readClasspath(path), path);
        assertEquals(
                List.of(),
                SchematicCheck.check(file, document).problems(),
                "Факел на столбе забора держится: забор даёт центральную опору, как в игре — сторож не должен "
                        + "называть это нарушением support");
    }

    @Test
    void wideStairs_FreeApproach_Passes() {
        assertFalse(
                ruleIds(new Fixture("wide_stairs_free", List.of())).contains(SchematicRules.RuleId.STAIR_APPROACH),
                "Дорожка шириной 2 от двойного входа к двухклеточной лестнице свободна — сторож не должен ругаться");
    }

    @Test
    void wideStairs_NarrowApproach_IsRejected() {
        assertTrue(
                ruleIds(new Fixture("wide_stairs_narrow", List.of())).contains(SchematicRules.RuleId.STAIR_APPROACH),
                "Бочка сузила подход к двухклеточной лестнице до одной клетки — нужен stair_approach");
    }

    @Test
    void wideStairs_SingleDoor_IsRejected() {
        assertTrue(
                ruleIds(new Fixture("wide_stairs_single_door", List.of()))
                        .contains(SchematicRules.RuleId.STAIR_APPROACH),
                "Двухклеточная лестница при входе в одну створку — нужен stair_approach");
    }

    @Test
    void railing_FloorEdgeWithoutFence_IsReported() {
        List<SchematicRules.Violation> violations = railingViolations(false);
        assertTrue(
                violations.stream().anyMatch(violation -> violation.rule() == SchematicRules.RuleId.RAILING),
                "С края пола второго этажа над обрывом можно упасть без ограждения — нужен railing");
    }

    @Test
    void railing_CompleteFence_Passes() {
        List<SchematicRules.Violation> violations = railingViolations(true);
        assertFalse(
                violations.stream().anyMatch(violation -> violation.rule() == SchematicRules.RuleId.RAILING),
                "Ограждение вокруг обрыва замкнуто — сторож не должен ругаться");
    }

    private static List<SchematicRules.Violation> railingViolations(boolean fenced) {
        List<Schematic.Entry> blocks = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new Schematic.Entry(x, 0, z, "minecraft:oak_planks"));
                if (x != 2 || z != 2) {
                    blocks.add(new Schematic.Entry(x, 2, z, "minecraft:oak_planks"));
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = 2 + dx;
                int z = 2 + dz;
                if (!fenced && x == 2 && z == 1) {
                    continue;
                }
                blocks.add(new Schematic.Entry(x, 3, z, "minecraft:oak_fence"));
            }
        }
        Schematic schematic = new Schematic(5, 4, 5, blocks);
        SchematicRules.Structure structure =
                new SchematicRules.Structure("probe", BuildingType.HOUSING, schematic, new BlockCoord(0, 1, 0), 1);
        Set<BlockCoord> feet = new HashSet<>();
        for (Schematic.Entry entry : blocks) {
            if (entry.dy() == 2) {
                feet.add(new BlockCoord(entry.dx(), 3, entry.dz()));
            }
        }
        for (Schematic.Entry entry : blocks) {
            if (entry.dy() == 3) {
                feet.remove(new BlockCoord(entry.dx(), 3, entry.dz()));
            }
        }
        Map<BlockCoord, String> grid = new LinkedHashMap<>();
        for (Schematic.Entry entry : blocks) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        List<SchematicRules.Violation> violations = new ArrayList<>();
        SchematicRailing.check(structure, grid, new SchematicWalk.Result(feet, Set.of()), violations);
        return violations;
    }

    @Test
    void ruleIds_AllValues_HaveUniqueNames() {
        List<String> ids = new ArrayList<>();
        for (SchematicRules.RuleId rule : SchematicRules.RuleId.values()) {
            ids.add(rule.id());
        }
        assertEquals(
                ids.size(),
                ids.stream().distinct().count(),
                "id правил обязаны быть уникальными: по id выбирают исключение, и повтор склеил бы два правила");
    }

    @Test
    void ruleId_UnknownId_IsRejected() {
        assertEquals(
                List.of(),
                SchematicRules.RuleId.byId("no_such_rule").stream().toList(),
                "Неизвестный id правила обязан не находиться: исключение с опечаткой не должно молча ослаблять сторож");
    }

    private static List<SchematicRules.RuleId> ruleIds(Fixture fixture) {
        return SchematicRules.check(structure(fixture)).stream()
                .map(SchematicRules.Violation::rule)
                .toList();
    }

    private static SchematicRules.Structure structure(Fixture fixture) {
        String path = ROOT + fixture.file() + SchematicTextParser.EXTENSION;
        SchematicDocument document = SchematicTextParser.parse(JsonResource.readClasspath(path), path);
        return new SchematicRules.Structure(
                fixture.file(),
                document.header().type().orElseThrow(),
                document.schematic(),
                document.header().entrance().orElseThrow(),
                document.header().level());
    }

    private static List<Fixture> fixtures() {
        JsonNode root = JsonResource.parse(JsonResource.readClasspath(INDEX), INDEX);
        List<Fixture> fixtures = new ArrayList<>();
        for (JsonNode item : JsonResource.array(root.get("fixtures"), INDEX, "списка fixtures")) {
            String file = JsonResource.text(item.get("file"), INDEX, "поля file фикстуры");
            List<SchematicRules.RuleId> expected = new ArrayList<>();
            for (String id : JsonResource.optionalStrings(item.get("expect"), INDEX, "ожидаемых правил")) {
                expected.add(SchematicRules.RuleId.byId(id)
                        .orElseThrow(() -> new IllegalStateException(
                                "У фикстуры '" + file + "' ожидается неизвестное правило '" + id + "'")));
            }
            fixtures.add(new Fixture(file, expected));
        }
        return fixtures;
    }
}
