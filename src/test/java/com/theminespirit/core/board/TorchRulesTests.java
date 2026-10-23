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
    void next_NoCoalNoFurnaceMineDug_MineCoal() {
        assertEquals(
                Optional.of(new TorchRules.TorchStep(
                        BoardCase.GATHER_MATERIALS,
                        TorchRules.COAL,
                        TorchRules.TORCH_TARGET / TorchRules.TORCHES_PER_CRAFT)),
                TorchRules.next(Map.of("stick", 8), false, true),
                "Угля и печи нет, шахта есть — добыть уголь на запас факелов (INC-25: свет ждал факелы вечно)");
    }

    @Test
    void next_NoCoalNoMine_NoCoalMining() {
        assertEquals(
                Optional.empty(),
                TorchRules.next(Map.of("stick", 8), false, false),
                "Уголь добывается в шахте (порядок владельца 21.10): до шахты задание на уголь не ставится");
    }

    @Test
    void next_FurnaceLogsAndMine_CharcoalFirst() {
        assertEquals(
                Optional.of(new TorchRules.TorchStep(BoardCase.SMELT_CHARCOAL, "oak_log", TorchRules.CHARCOAL_BATCH)),
                TorchRules.next(Map.of("oak_log", 8), true, true),
                "Печь и брёвна есть — сначала древесный уголь (чеклист 50 п.5), шахта — когда пережечь нечего");
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

    @Test
    void next_FurnaceNoFreeLogs_GathersSettlementLogsNotCoal() {
        assertEquals(
                Optional.of(
                        new TorchRules.TorchStep(BoardCase.GATHER_MATERIALS, "jungle_log", TorchRules.CHARCOAL_LOGS)),
                TorchRules.next(Map.of(), true, true, TorchRules.TORCH_TARGET, "jungle_log"),
                "При печи уголь — из брёвен поселения: жила угля кончается, провалы добычи запирали духам добычу");
    }

    @Test
    void next_NoFurnaceYetWoodKnown_WaitsForFurnace() {
        assertEquals(
                Optional.empty(),
                TorchRules.next(Map.of(), false, true, TorchRules.TORCH_TARGET, "jungle_log"),
                "До печи уголь из жилы не добывается: печь в цепочке до света, а провал добычи ставит духу паузу");
    }
}
