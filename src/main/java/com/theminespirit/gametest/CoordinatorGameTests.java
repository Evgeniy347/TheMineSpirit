package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoordinatorGameTests {
    private CoordinatorGameTests() {
    }

    private static WorkTarget tree(GameTestHelper helper, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        return new WorkTarget(WorkKind.TREE, new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workReserve_TwoSpiritsOneTarget_SecondRefusedUntilRelease(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity first = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity second = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(first, target),
                "Первая бронь цели встаёт: иначе распределение работ не защищает цель");
        helper.assertTrue(!WorkReserveHub.reserve(second, target),
                "Второй дух на ту же цель получает отказ: иначе двое рубят одно дерево");
        helper.assertTrue(WorkReserveHub.release(first, target), "Владелец снимает свою бронь");
        helper.assertTrue(WorkReserveHub.reserve(second, target),
                "После release цель свободна: работу забирает следующий дух");
        helper.assertTrue(WorkReserveHub.releaseAll(second) == 1,
                "releaseAll снимает бронь духа: выгрузка оставляет цель свободной");

        TacticsScene.finish(helper, first);
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workChoose_TargetUnderForeignReservation_NotTaken(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity seeker = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget taken = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, taken), "Владелец занял цель");
        helper.assertTrue(WorkReserveHub.choose(seeker, List.of(taken)).isEmpty(),
                "choose не берёт чужую бронь: занятая цель не возвращается");
        helper.assertTrue(WorkReserveHub.releaseAll(owner) == 1, "releaseAll снимает бронь владельца");
        helper.assertTrue(WorkReserveHub.choose(seeker, List.of(taken)).isPresent(),
                "После снятия брони цель снова свободна для выбора");

        TacticsScene.finish(helper, owner);
        seeker.discard();
        helper.succeed();
    }
}
