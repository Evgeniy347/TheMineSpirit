package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetBuildGameTests {
    private static final int SETTLE_TICKS = 5;

    private PresetBuildGameTests() {}

    private static void wideFloor(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static ScriptCall buildCall(GameTestHelper helper, String file, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        return new ScriptCall(
                "build_schematic",
                Map.of("file", file, "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    private static int countMatches(GameTestHelper helper, Schematic schematic, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        BlockCoord origin = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        int matches = 0;
        for (Schematic.PlacedEntry entry : schematic.entries(origin)) {
            if (helper.getLevel()
                    .getBlockState(new BlockPos(
                            entry.pos().x(), entry.pos().y(), entry.pos().z()))
                    .is(BlockStates.blockFor(entry.block()))) {
                matches++;
            }
        }
        return matches;
    }

    // [флак] каменный дом с места, с запасом на подходы; тест меряет результат, а не скорость.
    @SceneStep(template = "arena", timeoutTicks = 2400)
    public static void preset_StoneHouseFromLibrary_StandsWithRoof(GameTestHelper helper) {
        buildPreset(helper, "stone_house", "каменный дом");
    }

    private static void buildPreset(GameTestHelper helper, String scheme, String title) {
        GameTestFixtures.reset(helper);
        wideFloor(helper);
        BlockPos anchor = new BlockPos(1, 1, 1);
        Schematic schematic = SchematicResources.load(scheme);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(9, 1, 9));
        int slot = 0;
        for (Map.Entry<String, Integer> material :
                PresetLibrary.countMaterials(schematic).entrySet()) {
            Item item = BlockItems.itemFor(material.getKey());
            for (int left = material.getValue(); left > 0; left -= item.getDefaultMaxStackSize()) {
                spirit.getInventory()
                        .setItem(slot++, new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
            }
        }
        SpiritRun run = SpiritRun.launch(helper, spirit, buildCall(helper, scheme, anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    title + " по пресету обязан построиться целиком из ровно своих материалов, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int matches = countMatches(helper, schematic, anchor);
            helper.assertTrue(
                    matches == schematic.blocks().size(),
                    title + " обязан совпасть с пресетом блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size());
            GameTestFixtures.finish(helper);
        });
    }
}
