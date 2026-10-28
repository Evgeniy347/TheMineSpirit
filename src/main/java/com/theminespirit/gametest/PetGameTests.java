package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PetWatch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetGameTests {
    private static final int SPIRITS = 6;

    private PetGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 40, board = true, calmMobs = true)
    public static void pets_TwoWolvesWithBeef_BornPuppy(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int index = 0; index < SPIRITS; index++) {
            SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1 + index));
            spirit.setHome(absolute(helper, new BlockPos(3, 1, 3)));
            spirits.add(spirit);
        }
        SpiritEntity worker = spirits.get(0);
        worker.getInventory().addItem(new ItemStack(Items.BEEF, 4));
        Wolf first = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 2));
        Wolf second = helper.spawn(EntityType.WOLF, new BlockPos(3, 1, 2));
        for (Wolf wolf : List.of(first, second)) {
            wolf.setTame(true, false);
            wolf.setAge(0);
            wolf.setNoAi(true);
        }
        PetWatch watch = new PetWatch();

        helper.succeedWhen(() -> {
            watch.tick(level, spirits, level.getGameTime());
            long puppies = level
                    .getEntitiesOfClass(Wolf.class, first.getBoundingBox().inflate(16))
                    .stream()
                    .filter(Wolf::isBaby)
                    .count();
            helper.assertTrue(
                    puppies == 1,
                    "Две приручённые волчицы с мясом в руках духа обязаны дать одного щенка, щенков " + puppies);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(worker.getInventory(), Items.BEEF) == 2,
                    "На пару уходит ровно 2 мяса из 4, осталось "
                            + GameTestFixtures.countInInventory(worker.getInventory(), Items.BEEF));
            GameTestFixtures.finish(helper);
        });
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
