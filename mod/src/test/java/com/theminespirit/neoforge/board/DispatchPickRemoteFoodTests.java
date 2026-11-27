package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DispatchPickRemoteFoodTests {
    @Test
    void pick_SettlementFood_InsideSpiritSkipsChestPath() throws IOException {
        String source = Files.readString(
                        Path.of("mod/src/main/java/com/theminespirit/neoforge/board/DispatchPick.java"))
                .replaceAll("\\s+", "");

        assertTrue(
                source.contains("remoteFood(ticket,target)")
                        && source.contains("ChestWithdrawal.inside(entity)")
                        && source.contains("worksWithoutApproach(ticket,target,spirit)"),
                "Дистанционное получение еды внутри поселения не требует пути к конкретному сундуку: "
                        + "иначе голодный дух не получает еду из-за ложного NO_PATH"
                        + " При смене pick_SettlementFood_InsideSpiritSkipsChestPath проверь DispatchPick.pick, "
                        + "reachable и why.");
    }
}
