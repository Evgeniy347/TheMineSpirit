package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TorchRulesTests {
    @Test
    void next_FirstCoal_CraftTorches() {
        assertEquals(
                Optional.of(new TorchRules.TorchStep(BoardCase.CRAFT_TORCHES, "coal", 4)),
                TorchRules.next(Map.of("coal", 1, "stick", 8), false),
                "Первый уголь — сразу задание «сделать факелы»: из одного угля 4 факела (владелец 22.10)");
    }

    @Test
    void next_ManyCoal_TorchesUpToTarget() {
        assertEquals(
                Optional.of(new TorchRules.TorchStep(BoardCase.CRAFT_TORCHES, "coal", TorchRules.TORCH_TARGET)),
                TorchRules.next(Map.of("coal", 30), false),
                "Угля много — факелов ровно до запаса, а не весь уголь в факелы");
    }

    @Test
    void next_CharcoalOnly_TorchesFromCharcoal() {
        assertEquals(
                Optional.of(new TorchRules.TorchStep(BoardCase.CRAFT_TORCHES, "charcoal", 8)),
                TorchRules.next(Map.of("charcoal", 2, "torch", 6), false),
                "Древесный уголь годен для факелов; есть 6 факелов — нужно ещё 10, угля хватает на 8");
    }

    @Test
    void next_NoCoalFurnaceAndLogs_SmeltCharcoalFromAbundantLog() {
        assertEquals(
                Optional.of(
                        new TorchRules.TorchStep(BoardCase.SMELT_CHARCOAL, "jungle_log", TorchRules.CHARCOAL_BATCH)),
                TorchRules.next(Map.of("jungle_log", 9, "oak_log", 5), true),
                "Угля нет, печь есть — пережечь пару брёвен самой обильной породы в древесный уголь на факелы");
    }

    @Test
    void next_NoCoalNoFurnace_Nothing() {
        assertEquals(
                Optional.empty(),
                TorchRules.next(Map.of("oak_log", 64), false),
                "Без угля и печи факелы сделать нельзя: невыполнимое задание не ставится");
    }

    @Test
    void next_FurnaceTooFewLogs_Nothing() {
        assertEquals(
                Optional.empty(),
                TorchRules.next(Map.of("oak_log", TorchRules.CHARCOAL_LOGS - 1), true),
                "Брёвен меньше, чем на уголь и топливо, — пережигать нечего: задание было бы невыполнимым");
    }

    @Test
    void next_TorchesStocked_Nothing() {
        assertEquals(
                Optional.empty(),
                TorchRules.next(Map.of("torch", TorchRules.TORCH_TARGET, "coal", 5), true),
                "Запас факелов полон — уголь не тратится");
    }
}
