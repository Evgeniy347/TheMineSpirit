package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Самовыход замурованного духа (INC-45, требование владельца): дух в каменном кармане на 7 блоков ниже поверхности, с
 * деревянной киркой и булыжником, сам копает лестницу вверх и выходит под открытое небо.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class DigOutGameTests {
    private static final int TOP = 12;
    private static final int POCKET = TOP - 7;

    private DigOutGameTests() {}

    // [флак] 7 ступеней по 3 блока камня деревянной киркой (~70 тиков на ступень) и шаги; запас 2× к ~1000 тиков
    @GameTest(template = "mine_arena", batch = "dig_out_pocket", timeoutTicks = 2400)
    public static void digOut_SealedPocketSevenDeep_ReachesSurface(GameTestHelper helper) {
        pocket(helper, List.of(new BlockPos(2, POCKET, 5), new BlockPos(2, POCKET + 1, 5)));
    }

    // Форма живого кармана INC-59: клетка духа, над ней воздух и проём сбоку на уровне головы — (x,y), (x,y+1),
    // (x-1,y+1); выше — сплошной камень.
    // [флак] та же лестница вверх 7 ступеней; запас 2× к ~1000 тиков
    @GameTest(template = "mine_arena", batch = "dig_out_side_gap", timeoutTicks = 2400)
    public static void digOut_PocketWithSideGap_ReachesSurface(GameTestHelper helper) {
        pocket(
                helper,
                List.of(new BlockPos(2, POCKET, 5), new BlockPos(2, POCKET + 1, 5), new BlockPos(1, POCKET + 1, 5)));
    }

    private static void pocket(GameTestHelper helper, List<BlockPos> air) {
        GameTestFixtures.reset(helper, 16);
        BoardRegistry.get(helper.getLevel()).board().restore(new BoardState(List.of(), Set.of()));
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= TOP; y++) {
                for (int z = 0; z <= 10; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        air.forEach(cell -> helper.setBlock(cell, Blocks.AIR));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, POCKET, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 48));
        BlockPos home = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));

        helper.succeedWhen(() -> {
            BlockPos feet = spirit.blockPosition();
            helper.assertTrue(
                    DigOutScript.open(helper.getLevel(), feet) || feet.getY() >= home.getY(),
                    "замурованный дух обязан сам выбраться наверх (INC-45): стоит в " + feet + ", задача "
                            + spirit.currentTaskScript().orElse("-") + " "
                            + spirit.taskStatus().orElse(null)
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            spirit.discard();
            GameTestFixtures.finish(helper, 16);
        });
    }
}
