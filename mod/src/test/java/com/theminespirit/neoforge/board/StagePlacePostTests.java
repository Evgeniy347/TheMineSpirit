package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ToolGate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StagePlacePostTests {
    @Test
    void requiredTools_LeveledField_IncludesPickaxeForStone() {
        assertEquals(
                List.of("hoe", "shovel", ToolGate.PICKAXE),
                StagePlacePost.requiredTools(List.of("hoe", "shovel"), true),
                "Выравнивание поля требует кирку для камня: иначе дух с тяпкой и лопатой застревает на "
                        + "каменном склоне. При смене requiredTools_LeveledField_IncludesPickaxeForStone проверь "
                        + "StagePlacePost.tools и живой wheat_field.");
    }

    @Test
    void requiredTools_FlatField_DoesNotRequirePickaxe() {
        assertEquals(
                List.of("hoe", "shovel"),
                StagePlacePost.requiredTools(List.of("hoe", "shovel"), false),
                "Ровная схема не требует лишнюю кирку: иначе граф будет ждать инструмент, который не "
                        + "понадобится. При смене requiredTools_FlatField_DoesNotRequirePickaxe проверь ToolGate.requiredAll.");
    }
}
