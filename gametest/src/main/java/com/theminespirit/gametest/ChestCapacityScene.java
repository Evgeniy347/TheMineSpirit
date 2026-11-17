package com.theminespirit.gametest;

import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class ChestCapacityScene {
    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final BlockPos warehouse;
    private final SpiritRun run;
    private boolean checked;

    ChestCapacityScene(GameTestHelper helper, SpiritEntity spirit, BlockPos left, BlockPos right, BlockPos warehouse) {
        this.helper = helper;
        this.spirit = spirit;
        this.warehouse = warehouse;
        helper.assertTrue(
                SettlementChestStore.get(helper.getLevel()).chests().all().isEmpty(),
                "Без доступного склада нельзя разгрузить полный инвентарь перед разбором");
        run = SpiritRun.launch(
                helper,
                spirit,
                ChestPlans.breakOnly(List.of(new BlockCoord(left.getX(), left.getY(), left.getZ()))),
                5);
        helper.onEachTick(() -> {
            if (!checked && run.started()) {
                helper.assertTrue(
                        helper.getLevel().getBlockState(left).is(Blocks.CHEST)
                                && helper.getLevel().getBlockState(right).is(Blocks.CHEST),
                        "Если полный инвентарь некуда разгрузить, обе половины сундука должны остаться целыми");
            }
        });
    }

    void assertDone() {
        if (checked) {
            return;
        }
        run.assertStarted();
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                "Разбор с полным инвентарём и без склада обязан отказать до слома");
        helper.assertTrue(
                spirit.taskFailureReason().orElse("").contains("склада"),
                "Разбор остановлен именно из-за невозможности разгрузки, а не другой ошибки");
        SettlementChestStore.get(helper.getLevel()).track(warehouse);
        checked = true;
    }
}
