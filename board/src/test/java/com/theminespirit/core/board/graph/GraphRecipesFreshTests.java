package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphRecipesFreshTests {
    private static final Path RECIPES = Path.of("mod/src/main/resources/" + SupplyGraph.RECIPES);

    @Test
    void committed_Recipes_MatchFreshExport() throws IOException {
        String committed = Files.readString(RECIPES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String fresh = RecipeExportTests.render();
        assertTrue(
                committed.equals(fresh),
                "graph-recipes.json устарел: закоммиченный файл не совпадает со свежей выгрузкой ванильных рецептов. "
                        + "Граф не увидит новые рецепты. Запусти ./gradlew graphExport и закоммить файл.");
    }
}
