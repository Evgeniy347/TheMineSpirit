package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrchardRequirementsLiveTests {
    private static final Path SCHEME = Path.of("src/main/resources/data/theminespirit/schematics/orchard_1.txt");

    @Test
    void orchard_Requirements_MatchPlotDirtPathAndTorch() throws IOException {
        Schematic orchard = SchematicTextParser.parse(Files.readString(SCHEME, StandardCharsets.UTF_8), "orchard_1")
                .schematic();
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target);

        Map<String, Integer> requirements = BuildGap.requirements(
                plan,
                "oak",
                new BuildGap.Probe() {
                    @Override
                    public String block(BlockCoord at) {
                        return "minecraft:air";
                    }

                    @Override
                    public boolean passable(BlockCoord at) {
                        return true;
                    }
                },
                new BuildGap.Schemes() {
                    @Override
                    public Optional<MineLayout> mine(ScriptCall call) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
                        if (!"build_schematic".equals(call.script())) {
                            return Optional.empty();
                        }
                        BlockCoord origin = new BlockCoord(
                                (Integer) call.params().get("x"),
                                (Integer) call.params().get("y"),
                                (Integer) call.params().get("z"));
                        BuildRotation rotation = call.params().get("rotation") instanceof String turn
                                ? BuildRotation.parse(turn)
                                : BuildRotation.NONE;
                        return Optional.of(orchard.entries(origin, rotation).stream()
                                .filter(entry -> !entry.optional()
                                        && !SchematicBlocks.isSecondaryPart(entry.block(), entry.state()))
                                .toList());
                    }
                });

        assertEquals(
                20,
                requirements.get("dirt"),
                "Требования задания обязаны совпадать с участком: дорожек 20, а в смете они землёй "
                        + "(предмета dirt_path нет). Требования: " + requirements);
        assertFalse(
                requirements.containsKey("dirt_path"),
                "dirt_path не предмет: земля под тропой — обычный dirt. Требования: " + requirements);
        assertEquals(
                4,
                requirements.get("torch"),
                "Требования задания обязаны совпадать с участком: факелов 4. Требования: " + requirements);
        assertTrue(
                !requirements.containsKey("stick") && !requirements.containsKey("charcoal"),
                "Рецепт факелов (stick/charcoal) — внутреннее дело плана, а не требования участка. Требования: "
                        + requirements);
    }
}
