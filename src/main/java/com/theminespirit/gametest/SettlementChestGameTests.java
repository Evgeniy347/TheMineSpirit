package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.SettlementChests;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementChestGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int SUPPLY_DISTANCE = 60;
    private static final int BUILD_X = 20;
    private static final int BUILD_Z = 9;

    private SettlementChestGameTests() {}

    // [флак] дух идёт 60 блоков до склада и обратно: замер ~600 тиков, запас 2× на движение и заходы.
    @GameTest(template = "settlement_arena", batch = "settlement_chest_supply", timeoutTicks = 1600)
    public static void supply_ChestSixtyBlocksFromBuild_SpiritBringsMaterial(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        floor(helper);
        ServerLevel level = helper.getLevel();
        // Сцена одна в пачке: реестр — общий на уровень, чужие сундуки из прошлых пачек не должны подменять склад.
        clearRegistry(level);
        BlockPos chest = new BlockPos(BUILD_X + SUPPLY_DISTANCE, 1, BUILD_Z);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity warehouse = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chest));
        warehouse.setItem(0, new ItemStack(Items.COBBLESTONE, 17));
        placeEvent(helper, chest);

        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(BUILD_X + 2, 1, BUILD_Z));
        BlockPos origin = helper.absolutePos(new BlockPos(BUILD_X, 1, BUILD_Z));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of("file", "fixture_plate", "x", origin.getX(), "y", origin.getY(), "z", origin.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дух обязан принести материал из сундука в " + SUPPLY_DISTANCE
                            + " блоках и достроить плиту: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(17).equals(data.fields().get("placed")),
                    "все 17 блоков схемы обязаны встать из принесённого материала, итог "
                            + spirit.taskResult().map(Object::toString).orElse("пусто"));
            helper.assertTrue(
                    warehouse.isEmpty(),
                    "склад в " + SUPPLY_DISTANCE + " блоках обязан опустеть: материал принесён из него, осталось "
                            + warehouse.getItem(0));
            SettlementChestStore.get(level).forget(helper.absolutePos(chest));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "settlement_chests", timeoutTicks = 200)
    public static void chestEvents_PlaceAndBreak_TrackAndForget(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        ServerLevel level = helper.getLevel();
        SettlementChests chests = SettlementChestStore.get(level).chests();
        BlockPos relative = new BlockPos(4, 1, 4);
        int before = chests.size();
        helper.setBlock(relative, Blocks.CHEST);
        placeEvent(helper, relative);
        helper.assertTrue(
                chests.size() == before + 1,
                "событие установки обязано добавить сундук в реестр поселения: было " + before + ", стало "
                        + chests.size());
        helper.assertTrue(
                chests.nearest(coord(helper.absolutePos(relative)), 1).size() == 1,
                "поставленный сундук обязан находиться поиском ближайших: иначе дух его не увидит");

        BlockState state = level.getBlockState(helper.absolutePos(relative));
        helper.setBlock(relative, Blocks.AIR);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 1), "chest_breaker");
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, helper.absolutePos(relative), state, player));
        helper.assertTrue(
                chests.size() == before,
                "событие снятия обязано убрать сундук из реестра: было " + (before + 1) + ", стало " + chests.size());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "settlement_chests", timeoutTicks = 200)
    public static void chestStore_SavedAndLoaded_KeepsChests(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        ServerLevel level = helper.getLevel();
        SettlementChestStore store = new SettlementChestStore();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        store.track(pos);
        SettlementChestStore loaded = SettlementChestStore.load(
                store.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        BlockCoord expected = coord(pos);
        helper.assertTrue(
                loaded.chests().nearest(expected, 1).equals(List.of(expected)),
                "реестр сундуков обязан пережить сохранение и загрузку: ожидался " + expected + ", найден "
                        + loaded.chests().nearest(expected, 1));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 128; x++) {
            for (int z = 8; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int x = BUILD_X - 4; x <= BUILD_X + 6; x++) {
            for (int z = 6; z <= 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void clearRegistry(ServerLevel level) {
        SettlementChestStore store = SettlementChestStore.get(level);
        for (BlockCoord pos : store.chests().all()) {
            store.forget(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
    }

    private static void placeEvent(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, absolute), Blocks.AIR.defaultBlockState(), null));
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
