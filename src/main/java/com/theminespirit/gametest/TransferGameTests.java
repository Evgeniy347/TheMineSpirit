package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.core.social.TransferSource;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransferGameTests {
    private static final BlockPos GIVER_POS = new BlockPos(5, 1, 5);
    private static final BlockPos CRAFTER_POS = new BlockPos(6, 1, 5);
    private static final BlockPos TABLE_POS = new BlockPos(8, 1, 5);
    private static final BaseKit NO_PROTECTION = BaseKit.parse(
            "{\"rules\":[{\"match\":[\"*\"],\"keep\":0,\"mode\":\"any\"}],\"never_give_away\":[\"shulker_box\"]}",
            "gametest");

    private TransferGameTests() {
    }

    @GameTest(template = "arena", batch = "transfer_craft", timeoutTicks = 300)
    public static void transfer_SticksToCrafter_StonePickaxeCrafted(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        helper.setBlock(TABLE_POS, Blocks.CRAFTING_TABLE);
        SpiritEntity giver = TacticsScene.spirit(helper, GIVER_POS);
        SpiritEntity crafter = TacticsScene.spirit(helper, CRAFTER_POS);
        giver.getInventory().setItem(0, new ItemStack(Items.STICK, 2));
        crafter.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));

        StackMapper mapper = new StackMapper();
        List<TransferOrder> orders = TransferPlanner.planForCraft("stone_pickaxe", crafter.getUUID(),
                at(crafter), mapper.map(crafter.getInventory()),
                List.of(new Ingredient("minecraft:stick", 2), new Ingredient("minecraft:cobblestone", 3)),
                List.of(new TransferSource(giver.getUUID(), at(giver), mapper.map(giver.getInventory()),
                        NO_PROTECTION, List.of())),
                Map.of(), InventoryTables.values(), 16, 64);

        helper.assertTrue(orders.size() == 1 && orders.get(0).from().isPresent()
                        && orders.get(0).from().orElseThrow().equals(giver.getUUID())
                        && orders.get(0).count() == 2,
                "Планировщик обязан назначить передачу двух палок от дающего крафтеру, заказы: " + orders);

        SpiritRun deliver = SpiritRun.launch(helper, giver, new ScriptCall("deliver", Map.of(
                "item", "stick", "count", 2, "to", "spirit:" + crafter.getUUID())), 1);
        SpiritRun craft = SpiritRun.launch(helper, crafter, new ScriptCall("craft", Map.of(
                "item", "minecraft:stone_pickaxe", "count", 1, "radius", 8)), 1,
                () -> GameTestFixtures.countInInventory(crafter.getInventory(), Items.STICK) == 2);

        helper.succeedWhen(() -> {
            deliver.assertStarted();
            RunStatus giverStatus = giver.taskStatus().orElse(null);
            helper.assertTrue(giverStatus != RunStatus.FAILED,
                    "Передача не должна проваливаться: " + giver.taskFailureReason().orElse("нет причины"));
            if (craft.started()) {
                RunStatus craftStatus = crafter.taskStatus().orElse(null);
                helper.assertTrue(craftStatus != RunStatus.FAILED,
                        "Крафт каменной кирки не должен проваливаться: "
                                + crafter.taskFailureReason().orElse("нет причины"));
            }
            helper.assertTrue(giverStatus == RunStatus.SUCCEEDED,
                    "Передача ещё не завершена: " + giverStatus + ", причина "
                            + giver.taskFailureReason().orElse("нет"));
            helper.assertTrue(crafter.taskStatus().orElse(null) == RunStatus.SUCCEEDED
                            && GameTestFixtures.countInInventory(crafter.getInventory(),
                                    Items.STONE_PICKAXE) == 1,
                    "Кирка ещё не скрафчена: крафт " + crafter.taskStatus().orElse(null) + ", причина "
                            + crafter.taskFailureReason().orElse("нет") + ", кирок "
                            + GameTestFixtures.countInInventory(crafter.getInventory(), Items.STONE_PICKAXE));
            TacticsScene.finish(helper, giver);
        });
    }

    private static BlockCoord at(SpiritEntity spirit) {
        return new BlockCoord(spirit.blockPosition().getX(), spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }
}
