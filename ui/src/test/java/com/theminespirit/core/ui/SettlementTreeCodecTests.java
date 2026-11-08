package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageRules;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTreeCodecTests {
    @Test
    void codec_FullStageModel_RoundTripsThroughPacketBytes() {
        SettlementTreeFacts facts = new SettlementTreeFacts(Map.of(
                SettlementTreeFacts.boardKey(BoardCase.BUILD_MINE, 1),
                new SettlementTreeFacts.Fact(
                        40,
                        false,
                        false,
                        Map.of("minecraft:oak_planks", 12, "minecraft:cobblestone", 34),
                        new SettlementTree.Position(10, 64, -3))));
        SettlementTree tree = SettlementTrees.build(StageRules.load(), facts, SettlementTreeIcons.load(), 1, 1);

        SettlementTree decoded = SettlementTreeCodec.decode(SettlementTreeCodec.encode(tree));

        assertEquals(
                tree,
                decoded,
                "Пакет древа переживает упаковку и распаковку без потерь: рамки, связи, проценты и подсказки");
    }

    @Test
    void decode_TruncatedPacket_ReportsBrokenPayload() {
        SettlementTree tree =
                SettlementTrees.build(StageRules.load(), SettlementTreeFacts.empty(), SettlementTreeIcons.load(), 1, 1);
        byte[] payload = SettlementTreeCodec.encode(tree);
        byte[] cut = Arrays.copyOf(payload, payload.length / 2);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementTreeCodec.decode(cut),
                "Обрезанный пакет древа обязан давать понятную ошибку, а не молча рисовать половину");

        assertTrue(
                error.getMessage().contains("оборван"),
                "Ошибка обрезанного пакета называет обрыв: «" + error.getMessage() + "»");
    }

    @Test
    void decode_ForeignMagic_ReportsVersionMismatch() {
        SettlementTree tree =
                SettlementTrees.build(StageRules.load(), SettlementTreeFacts.empty(), SettlementTreeIcons.load(), 1, 1);
        byte[] payload = SettlementTreeCodec.encode(tree);
        payload[0] = 0;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementTreeCodec.decode(payload),
                "Чужой пакет древа обязан отвергаться: иначе клиент нарисует мусор вместо этапа");

        assertTrue(
                error.getMessage().contains("метки сборки"),
                "Ошибка чужого пакета говорит о расхождении сборок: «" + error.getMessage() + "»");
    }

    @Test
    void codec_EveryRealStage_RoundTrips() {
        StageRules rules = StageRules.load();
        SettlementTreeIcons icons = SettlementTreeIcons.load();
        for (int stage = 1; stage <= rules.stages().size(); stage++) {
            SettlementTree tree = SettlementTrees.build(rules, SettlementTreeFacts.empty(), icons, 1, stage);

            SettlementTree decoded = SettlementTreeCodec.decode(SettlementTreeCodec.encode(tree));

            assertEquals(
                    tree,
                    decoded,
                    "Этап " + stage + ": пакет древа с реальными таблицами упаковывается и распаковывается");
        }
    }
}
