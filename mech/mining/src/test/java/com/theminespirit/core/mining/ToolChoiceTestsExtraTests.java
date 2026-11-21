package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.ToolChoiceTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.ToolChoice.Choice;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolChoiceTestsExtraTests {

    @Test
    void refusesHandDig_NormalPickaxeBlockWithoutPick_Refuses() {
        Choice hand = new Choice(ToolChoice.HAND, ToolChoice.HAND_SPEED, false);

        assertEquals(
                true,
                ToolChoice.refusesHandDig(hand, List.of(), true, ToolChoice.DigMode.NORMAL),
                "вне планового прокопа камень без кирки не копают: дроп теряется, руда портится"
                        + " Иначе тест refusesHandDig_NormalPickaxeBlockWithoutPick_Refuses перестанет ловить эту поломку. При смене refusesHandDig_NormalPickaxeBlockWithoutPick_Refuses проверь это утверждение.");
    }
}
