package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolChoiceHandDigTests {
    private static final ToolOption BARE_HAND =
            new ToolOption(ToolChoice.HAND, ToolChoice.HAND_SPEED, false, 0, 0, false, "");
    private static final ToolOption AXE = new ToolOption(3, 4.0, false, 59, 59, false, "minecraft:wooden_axe");
    private static final ToolOption WOODEN_PICKAXE =
            new ToolOption(4, 2.0, false, 59, 59, false, "minecraft:wooden_pickaxe");

    @Test
    void refusesHandDig_StoneWithoutPickaxe_Refused() {
        List<ToolOption> options = List.of(BARE_HAND, AXE);
        Choice choice = ToolChoice.best(options);

        assertTrue(
                ToolChoice.refusesHandDig(choice, options, true, false),
                "Камень без кирки руками не копается (владелец 27.10, INC-94): без дропа шахта стоит, а дух держит "
                        + "задание; при смене правила пересмотреть BlockBreaker и выдачу first:build_mine");
    }

    @Test
    void refusesHandDig_WalledInWithoutPickaxe_Allowed() {
        List<ToolOption> options = List.of(BARE_HAND);

        assertFalse(
                ToolChoice.refusesHandDig(ToolChoice.best(options), options, true, true),
                "Замурованному духу без кирки копать руками можно — иначе он не выберется (единственное исключение "
                        + "владельца 27.10)");
    }

    @Test
    void refusesHandDig_BlockWithoutPickaxeNeed_Allowed() {
        List<ToolOption> options =
                List.of(new ToolOption(ToolChoice.HAND, ToolChoice.HAND_SPEED, true, 0, 0, false, ""));

        assertFalse(
                ToolChoice.refusesHandDig(ToolChoice.best(options), options, false, false),
                "Земля и дерево руками копаются: запрет касается только блоков, которым нужна кирка");
    }

    @Test
    void refusesHandDig_WrongTierPickaxeInInventory_NotHandDig() {
        List<ToolOption> options = List.of(BARE_HAND, WOODEN_PICKAXE);

        assertFalse(
                ToolChoice.refusesHandDig(ToolChoice.best(options), options, true, false),
                "Кирка слабого уровня в инвентаре — это не копание руками: руду не по уровню решают правила руд, "
                        + "а не запрет ручной копки");
    }
}
