package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrchardDecorTests {
    private static final Path ORCHARD =
            Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT + "orchard_1.txt");
    private static final Set<String> FLOWERS =
            Set.of("poppy", "dandelion", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "blue_orchid");

    @Test
    void orchardMaterials_PathAndFlowers_NotBought() throws IOException {
        Schematic orchard = parse();
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_ORCHARD, TicketTarget.at(new BlockCoord(10, 60, -5)));
        MaterialSource source = new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return 1;
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return List.of();
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return PresetLibrary.countMaterials(orchard);
            }
        };

        Map<String, Integer> need = CaseMaterials.need(plan, source);

        assertTrue(
                entries(orchard).stream().anyMatch(entry -> "dirt_path".equals(SchematicBlocks.path(entry.block()))),
                "Схема сада обязана содержать тропинку: иначе тест не проверяет действие лопатой");
        assertFalse(
                need.containsKey("dirt_path"),
                "Тропинка не предмет: её топчут лопатой по земле или траве на клетке схемы. Материалы: " + need);
        for (String flower : FLOWERS) {
            assertFalse(
                    need.containsKey(flower),
                    "Цветок '" + flower + "' — декор: его отсутствие не держит постройку сада. Материалы: " + need);
        }
        assertTrue(
                need.containsKey("charcoal") && need.containsKey("stick"),
                "Необязательная тропинка не должна прятать остальные материалы сада: факелы крафтятся из "
                        + "древесного угля и палки, и эти материалы остаются. Материалы: " + need);
        assertFalse(
                need.containsKey("coal"),
                "Сад требует древесный уголь, а не уголь из шахты: это то, что план добывает первым "
                        + "(observe12 N19). Материалы: " + need);
    }

    @Test
    void orchardFlowers_InGardenScheme_MarkedOptional() throws IOException {
        Schematic orchard = parse();

        for (Schematic.Entry entry : entries(orchard)) {
            String block = SchematicBlocks.path(entry.block());
            if (FLOWERS.contains(block)) {
                assertTrue(entry.optional(), "Цветок '" + block + "' в схеме сада — необязательный декор");
            }
        }
    }

    @Test
    void orchardPlan_BuildOrchard_DoesNotAcquirePathOrFlowers() {
        List<String> acquired = new ArrayList<>();
        collectAcquired(
                TicketPlans.plan(BoardCase.BUILD_ORCHARD, TicketTarget.at(new BlockCoord(10, 60, -5))), acquired);

        assertTrue(
                acquired.stream().noneMatch(item -> item.endsWith("dirt_path")),
                "План сада не добывает dirt_path: тропинка делается лопатой на месте, а не предметом. Добыча: "
                        + acquired);
        for (String flower : FLOWERS) {
            assertTrue(
                    acquired.stream().noneMatch(item -> item.endsWith(flower)),
                    "План сада не добывает цветок '" + flower + "': декор не блокирует постройку. Добыча: " + acquired);
        }
    }

    @Test
    void schematicActions_DirtPathCell_NeedsShovelOnDirt() {
        assertTrue(
                SchematicActions.isActionPlaced("minecraft:dirt_path"),
                "Клетка dirt_path ставится действием, а не предметом схемы");
        assertFalse(SchematicActions.isActionPlaced("minecraft:dirt"), "Земля не ставится действием лопаты");
        assertTrue(SchematicActions.isTrampleGround("minecraft:grass_block"), "По траве тропинку топчут лопатой");
        assertTrue(SchematicActions.isTrampleGround("minecraft:dirt"), "По земле тропинку топчут лопатой");
        assertFalse(SchematicActions.isTrampleGround("minecraft:stone"), "Камень лопатой в тропинку не превратить");
        assertEquals(
                "minecraft:wooden_shovel", SchematicActions.shovelItem(false), "Без запаса камня — деревянная лопата");
        assertEquals(
                "minecraft:stone_shovel", SchematicActions.shovelItem(true), "С камнем — каменная лопата, она прочнее");
    }

    private static Schematic parse() throws IOException {
        return SchematicTextParser.parse(Files.readString(ORCHARD), ORCHARD.toString())
                .schematic();
    }

    private static List<Schematic.Entry> entries(Schematic schematic) {
        return schematic.blocks();
    }

    private static void collectAcquired(TaskNode node, List<String> acquired) {
        switch (node) {
            case ScriptCall call -> {
                if ("acquire".equals(call.script()) && call.params().get("item") instanceof String item) {
                    acquired.add(item);
                }
            }
            case SequenceNode sequence -> sequence.steps().forEach(step -> collectAcquired(step, acquired));
            default -> {}
        }
    }
}
