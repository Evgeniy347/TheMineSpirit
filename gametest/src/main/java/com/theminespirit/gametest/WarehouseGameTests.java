package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarehouseGameTests {
    private static final int SETTLE_TICKS = 5;

    private WarehouseGameTests() {}

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int i = 0; i < stacks.length; i++) {
            spirit.getInventory().setItem(i, stacks[i]);
        }
        return spirit;
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static ScriptCall build(GameTestHelper helper, String file, BlockPos anchor) {
        BlockPos pos = helper.absolutePos(anchor);
        return new ScriptCall(
                "build_schematic", Map.of("file", file, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
    }

    // [флак] 176 блоков склада дух ставит по одному за тик с подходом к каждой клетке; запас двойной,
    // иначе тест меряет скорость стройки, а не её результат.
    @SceneStep(template = "arena", timeoutTicks = 12000)
    public static void warehouse_LevelOneBuilt_AllChestsOpenable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("storage_v1");
        SpiritEntity spirit = spawnWith(
                helper,
                new BlockPos(7, 1, 4),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_LOG, 16),
                new ItemStack(Items.CHEST, 18),
                new ItemStack(Items.OAK_STAIRS, 1),
                new ItemStack(Items.TORCH, 1));
        BlockPos anchor = new BlockPos(1, 1, 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, build(helper, "storage_v1", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Склад уровня 1 обязан построиться целиком: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", прогресс "
                            + spirit.taskProgress().orElse(null));
            int chests = 0;
            int blocked = 0;
            List<String> missing = new ArrayList<>();
            for (Schematic.PlacedEntry entry : schematic.entries(absolute(helper, anchor))) {
                if (!"minecraft:chest".equals(entry.block())) {
                    continue;
                }
                chests++;
                BlockPos pos = new BlockPos(
                        entry.pos().x(), entry.pos().y(), entry.pos().z());
                if (!helper.getLevel().getBlockState(pos).is(Blocks.CHEST)) {
                    missing.add(pos.toShortString() + "="
                            + helper.getLevel().getBlockState(pos).getBlock()
                            + "@local("
                            + (entry.pos().x() - helper.absolutePos(anchor).getX()) + ","
                            + (entry.pos().y() - helper.absolutePos(anchor).getY()) + ","
                            + (entry.pos().z() - helper.absolutePos(anchor).getZ()) + ")");
                }
                if (ChestBlock.isChestBlockedAt(helper.getLevel(), pos)) {
                    blocked++;
                }
            }
            helper.assertTrue(
                    missing.isEmpty(),
                    "Сундук схемы не поставлен: " + missing + "; статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", дух в "
                            + spirit.blockPosition().toShortString() + ", сундуков в инвентаре "
                            + BlockItems.countIn(spirit.getInventory(), "minecraft:chest"));
            helper.assertTrue(
                    chests == 18, "Склад уровня 1 обязан вмещать 18 блоков сундуков (9 двойных), фактически " + chests);
            helper.assertTrue(
                    blocked == 0,
                    "Все сундуки склада, включая верхний, обязаны открываться: заблокировано " + blocked + " из "
                            + chests);
            GameTestFixtures.finish(helper);
        });
    }
}
