package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.ORDER_HINT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolTierScenarioTests {
    private static final List<String> KINDS = List.of("pickaxe", "axe", "shovel", "hoe", "sword");

    @Test
    void axes_LogLacking_StoneAheadOfWoodenAndAboveKitIron() {
        GraphPlan plan = GraphWorld.empty().with("oak_log", 0).plan();

        int iron = plan.priority("craft_iron_axe@spirit-1");
        int stone = plan.priority("craft_stone_axe@spirit-1");
        int wooden = plan.priority("craft_wooden_axe@spirit-1");
        assertTrue(
                stone > wooden && stone > iron,
                "Топоры в пределах набора этапа: каменный раньше деревянного; железный выше набора — удача, а не "
                        + "план, и не обгоняет каменный: " + iron + ", " + stone + ", " + wooden + ". " + ORDER_HINT);
    }

    @Test
    void everyKind_IronInStock_IronOvertakesStone() {
        GraphPlan iron = GraphWorld.empty()
                .built("crafting_table", GraphState.Built.BUILT)
                .with("oak_log", 0)
                .with("iron_ingot", 16)
                .with("stick", 8)
                .plan();

        for (String kind : KINDS) {
            String ironTool = "craft_iron_" + kind + "@spirit-1";
            String stoneTool = "craft_stone_" + kind + "@spirit-1";
            assertTrue(
                    iron.priority(ironTool) > iron.priority(stoneTool),
                    kind + ": железо на складе — переход на железный инструмент, он раньше каменного ("
                            + iron.priority(ironTool) + "/" + iron.priority(stoneTool) + "). " + ORDER_HINT);
        }
    }

    @Test
    void everyKind_DiamondsInStock_DiamondOvertakesIron() {
        GraphPlan rich = GraphWorld.empty()
                .built("crafting_table", GraphState.Built.BUILT)
                .with("oak_log", 0)
                .with("iron_ingot", 16)
                .with("diamond", 16)
                .with("stick", 8)
                .plan();

        for (String kind : KINDS) {
            String diamondTool = "craft_diamond_" + kind + "@spirit-1";
            String ironTool = "craft_iron_" + kind + "@spirit-1";
            assertTrue(
                    rich.priority(diamondTool) > rich.priority(ironTool),
                    kind + ": алмазы и железо на складе — переход на лучший инструмент, алмазный раньше железного ("
                            + rich.priority(diamondTool) + "/" + rich.priority(ironTool) + "). " + ORDER_HINT);
        }
    }
}
