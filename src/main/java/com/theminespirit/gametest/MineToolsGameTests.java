package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.TOOL_SLOT;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.countBlock;
import static com.theminespirit.gametest.MineSceneSupport.mineCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineToolsGameTests {
    private MineToolsGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void toolReplace_SpentPickaxeInHandAndSpareInInventory_MinesWithSpareAndKeepsWorn(
            GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        int wornDamage = worn.getMaxDamage() - 1;
        worn.setDamageValue(wornDamage);
        spirit.setItemSlot(EquipmentSlot.MAINHAND, worn);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "с запасной киркой задача обязана дойти до конца, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ": "
                            + spirit.taskFailureReason().orElse(""));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 2,
                    "обе руды обязаны быть добыты запасной киркой, фактически сырого железа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(
                    countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA) == 0,
                    "в мире не должно остаться железной руды, осталось "
                            + countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE)
                            && spirit.getMainHandItem().getDamageValue() >= 1,
                    "в руке обязана оказаться запасная кирка и износиться на добыче, а в руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    spirit.getInventory().getItem(TOOL_SLOT).is(Items.IRON_PICKAXE)
                            && spirit.getInventory().getItem(TOOL_SLOT).getDamageValue() == wornDamage,
                    "изношенная кирка обязана вернуться в слот инвентаря нетронутой, а в слоте "
                            + spirit.getInventory().getItem(TOOL_SLOT));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void toolReplace_SpentPickaxeInHandAndNoSpare_FailsNeedTool(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = helper.spawn(
                ModEntities.SPIRIT.get(), SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        int wornDamage = worn.getMaxDamage() - 1;
        worn.setDamageValue(wornDamage);
        spirit.setItemSlot(EquipmentSlot.MAINHAND, worn);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без запасной кирки задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужен инструмент") && reason.contains("кирка"),
                    "причина обязана назвать, что нужен инструмент (кирка), фактически: " + reason);
            helper.assertTrue(
                    countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA) == 2,
                    "обе руды обязаны остаться: последний удар изношенной кирки не тратится, осталось "
                            + countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    spirit.getMainHandItem().getDamageValue() == wornDamage,
                    "изношенная кирка не должна изнашиваться дальше, фактический износ "
                            + spirit.getMainHandItem().getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void gatherPolicy_ExactIds_ExistInRegistries(GameTestHelper helper) {
        for (String id : GatherPolicy.forbiddenExactIds()) {
            helper.assertTrue(
                    BlockSets.resolve(IdFilter.of(List.of("minecraft:" + id))).size() == 1,
                    "точный запрет gather '" + id + "' обязан существовать в реестре блоков: опечатка в списке "
                            + "ослепляет политику");
        }
        helper.succeed();
    }
}
