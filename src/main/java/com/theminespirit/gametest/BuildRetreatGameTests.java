package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Правило отхода (владелец 20.10, постоянное): дух не ставит блок, после которого ему не выйти из постройки, — сперва
 * переходит на стоянку снаружи. Сцена — ниша 1×1 в стене высотой 2 ({@code retreat_nook}), дух стоит внутри.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildRetreatGameTests {
    // [флак] 18 блоков ниши и выход наружу перед последним — ≈ 300 тиков, запас под нагрузкой полного прогона.
    private static final int TIMEOUT_TICKS = 1200;
    private static final int SETTLE_TICKS = 5;

    private BuildRetreatGameTests() {}

    // Правило отхода (владелец 20.10): дух в нише не ставит пробку проёма изнутри — сперва выходит, потом ставит.
    @SceneStep(template = "arena", timeoutTicks = TIMEOUT_TICKS)
    public static void build_SpiritInNook_PlugsOpeningFromOutside(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setDemo(true);
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 32));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of("file", "retreat_nook", "x", origin.getX(), "y", origin.getY(), "z", origin.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Стройка ниши обязана завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            BlockPos feet = spirit.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
            helper.assertTrue(
                    !(feet.getX() == 4 && feet.getZ() == 4),
                    "Дух обязан выйти из ниши до пробки проёма: замурован в " + feet.toShortString());
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 2, 3));
            GameTestFixtures.finish(helper);
        });
    }
}
