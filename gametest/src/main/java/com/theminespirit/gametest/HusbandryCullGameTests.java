package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryCullGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryCullGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void cull_FiveCowsKeepThree_TwoCulledCalfAliveDropsPicked(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandrySceneSupport.fencePerimeter(helper);
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 5));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 6));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 4));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 6));
        HusbandrySceneSupport.spawnStill(helper, EntityType.COW, new BlockPos(5, 1, 2));
        HusbandrySceneSupport.spawnBaby(helper, EntityType.COW, new BlockPos(1, 1, 7), true);
        SpiritEntity spirit = HusbandrySceneSupport.spawnWith(helper, START, new ItemStack(Items.IRON_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("cull", Map.of("species", "cow", "keep", 3, "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandrySceneSupport.assertSucceeded(helper, spirit, "из пяти коров лишние две обязаны забиться");
            List<Cow> cows = HusbandrySceneSupport.alive(helper, Cow.class);
            helper.assertTrue(
                    cows.size() == 4, "остаться обязаны 3 взрослые и телёнок (всего 4), фактически " + cows.size());
            helper.assertTrue(
                    cows.stream().filter(Cow::isBaby).count() == 1,
                    "телёнок обязан остаться цел: детёнышей не трогают никогда");
            int beef = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BEEF);
            int leather = GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER);
            helper.assertTrue(
                    beef >= 2 && beef + leather >= 2,
                    "говядина и кожа с двух туш обязаны подобраться (говядины " + beef + ", кожи " + leather + ")");
            GameTestFixtures.finish(helper);
        });
    }
}
