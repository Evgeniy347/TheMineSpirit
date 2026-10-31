package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrchardFuelLiveTicketTests {
    private static final Path TICKET = Path.of("src/test/resources/live/observe12-task-build-orchard.json");
    private static final String TORCH = "torch";
    private static final String CHARCOAL = "charcoal";
    private static final String COAL = "coal";
    private static final int TORCHES_PER_CRAFT = 4;

    private static final MaterialSource RECIPES = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return TORCH.equals(item) ? TORCHES_PER_CRAFT : 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return TORCH.equals(item) ? List.of(new Ingredient(COAL, 1), new Ingredient("stick", 1)) : List.of();
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return Map.of();
        }
    };

    @Test
    void liveOrchardTicket_RequiredFuel_MatchesWhatThePlanFetches() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(read(TICKET));
        BoardCase boardCase = BoardCase.valueOf(ticket.get("case").asText());
        assertEquals(BoardCase.BUILD_ORCHARD, boardCase, "Живой тикет обязан быть постройкой сада");
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));
        TaskNode plan = TicketPlans.plan(boardCase, ticket.get("level").asInt(), target);

        Map<String, Integer> need = CaseMaterials.need(plan, RECIPES);

        assertTrue(
                need.containsKey(CHARCOAL),
                "Тикет сада обязан требовать тот же уголь, который план добывает первым: " + need
                        + ". В observe12 requiredItems={stick=1, coal=1}, а acquire падал на "
                        + "'minecraft:charcoal' — доска ждала уголь из шахты, а дух жёг древесный");
        assertFalse(
                need.containsKey(COAL),
                "Уголь и древесный уголь — замена друг другу в рецепте факела: доска не может требовать оба " + "сразу "
                        + need + " — иначе гейт ждёт уголь, которого в поселении нет");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
