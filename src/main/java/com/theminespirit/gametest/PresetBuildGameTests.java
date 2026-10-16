package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Дух строит постройку по пресету из своей библиотеки: материалы выданы в инвентарь, тело пресета читается
 * тем же {@code build_schematic}, а итог сверяется с телом схемы. Проверяются склад-хижина (этап 1) и
 * палисад-секция (этап 0) — первая партия застройки поселения.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetBuildGameTests {
    private static final int SETTLE_TICKS = 5;

    private PresetBuildGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int i = 0; i < stacks.length; i++) {
            spirit.getInventory().setItem(i, stacks[i]);
        }
        return spirit;
    }

    private static ScriptCall buildCall(GameTestHelper helper, String file, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        return new ScriptCall("build_schematic", Map.of("file", file, "x", absolute.getX(), "y", absolute.getY(),
                "z", absolute.getZ()));
    }

    private static int countMatches(GameTestHelper helper, Schematic schematic, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        BlockCoord origin = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        int matches = 0;
        for (Schematic.PlacedEntry entry : schematic.entries(origin)) {
            if (helper.getLevel().getBlockState(new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z()))
                    .is(BlockStates.blockFor(entry.block()))) {
                matches++;
            }
        }
        return matches;
    }

    // [флак] 40 блоков склада дух ставит по одному за тик с подходом к каждой клетке; замер: на 400
    // и 900 тиках задача ещё RUNNING, потолок поднят с запасом — иначе тест меряет скорость стройки,
    // а не её результат.
    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 1800)
    public static void preset_StorageHutFromLibrary_StandsBlockForBlock(GameTestHelper helper) {
        prepare(helper);
        BlockPos anchor = new BlockPos(1, 1, 1);
        Schematic schematic = SchematicResources.load("storage_hut");
        SpiritEntity spirit = spawnWith(helper, new BlockPos(3, 1, 6),
                new ItemStack(Items.OAK_PLANKS, 38),
                new ItemStack(Items.CHEST, 2));
        SpiritRun run = SpiritRun.launch(helper, spirit, buildCall(helper, "storage_hut", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "склад-хижина по пресету обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int matches = countMatches(helper, schematic, anchor);
            helper.assertTrue(matches == schematic.blocks().size(),
                    "постройка обязана совпасть с пресетом блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 400)
    public static void preset_PalisadeSectionFromLibrary_StandsBlockForBlock(GameTestHelper helper) {
        prepare(helper);
        BlockPos anchor = new BlockPos(0, 1, 1);
        Schematic schematic = SchematicResources.load("palisade_section");
        SpiritEntity spirit = spawnWith(helper, new BlockPos(4, 1, 6),
                new ItemStack(Items.OAK_FENCE, 7),
                new ItemStack(Items.OAK_FENCE_GATE, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, buildCall(helper, "palisade_section", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "палисад-секция по пресету обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int matches = countMatches(helper, schematic, anchor);
            helper.assertTrue(matches == schematic.blocks().size(),
                    "палисад обязан совпасть с пресетом блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size());
            GameTestFixtures.finish(helper);
        });
    }
}