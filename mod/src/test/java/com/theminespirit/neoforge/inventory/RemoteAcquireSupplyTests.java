package com.theminespirit.neoforge.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RemoteAcquireSupplyTests {
    @Test
    void withdraw_SpiritInsideSettlement_UsesAllRemoteContainers() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/inventory/SpiritSupplies.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("if(ChestWithdrawal.inside(spirit))")
                        && compact.contains("found.addAll(ChestWithdrawal.positions(spirit))")
                        && compact.contains(
                                "BlockLocator.search(spirit.level(),center,radius,ContainerAccess.allowedBlocks())"),
                "Acquire внутри поселения планирует получение из всех сундуков поселения: иначе он снова "
                        + "идёт за факелами в глубокую шахту вместо удалённой бочки. При смене "
                        + "withdraw_SpiritInsideSettlement_UsesAllRemoteContainers проверь SpiritSupplies.withdraw "
                        + "и живой wheat_field.");
    }
}
