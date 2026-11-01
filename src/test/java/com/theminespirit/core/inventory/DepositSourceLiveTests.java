package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.IdFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositSourceLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/deposit-wildcard");
    private static final String PLANKS = "minecraft:*_planks";

    @Test
    void liveEmptySpirit_LeftoverDeposit_SucceedsWithoutTransfer() throws IOException {
        int available = available("observe-spirit-4-empty.json", PLANKS);

        DepositSource.Decision decision = DepositSource.of(available, true, PLANKS);

        assertEquals(0, available, "в живом инвентаре spirit-4 при [deposit] *_planks не было ни одной доски");
        assertFalse(
                decision.transfer(),
                "сдавать нечего: [deposit] не должен идти к сундуку и писать «кладу *_planks ×256» "
                        + "(observe17 A5: 30 таких строк с переносом 0)");
        assertTrue(
                decision.success(),
                "leftover-сдача пустой группы — не провал: store после стройки не обязан иметь все виды остатков");
    }

    @Test
    void liveEmptySpirit_RequiredDeposit_FailsWithItemName() throws IOException {
        int available = available("observe-spirit-4-empty.json", PLANKS);

        DepositSource.Decision decision = DepositSource.of(available, false, PLANKS);

        assertFalse(decision.transfer(), "предмета нет — к сундуку идти незачем");
        assertFalse(decision.success(), "обязательная сдача без предмета — провал, а не тихий успех");
        assertTrue(
                decision.reason().contains(PLANKS),
                "причина называет, чего не хватает по фильтру, а не «нужен второй контейнер»: " + decision.reason());
    }

    @Test
    void liveSpiritWithPlanks_WildcardDeposit_Transfers() throws IOException {
        int available = available("observe-spirit-1-planks.json", PLANKS);

        DepositSource.Decision decision = DepositSource.of(available, true, PLANKS);

        assertEquals(21, available, "у spirit-1 в живом мире 21 oak_planks: группа *_planks не пуста");
        assertTrue(decision.transfer(), "доски есть — сдача идёт и раскладывает их по сундукам");
    }

    private static int available(String file, String pattern) throws IOException {
        JsonNode observe = new ObjectMapper().readTree(Files.readString(LIVE.resolve(file), StandardCharsets.UTF_8));
        IdFilter filter = IdFilter.of(List.of(pattern));
        int total = 0;
        for (JsonNode stack : observe.get("inventory")) {
            String item = stack.get("item").asText();
            if (filter.matches(item)) {
                total += stack.get("count").asInt();
            }
        }
        return total;
    }
}
