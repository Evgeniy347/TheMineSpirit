package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClearScaffoldScenesGameTests {
    private static final int TALL = 9;
    private static final int OWNED_HEIGHT = 6;
    private static final BlockPos COLUMN = new BlockPos(5, 1, 5);
    private static final BlockPos OWNED = new BlockPos(7, 1, 7);
    private static final BlockPos SPIRIT = new BlockPos(5, 1, 6);

    private ClearScaffoldScenesGameTests() {}

    @GameTest(template = "mine_arena", batch = "scene_clear_column", timeoutTicks = 400)
    public static void clearScene_TallColumn_ClearedTopDownAndScaffoldReturnedToGround(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int dy = 0; dy < TALL; dy++) {
            helper.setBlock(COLUMN.above(dy), Blocks.DIRT);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SHOVEL));
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, TALL + 4));
        LeaseRun owned = LeaseRun.open(spirit, "scaffold");
        PlacedBlocksData placed = PlacedBlocksData.get(helper.getLevel());
        for (int dy = 0; dy < OWNED_HEIGHT; dy++) {
            BlockPos cell = OWNED.above(dy);
            helper.setBlock(cell, Blocks.DIRT);
            placed.markSpirit(helper.absolutePos(cell));
            owned.placed(helper.absolutePos(cell), "minecraft:dirt");
        }
        owned.orphan();
        BlockPos low = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos high = helper.absolutePos(new BlockPos(8, TALL, 8));
        ScriptCall clear = new ScriptCall(
                "clear_area",
                Map.of(
                        "x1", low.getX(),
                        "y1", low.getY(),
                        "z1", low.getZ(),
                        "x2", high.getX(),
                        "y2", high.getY(),
                        "z2", high.getZ()));
        SpiritRun run = SpiritRun.launch(helper, spirit, clear, 1);
        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            for (int dy = 0; dy < TALL; dy++) {
                helper.assertTrue(
                        helper.getBlockState(COLUMN.above(dy)).isAir(),
                        "чужой высокий столб разбирается сверху вниз до земли: в " + COLUMN.above(dy) + " остался "
                                + helper.getBlockState(COLUMN.above(dy)) + ", дух на " + spirit.blockPosition());
            }
            for (int dy = 0; dy < OWNED_HEIGHT; dy++) {
                helper.assertTrue(
                        helper.getBlockState(OWNED.above(dy)).is(Blocks.DIRT),
                        "клетки своей аренды не цель расчистки: их откатывает узел undo_lease, а не clear_area; " + "в "
                                + OWNED.above(dy) + " сейчас " + helper.getBlockState(OWNED.above(dy)));
            }
            Lease own = LeaseData.get(helper.getLevel()).book().require(owned.id());
            helper.assertTrue(
                    own.status() == LeaseStatus.ORPHANED && own.cells().size() == OWNED_HEIGHT,
                    "чужая для расчистки (своя духу) брошенная аренда остаётся брошенной и целой: статус "
                            + own.status() + ", клеток " + own.cells().size());
            List<Lease> scaffolds = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(lease -> "scaffold".equals(lease.purpose())
                            && lease.ownedBy(spirit.getUUID())
                            && !lease.id().equals(owned.id()))
                    .toList();
            helper.assertTrue(
                    scaffolds.size() == 1,
                    "подъём к высокой клетке работает на одной аренде подмостей, новых не плодит: аренд "
                            + scaffolds.size());
            helper.assertTrue(
                    scaffolds.get(0).status() == LeaseStatus.DONE,
                    "спуск по своим подмостям заканчивается на земле и закрывает аренду DONE, а не бросает столб: "
                            + scaffolds.get(0).status() + ", клеток "
                            + scaffolds.get(0).cells().size());
            helper.assertTrue(
                    spirit.getY() <= low.getY() + 0.5,
                    "дух обязан спуститься по своим подмостям на землю: стоит на " + spirit.blockPosition()
                            + ", земля на " + low.getY());
            for (int x = low.getX(); x <= high.getX(); x++) {
                for (int y = low.getY(); y <= high.getY(); y++) {
                    for (int z = low.getZ(); z <= high.getZ(); z++) {
                        BlockPos cell = new BlockPos(x, y, z);
                        boolean ownColumn = cell.getX()
                                        == helper.absolutePos(OWNED).getX()
                                && cell.getZ() == helper.absolutePos(OWNED).getZ();
                        helper.assertTrue(
                                ownColumn
                                        || helper.getLevel().getBlockState(cell).isAir(),
                                "после расчистки в ящике не остаётся подмостей-столбов: в " + cell + " стоит "
                                        + helper.getLevel().getBlockState(cell));
                    }
                }
            }
            spirit.discard();
            GameTestFixtures.finish(helper, 16);
        });
    }
}
