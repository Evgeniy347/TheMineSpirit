package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockBreakerGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 2);
    private static final BlockPos TARGET = new BlockPos(2, 1, 2);
    private static final int TOOL_SLOT = 5;
    private static final int RUNAWAY_LIMIT = 1000;

    private BlockBreakerGameTests() {
    }

    @GameTest(template = "empty", batch = "breaker_hand", timeoutTicks = 40)
    public static void blockBreaker_HandOnDirt_BreaksInVanillaTimeAndDropsDirt(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.DIRT);
        BlockBreaker breaker = new BlockBreaker(spirit);

        helper.assertTrue(!breaker.begin(helper.absolutePos(TARGET)).isRefused(), "земля рядом с духом обязана ломаться");
        int expected = BreakTiming.ticks(0.5, 1.0, true);
        helper.assertTrue(breaker.requiredTicks() == expected,
                "рука на земле: срок ломки " + expected + " тиков по формуле игрока, а выбран " + breaker.requiredTicks());
        int ticks = runToEnd(breaker);

        helper.assertTrue(ticks == expected, "ломка обязана занять ровно " + expected + " тиков, заняла " + ticks);
        helper.assertTrue(helper.getBlockState(TARGET).isAir(), "после ломки в клетке обязан быть воздух");
        helper.assertTrue(GameTestFixtures.countInWorld(helper, Items.DIRT) == 1,
                "рука на земле обязана оставить дроп: один блок земли, фактически "
                        + GameTestFixtures.countInWorld(helper, Items.DIRT));
        helper.assertTrue(breaker.crackStage() == -1, "после BROKEN трещина обязана быть снята, стадия " + breaker.crackStage());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "breaker_pickaxe", timeoutTicks = 40)
    public static void blockBreaker_IronPickaxeOnStone_ChoosesToolDropsCobblestoneAndWearsTool(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.STONE);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(TOOL_SLOT + 1, new ItemStack(Items.WOODEN_AXE));
        BlockBreaker breaker = new BlockBreaker(spirit);

        helper.assertTrue(!breaker.begin(helper.absolutePos(TARGET)).isRefused(), "камень рядом с духом обязан ломаться");
        helper.assertTrue(breaker.choice().slot() == TOOL_SLOT && breaker.choice().dropsAllowed(),
                "для камня обязана выбраться кирка из слота " + TOOL_SLOT + ", а выбрано " + breaker.choice());
        int expected = BreakTiming.ticks(1.5, new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState()), true);
        helper.assertTrue(breaker.requiredTicks() == expected,
                "железная кирка на камне: срок " + expected + " тиков, а выбран " + breaker.requiredTicks());
        runToEnd(breaker);

        helper.assertTrue(helper.getBlockState(TARGET).isAir(), "камень обязан исчезнуть");
        helper.assertTrue(GameTestFixtures.countInWorld(helper, Items.COBBLESTONE) == 1,
                "кирка обязана дать булыжник, фактически " + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE));
        helper.assertTrue(spirit.getInventory().getItem(TOOL_SLOT).getDamageValue() == 1,
                "износ кирки за блок обязан быть 1, фактически " + spirit.getInventory().getItem(TOOL_SLOT).getDamageValue());
        helper.assertTrue(spirit.getInventory().getItem(TOOL_SLOT + 1).getDamageValue() == 0,
                "неподходящий топор не должен изнашиваться");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "breaker_nopick", timeoutTicks = 40)
    public static void blockBreaker_HandOnStone_BreaksSlowlyWithoutDrop(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.STONE);
        BlockBreaker breaker = new BlockBreaker(spirit);

        helper.assertTrue(!breaker.begin(helper.absolutePos(TARGET)).isRefused(), "камень обязан ломаться и рукой");
        helper.assertTrue(breaker.choice().slot() == ToolChoice.HAND && !breaker.choice().dropsAllowed(),
                "без кирки обязана выбраться рука без дропа, а выбрано " + breaker.choice());
        helper.assertTrue(breaker.requiredTicks() == 150,
                "камень рукой без кирки ломается 150 тиков, а выбрано " + breaker.requiredTicks());
        runToEnd(breaker);

        helper.assertTrue(helper.getBlockState(TARGET).isAir(), "камень обязан исчезнуть и при ломке рукой");
        helper.assertTrue(GameTestFixtures.countInWorld(helper, Items.COBBLESTONE) == 0,
                "камень, сломанный рукой, не даёт булыжника, как у игрока");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "breaker_wear", timeoutTicks = 40)
    public static void blockBreaker_ToolOnLastDurability_VanishesFromSlot(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.STONE);
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        spirit.getInventory().setItem(TOOL_SLOT, pickaxe);
        BlockBreaker breaker = new BlockBreaker(spirit);

        breaker.begin(helper.absolutePos(TARGET));
        runToEnd(breaker);

        helper.assertTrue(spirit.getInventory().getItem(TOOL_SLOT).isEmpty(),
                "кирка с последней единицей прочности обязана сломаться и исчезнуть из слота, а в слоте "
                        + spirit.getInventory().getItem(TOOL_SLOT));
        helper.assertTrue(GameTestFixtures.countInWorld(helper, Items.COBBLESTONE) == 1,
                "блок, сломанный последним ударом кирки, обязан дать дроп: инструмент для дропа выбран до поломки");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "breaker_refuse", timeoutTicks = 40)
    public static void blockBreaker_ImpossibleTargets_AreRefusedWithReason(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.BEDROCK);
        BlockBreaker breaker = new BlockBreaker(spirit);

        BlockBreaker.Begin bedrock = breaker.begin(helper.absolutePos(TARGET));
        helper.assertTrue(bedrock.isRefused() && bedrock.refusal().contains("неразрушим"),
                "бедрок обязан отвергаться как неразрушимый, получено " + bedrock);
        BlockBreaker.Begin air = breaker.begin(helper.absolutePos(new BlockPos(2, 3, 2)));
        helper.assertTrue(air.isRefused() && air.refusal().contains("воздух"),
                "воздух обязан отвергаться, получено " + air);
        helper.setBlock(8, 1, 2, Blocks.DIRT);
        BlockBreaker.Begin far = breaker.begin(helper.absolutePos(new BlockPos(8, 1, 2)));
        helper.assertTrue(far.isRefused() && far.refusal().contains("дальше"),
                "блок дальше досягаемости " + BlockBreaker.REACH + " обязан отвергаться, получено " + far);
        BlockPos base = helper.absolutePos(SPIRIT_START);
        BlockPos unloaded = GameTestFixtures.firstUnloadedChunkEast(helper, base);
        helper.assertTrue(unloaded != null, "рядом должна найтись незагруженная область для проверки отказа");
        int loadedBefore = helper.getLevel().getChunkSource().getLoadedChunksCount();
        BlockBreaker.Begin notLoaded = breaker.begin(unloaded);
        helper.assertTrue(notLoaded.isRefused() && notLoaded.refusal().contains("не загружен"),
                "блок в незагруженном чанке обязан отвергаться, получено " + notLoaded);
        helper.assertTrue(helper.getLevel().getChunkSource().getLoadedChunksCount() == loadedBefore,
                "проверка блока в незагруженном чанке не должна грузить чанк");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "breaker_crack", timeoutTicks = 40)
    public static void blockBreaker_CrackStage_IsShownWhileBreakingAndClearedOnCancelAndGone(GameTestHelper helper) {
        SpiritEntity spirit = prepare(helper, Blocks.STONE);
        BlockBreaker breaker = new BlockBreaker(spirit);
        BlockPos target = helper.absolutePos(TARGET);

        breaker.begin(target);
        for (int i = 0; i < 40; i++) {
            breaker.tick();
        }
        helper.assertTrue(breaker.crackStage() > 0 && breaker.crackStage() <= 9,
                "посреди ломки обязана показываться стадия трещины 1..9, а стадия " + breaker.crackStage());
        breaker.cancel();
        helper.assertTrue(breaker.crackStage() == -1, "cancel обязан снять трещину, стадия " + breaker.crackStage());
        helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.STONE), "отменённая ломка не должна трогать блок");

        breaker.begin(target);
        for (int i = 0; i < 40; i++) {
            breaker.tick();
        }
        helper.setBlock(TARGET, Blocks.AIR);
        helper.assertTrue(breaker.tick() == BlockBreaker.Progress.GONE,
                "блок, исчезнувший сам, обязан дать GONE");
        helper.assertTrue(breaker.crackStage() == -1, "GONE обязан снять трещину, стадия " + breaker.crackStage());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static SpiritEntity prepare(GameTestHelper helper, Block target) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(TARGET, target);
        return helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
    }

    private static int runToEnd(BlockBreaker breaker) {
        int ticks = 0;
        BlockBreaker.Progress progress = BlockBreaker.Progress.BREAKING;
        while (progress == BlockBreaker.Progress.BREAKING && ticks < RUNAWAY_LIMIT) {
            progress = breaker.tick();
            ticks++;
        }
        return ticks;
    }
}
