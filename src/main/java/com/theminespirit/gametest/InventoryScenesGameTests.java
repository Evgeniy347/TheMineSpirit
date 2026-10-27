package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InventoryScenesGameTests {
    private InventoryScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_inventory_arena", timeoutTicks = 27652)
    public static void inventoryScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                ArmorEquipGameTests.class,
                InventoryContainersGameTests.class,
                InventoryTransferGameTests.class,
                InventoryDepositGameTests.class,
                InventoryDepositLimitsGameTests.class,
                InventoryReloadGameTests.class,
                InventoryWithdrawGameTests.class,
                InventoryAutoGameTests.class,
                InventoryEquipGameTests.class,
                InventoryMiscGameTests.class,
                KitActivityGameTests.class,
                SpiritSuppliesGameTests.class,
                TransferGameTests.class,
                WarehouseGameTests.class);
    }
}
