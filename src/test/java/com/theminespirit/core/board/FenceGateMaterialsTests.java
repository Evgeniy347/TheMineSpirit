package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.FenceGateRule;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FenceGateMaterialsTests {
    private static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, -40);

    @Test
    void fencePlan_SettlementWood_GatesAskedByMaterials() throws IOException {
        Path file = ROOT.resolve("fence.txt");
        Schematic fence = SchematicTextParser.parse(Files.readString(file), file.toString())
                .schematic();
        MaterialSource source = new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return 0;
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return List.of();
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return "fence".equals(file) ? PresetLibrary.countMaterials(fence) : Map.of();
            }
        };
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_FENCE, 1, TicketTarget.at(ANCHOR).withWood("jungle"));
        Map<String, Integer> need = CaseMaterials.need(plan, source);
        int gates = 4 * FenceGateRule.gates(fence.sizeX()).size();

        assertEquals(
                gates,
                need.get("jungle_fence_gate"),
                "Стройка ограды просит по калитке породы поселения на каждую калитку схемы: иначе дух несёт "
                        + "дубовый забор с чужими калитками или нехватку материала");
    }
}
