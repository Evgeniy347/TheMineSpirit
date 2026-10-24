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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Дух строит постройку по пресету из своей библиотеки: материалы выданы в инвентарь, тело пресета читается
 * тем же {@code build_schematic}, а итог сверяется с телом схемы. Проверяются склад-хижина (этап 1) с крышей
 * и сундуками внутри, простой каменный дом (этап 2) с крышей и палисад-секция (этап 0).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresetBuildGameTests {
    private static final int SETTLE_TICKS = 5;

    private PresetBuildGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static void wideFloor(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
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

    private static String mismatchReport(GameTestHelper helper, Schematic schematic, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        BlockCoord origin = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        StringBuilder report = new StringBuilder();
        int shown = 0;
        for (Schematic.PlacedEntry entry : schematic.entries(origin)) {
            var state = helper.getLevel()
                    .getBlockState(new BlockPos(
                            entry.pos().x(), entry.pos().y(), entry.pos().z()));
            if (!state.is(BlockStates.blockFor(entry.block())) && shown < 12) {
                report.append(entry.block())
                        .append("@")
                        .append(entry.pos())
                        .append("->")
                        .append(state.getBlock())
                        .append("; ");
                shown++;
            }
        }
        return report.toString();
    }

    private static int countOf(GameTestHelper helper, Schematic schematic, BlockPos anchor, String block) {
        BlockPos absolute = helper.absolutePos(anchor);
        BlockCoord origin = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        int matches = 0;
        for (Schematic.PlacedEntry entry : schematic.entries(origin)) {
            if (entry.block().equals(block)
                    && helper.getLevel()
                            .getBlockState(new BlockPos(
                                    entry.pos().x(),
                                    entry.pos().y(),
                                    entry.pos().z()))
                            .is(BlockStates.blockFor(block))) {
                matches++;
            }
        }
        return matches;
    }

    // [флак] склад-хижина с места, с запасом на подходы; тест меряет результат, а не скорость.
    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 2400)
    public static void preset_StorageHutFromLibrary_StandsBlockForBlock(GameTestHelper helper) {
        buildPreset(helper, "storage_hut", "склад-хижина");
    }

    // [флак] каменный дом с места, с запасом на подходы; тест меряет результат, а не скорость.
    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 2400)
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

    // [флак] чаровальня уровня 1 дух ставит по одному за тик с подходом к каждой клетке и лесами; потолок с запасом,
    // иначе тест меряет скорость стройки, а не её результат.
    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 24000)
    public static void preset_EnchantRoomV1FromLibrary_StandsWithFiveShelves(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        wideFloor(helper);
        BlockPos anchor = new BlockPos(1, 1, 1);
        Schematic schematic = SchematicResources.load("enchant_room_v1");
        SpiritEntity spirit = spawnWith(
                helper,
                new BlockPos(0, 1, 0),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_LOG, 16),
                new ItemStack(Items.SPRUCE_PLANKS, 64),
                new ItemStack(Items.BOOKSHELF, 5),
                new ItemStack(Items.ENCHANTING_TABLE, 1),
                new ItemStack(Items.TORCH, 4),
                new ItemStack(Items.GLASS_PANE, 3),
                new ItemStack(Items.OAK_DOOR, 1),
                new ItemStack(Items.COBBLESTONE_STAIRS, 1),
                new ItemStack(Items.RED_CARPET, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, buildCall(helper, "enchant_room_v1", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "чаровальня уровня 1 по пресету обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int matches = countMatches(helper, schematic, anchor);
            helper.assertTrue(
                    matches == schematic.blocks().size(),
                    "постройка обязана совпасть с пресетом блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size() + "; расхождения: "
                            + mismatchReport(helper, schematic, anchor));
            int shelves = countOf(helper, schematic, anchor, "minecraft:bookshelf");
            helper.assertTrue(
                    shelves == 5,
                    "в чаровальне уровня 1 обязано стоять 5 книжных полок под 10 ступеней: на месте " + shelves
                            + " — уровень растёт полками, а не начинает с максимума");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "preset_build", timeoutTicks = 400)
    public static void preset_PalisadeSectionFromLibrary_StandsBlockForBlock(GameTestHelper helper) {
        prepare(helper);
        BlockPos anchor = new BlockPos(0, 1, 1);
        Schematic schematic = SchematicResources.load("palisade_section");
        SpiritEntity spirit = spawnWith(
                helper,
                new BlockPos(4, 1, 6),
                new ItemStack(Items.OAK_FENCE, 7),
                new ItemStack(Items.TORCH, 2),
                new ItemStack(Items.OAK_FENCE_GATE, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, buildCall(helper, "palisade_section", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "палисад-секция по пресету обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int matches = countMatches(helper, schematic, anchor);
            helper.assertTrue(
                    matches == schematic.blocks().size(),
                    "палисад обязан совпасть с пресетом блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size());
            GameTestFixtures.finish(helper);
        });
    }
}
