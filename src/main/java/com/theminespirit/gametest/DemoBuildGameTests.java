package com.theminespirit.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Демо-стройка /spirit demo в креативе: объём очищается, сундук и дух появляются, постройка совпадает
 * со схемой, а после «Готово» дух пропадает. Отдельно проверяется улучшение загона 1→2 на месте.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoBuildGameTests {
    private DemoBuildGameTests() {
    }

    private static CommandSourceStack source(GameTestHelper helper, ServerPlayer player) {
        return player.server.createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static String demo(String building, BlockPos anchor) {
        return "spirit demo " + building + " " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ();
    }

    private static List<SpiritEntity> spiritsNear(GameTestHelper helper, BlockPos absolute, int radius) {
        AABB box = new AABB(absolute).inflate(radius);
        return helper.getLevel().getEntitiesOfClass(SpiritEntity.class, box, spirit -> !spirit.isRemoved());
    }

    private static int countMatches(GameTestHelper helper, Schematic schematic, BlockPos anchorRelative) {
        BlockPos absolute = helper.absolutePos(anchorRelative);
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

    // [флак] 67 блоков склада дух ставит по одному за тик с подходом к каждой клетке; потолок с запасом,
    // иначе тест меряет скорость стройки, а не её результат.
    @GameTest(template = "demo_arena", batch = "demo_build", timeoutTicks = 2600)
    public static void demo_StorageHutCommand_ChestAndSpiritBuildThenSpiritLeaves(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        BlockPos anchorRelative = new BlockPos(15, 1, 15);
        BlockPos anchor = helper.absolutePos(anchorRelative);
        helper.setBlock(10, 1, 14, Blocks.DIRT);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_demo_hut");

        int accepted = dispatcher(helper).execute(demo("storage_hut", anchor) + " none", source(helper, player));
        helper.assertTrue(accepted == 1,
                "Демо-стройка обязана начаться по команде оператора: результат=" + accepted + ", причина отказа "
                        + "в чате команды");
        BlockPos chest = new BlockPos(13, 1, 15);
        helper.assertTrue(helper.getBlockState(chest).is(Blocks.CHEST),
                "Сундук с материалами обязан появиться на площадке в стороне от входа сразу: без него дух "
                        + "ничего не построит");
        List<SpiritEntity> spawned = spiritsNear(helper, anchor, 5);
        helper.assertTrue(!spawned.isEmpty(),
                "Дух-строитель обязан появиться у площадки: демонстрация без духа нечего показывать");
        SpiritEntity spirit = spawned.get(0);
        helper.assertTrue(helper.getBlockState(new BlockPos(10, 1, 14)).isAir(),
                "Площадка вокруг постройки обязана очиститься сразу: блок в её объёме остался на месте");

        Schematic schematic = SchematicResources.load("storage_hut");
        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.isRemoved(),
                    "После «Готово» дух обязан пропасть: он остался у постройки");
            int matches = countMatches(helper, schematic, new BlockPos(13, 1, 16));
            helper.assertTrue(matches == schematic.blocks().size(),
                    "Постройка обязана совпасть со схемой блок в блок: поставлено " + matches + " из "
                            + schematic.blocks().size());
            helper.assertTrue(helper.getBlockState(chest).is(Blocks.CHEST),
                    "Сундук после стройки остаётся на месте (пустым): он часть демонстрации");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] Загон строится двумя уровнями: дух проходит подход к 4+7 клеткам забора с лесами; потолок с запасом,
    // иначе тест меряет скорость стройки, а не её результат.
    @GameTest(template = "demo_arena", batch = "demo_upgrade", timeoutTicks = 2600)
    public static void demo_PenUpgradeOneToTwo_LeavesBiggerPen(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        BlockPos anchorRelative = new BlockPos(15, 1, 15);
        BlockPos anchor = helper.absolutePos(anchorRelative);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_demo_pen");

        int accepted = dispatcher(helper).execute("spirit demo pen upgrade 1 2 " + anchor.getX() + " "
                + anchor.getY() + " " + anchor.getZ() + " none", source(helper, player));
        helper.assertTrue(accepted == 1,
                "Улучшение загона обязано начаться по команде: результат=" + accepted);
        List<SpiritEntity> spawned = spiritsNear(helper, anchor, 5);
        helper.assertTrue(!spawned.isEmpty(), "Дух-строитель обязан появиться у площадки");
        SpiritEntity spirit = spawned.get(0);

        Schematic bigPen = SchematicResources.load("pen_7x7");
        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.isRemoved(),
                    "После улучшения дух обязан пропасть: он остался у загона");
            int matches = countMatches(helper, bigPen, new BlockPos(12, 1, 16));
            helper.assertTrue(matches == bigPen.blocks().size(),
                    "После upgrade 1→2 загон обязан совпасть со схемой 7×7: поставлено " + matches + " из "
                            + bigPen.blocks().size());
            helper.assertTrue(helper.getBlockState(new BlockPos(15, 1, 20)).isAir(),
                    "Старый забор младшего уровня обязан исчезнуть: улучшение оставило лишний забор внутри");
            GameTestFixtures.finish(helper);
        });
    }
}
