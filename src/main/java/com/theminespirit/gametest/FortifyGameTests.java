package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.intellect.FortifyTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.fortify.DismantleExecutor;
import com.theminespirit.neoforge.fortify.FortifyRegistry;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FortifyGameTests {
    private static final int WAIT_TICKS = 6000;
    private static final int OBSERVED_TICKS = 120;

    private FortifyGameTests() {
    }

    private static SpiritEntity builderWithBlocks(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        return spirit;
    }

    private static void threaten(GameTestHelper helper, SpiritEntity target, int count) {
        for (int index = 0; index < count; index++) {
            Mob mob = TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(2 + index, 1, 9));
            mob.setTarget(target);
        }
    }

    private static int spiritBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return PlacedBlocksData.get(level).spiritSize();
    }

    @GameTest(template = "arena", batch = "fortify_shelter", timeoutTicks = 300)
    public static void fortify_ManyEnemiesNoPassage_RaisesShelterWithoutBuryingBuilder(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        int before = spiritBlocks(helper);
        SpiritEntity builder = builderWithBlocks(helper, new BlockPos(5, 1, 5));
        SpiritRun run = SpiritRun.launch(helper, builder, MovementScene.call("wait", Map.of("ticks", WAIT_TICKS)),
                MovementScene.SETTLE_TICKS);
        threaten(helper, builder, FortifyTuning.FORTIFY_ENEMY_MIN);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(builder.tickCount >= OBSERVED_TICKS,
                    "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + builder.tickCount);
            int placed = spiritBlocks(helper) - before;
            helper.assertTrue(placed > 0,
                    "при " + FortifyTuning.FORTIFY_ENEMY_MIN + " врагах и без прохода интеллект обязан поднять укрытие, "
                            + "поставлено блоков " + placed + ", решений " + FortifyRegistry.get(helper.getLevel()).decisionCalls());
            BlockPos feet = builder.blockPosition();
            helper.assertTrue(!PlacedBlocksData.get(helper.getLevel()).isSpiritMarked(feet),
                    "строитель не замурован: его клетка не занята своим блоком");
            for (SpiritEntity spirit : spirits(helper, builder)) {
                spirit.discard();
            }
            TacticsScene.finish(helper, builder);
        });
    }

    @GameTest(template = "arena", batch = "fortify_barricade", timeoutTicks = 300)
    public static void fortify_ManyEnemiesNarrowPassage_SealsPassageInsteadOfShelter(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(3, y, 5, Blocks.STONE);
            helper.setBlock(7, y, 5, Blocks.STONE);
        }
        int before = spiritBlocks(helper);
        SpiritEntity builder = builderWithBlocks(helper, new BlockPos(5, 1, 5));
        SpiritRun run = SpiritRun.launch(helper, builder, MovementScene.call("wait", Map.of("ticks", WAIT_TICKS)),
                MovementScene.SETTLE_TICKS);
        threaten(helper, builder, FortifyTuning.FORTIFY_ENEMY_MIN);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(builder.tickCount >= OBSERVED_TICKS,
                    "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + builder.tickCount);
            int placed = spiritBlocks(helper) - before;
            helper.assertTrue(placed > 0,
                    "при проходе и " + FortifyTuning.FORTIFY_ENEMY_MIN + " врагах интеллект обязан запечатать проход, "
                            + "поставлено блоков " + placed);
            for (SpiritEntity spirit : spirits(helper, builder)) {
                spirit.discard();
            }
            TacticsScene.finish(helper, builder);
        });
    }

    @GameTest(template = "arena", batch = "fortify_none", timeoutTicks = 200)
    public static void fortify_FewEnemies_LeavesNoFortification(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        int before = spiritBlocks(helper);
        SpiritEntity builder = builderWithBlocks(helper, new BlockPos(5, 1, 5));
        SpiritRun run = SpiritRun.launch(helper, builder, MovementScene.call("wait", Map.of("ticks", WAIT_TICKS)),
                MovementScene.SETTLE_TICKS);
        threaten(helper, builder, FortifyTuning.FORTIFY_ENEMY_MIN - 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(builder.tickCount >= OBSERVED_TICKS,
                    "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + builder.tickCount);
            helper.assertTrue(spiritBlocks(helper) - before == 0,
                    "при " + (FortifyTuning.FORTIFY_ENEMY_MIN - 1) + " врагах укрепление не строится, поставлено "
                            + (spiritBlocks(helper) - before));
            for (SpiritEntity spirit : spirits(helper, builder)) {
                spirit.discard();
            }
            TacticsScene.finish(helper, builder);
        });
    }

    private static List<SpiritEntity> spirits(GameTestHelper helper, SpiritEntity builder) {
        return List.of(builder);
    }

    @GameTest(template = "arena", batch = "fortify_dismantle", timeoutTicks = 200)
    public static void dismantle_OwnAndForeignBlocks_RemovesOnlySpiritMarkedTopDown(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity builder = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        ServerLevel level = helper.getLevel();
        BlockPos ownLow = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos ownHigh = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos foreign = helper.absolutePos(new BlockPos(5, 1, 6));
        level.setBlock(ownLow, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(ownHigh, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(foreign, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        PlacedBlocksData.get(level).markSpirit(ownLow);
        PlacedBlocksData.get(level).markSpirit(ownHigh);
        PlacedBlocksData data = PlacedBlocksData.get(level);
        int markedBefore = data.spiritSize();

        DismantleExecutor executor = new DismantleExecutor(builder, List.of(
                new BlockCoord(ownLow.getX(), ownLow.getY(), ownLow.getZ()),
                new BlockCoord(ownHigh.getX(), ownHigh.getY(), ownHigh.getZ())));
        helper.onEachTick(executor::tick);

        helper.succeedWhen(() -> {
            helper.assertTrue(executor.done() || builder.tickCount >= OBSERVED_TICKS,
                    "разбор своих блоков обязан завершиться: снято " + executor.removed() + " из 2");
            helper.assertTrue(level.getBlockState(ownLow).isAir(), "свой нижний блок снят");
            helper.assertTrue(level.getBlockState(ownHigh).isAir(), "свой верхний блок снят");
            helper.assertTrue(!level.getBlockState(foreign).isAir(), "чужой немаркированный блок цел");
            helper.assertTrue(data.spiritSize() < markedBefore,
                    "пометка своих блоков снята: было " + markedBefore + ", стало " + data.spiritSize());
            executor.cancel();
            TacticsScene.finish(helper, builder);
        });
    }
}