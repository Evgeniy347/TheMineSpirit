package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Упрощения механик «читом» (согласованы владельцем 18.10): магнит дропа, контейнер в досягаемости,
 * жила с места с дропом прямо в инвентарь, телепорт при застревании не сквозь стены, ферма со стоянки.
 * Каждое упрощение ограничено целью: чужие предметы, блоки вне цели и стены не трогаются.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SimplificationGameTests {
    private static final BlockPos SPIRIT_AT = new BlockPos(4, 1, 4);
    private static final String MAGNET_BATCH = "simplify_magnet";

    private SimplificationGameTests() {}

    @BeforeBatch(batch = MAGNET_BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = MAGNET_BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = MAGNET_BATCH, timeoutTicks = 100)
    public static void magnet_FreeDropNearbyAndYoungPlayerDrop_OnlyFreeDropPulled(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        ItemEntity near = GameTestFixtures.dropAt(helper, Items.IRON_INGOT, 3, 7.5, 1, 4.5);
        ItemEntity far = GameTestFixtures.dropAt(helper, Items.GOLD_INGOT, 2, 9.5, 1, 9.5);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(0, 1, 0), "gt_magnet_thrower");
        ItemEntity thrown = GameTestFixtures.dropAt(helper, Items.EMERALD, 1, 5.5, 1, 5.5);
        thrown.setThrower(player);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    near.isRemoved() && count(spirit.getInventory(), Items.IRON_INGOT) == 3,
                    "Дроп в 3 блоках обязан сам лечь в инвентарь (магнит, радиус 5): слитков у духа "
                            + count(spirit.getInventory(), Items.IRON_INGOT) + ", дроп на земле " + !near.isRemoved());
            helper.assertTrue(
                    !far.isRemoved() && count(spirit.getInventory(), Items.GOLD_INGOT) == 0,
                    "Дроп дальше радиуса магнита обязан остаться на земле");
            helper.assertTrue(
                    !thrown.isRemoved() && count(spirit.getInventory(), Items.EMERALD) == 0,
                    "Предмет, только что брошенный игроком, магнит не трогает: игрок мог бросить его другому");
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }

    // [флак] открытие сундука и перенос пяти блоков с места: запас под нагрузкой полного прогона.
    @GameTest(template = "arena", batch = "simplify_chest_reach", timeoutTicks = 200)
    public static void reach_ChestThreeBlocksAway_WithdrawsWithoutWalking(GameTestHelper helper) {
        MovementScene.prepare(helper);
        BlockPos chestAt = new BlockPos(7, 1, 4);
        helper.setBlock(chestAt, Blocks.CHEST);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(chestAt)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        }
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        BlockPos chestAbsolute = helper.absolutePos(chestAt);
        Map<String, Object> params = new HashMap<>();
        params.put("container", chestAbsolute.getX() + "," + chestAbsolute.getY() + "," + chestAbsolute.getZ());
        params.put("item", "minecraft:cobblestone");
        params.put("count", 5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, MovementScene.call("withdraw", params), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Сундук в 3 блоках — в досягаемости: withdraw обязан пройти, статус " + spirit.taskStatus()
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    count(spirit.getInventory(), Items.COBBLESTONE) == 5, "Из сундука взято ровно 5 булыжников");
            helper.assertTrue(
                    spirit.pathBuilds() == 0,
                    "Сундук в досягаемости открывается с места (упрощение 44.2): путь строился " + spirit.pathBuilds()
                            + " раз");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] две руды за стеной и дроп в инвентарь: ломка деревянной киркой с запасом.
    @GameTest(template = "arena", batch = "simplify_vein_reach", timeoutTicks = 300)
    public static void vein_OreBehindStoneWithinReach_MinedFromPlaceIntoInventory(GameTestHelper helper) {
        MovementScene.prepare(helper);
        for (int y = 1; y <= 3; y++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(6, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(6, y, 5), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(6, 3, 4), Blocks.STONE);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        Map<String, Object> params = Map.of("ore", "coal", "count", 2, "radius", 6);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, MovementScene.call("mine_vein", params), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Жила за каменной стенкой в досягаемости обязана добыться с места (упрощение 44.3): статус "
                            + spirit.taskStatus() + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    count(spirit.getInventory(), Items.COAL) >= 2,
                    "Уголь обязан лечь прямо в инвентарь (дроп целевого блока в инвентарь, решение владельца 18.10): "
                            + "у духа " + count(spirit.getInventory(), Items.COAL));
            helper.assertTrue(dropsAround(helper).isEmpty(), "На земле не осталось дропа: " + dropsAround(helper));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 1, 4)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(6, 3, 4)).is(Blocks.STONE),
                    "Ломается только руда этой жилы: камень стенки и над жилой остаётся на месте");
            helper.assertTrue(
                    spirit.pathBuilds() == 0,
                    "Руда в досягаемости ломается с места, без подхода: путь строился " + spirit.pathBuilds() + " раз");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] застревание детектируется за 40 тиков, затем перенос и спуск к цели.
    @GameTest(template = "arena", batch = "simplify_unstuck", timeoutTicks = 400)
    public static void unstuck_SpiritInTwoHighRing_TeleportsOverWallAndReachesTarget(GameTestHelper helper) {
        MovementScene.prepare(helper);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                if (x == 4 && z == 4) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        BlockPos target = new BlockPos(8, 1, 4);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.pointCall(helper, "goto", target), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Дух в кольце высотой 2 застревает, перенос на 1–3 блока к цели (упрощение 44.6) обязан вывести его, "
                            + "статус " + spirit.taskStatus() + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.teleports() >= 1, "Выход из кольца — только переносом: переносов " + spirit.teleports());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "simplify_unstuck_wall", timeoutTicks = 300)
    public static void unstuck_SealedCell_NeverTeleportsThroughWalls(GameTestHelper helper) {
        MovementScene.prepare(helper);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x == 4 && z == 4 && y < 3) {
                        continue;
                    }
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                MovementScene.pointCall(helper, "goto", new BlockPos(8, 1, 4)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "Из замурованной клетки выхода нет: перенос не сквозь стены (решение владельца 18.10), goto обязан "
                            + "провалиться, статус " + spirit.taskStatus());
            helper.assertTrue(
                    spirit.teleports() == 0 && spirit.blockPosition().equals(helper.absolutePos(SPIRIT_AT)),
                    "Дух остаётся в своей клетке: переносов " + spirit.teleports() + ", стоит "
                            + spirit.blockPosition().toShortString());
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] три зрелые клетки пшеницы с места и подбор урожая в инвентарь.
    @GameTest(template = "arena", batch = "simplify_farm_reach", timeoutTicks = 300)
    public static void farm_MatureWheatWithinReach_HarvestedFromPlace(GameTestHelper helper) {
        MovementScene.prepare(helper);
        List<BlockPos> cells = List.of(new BlockPos(6, 0, 4), new BlockPos(6, 0, 5), new BlockPos(4, 0, 6));
        helper.setBlock(new BlockPos(5, 0, 6), Blocks.WATER);
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.FARMLAND);
            helper.setBlock(cell.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        }
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT_AT);
        Map<String, Object> params = Map.of("crop", "wheat", "radius", 6, "replant", false);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.call("harvest_crop", params), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Три зрелые клетки в досягаемости обязаны собраться, статус " + spirit.taskStatus() + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        !helper.getBlockState(cell.above()).is(Blocks.WHEAT),
                        "Клетка " + cell + " обязана быть собрана");
            }
            helper.assertTrue(
                    count(spirit.getInventory(), Items.WHEAT) >= 3,
                    "Урожай лежит в инвентаре духа: пшеницы " + count(spirit.getInventory(), Items.WHEAT));
            helper.assertTrue(
                    spirit.pathBuilds() == 0,
                    "Грядка в досягаемости собирается со стоянки без хождения по клеткам (упрощение 44.9): путь "
                            + "строился " + spirit.pathBuilds() + " раз");
            GameTestFixtures.finish(helper);
        });
    }

    private static int count(Container inventory, Item item) {
        return GameTestFixtures.countInInventory(inventory, item);
    }

    private static List<ItemEntity> dropsAround(GameTestHelper helper) {
        Vec3 min = helper.absoluteVec(new Vec3(0, 0, 0));
        Vec3 max = helper.absoluteVec(new Vec3(11, 6, 11));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(min, max), ItemEntity::isAlive);
    }
}
