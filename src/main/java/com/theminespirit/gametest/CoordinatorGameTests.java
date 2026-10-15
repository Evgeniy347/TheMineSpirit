package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.coordinator.SpiritSnapshotReader;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
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

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workCancelTask_ReservedTarget_ReleasesIt(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, target), "Дух занял цель перед задачей");
        owner.cancelTask();
        helper.assertTrue(WorkReserveHub.choose(owner, List.of(target)).isPresent(),
                "Отмена задачи освобождает цель: бронь не держится за снятым заданием");
        owner.cancelTask();
        helper.assertTrue(WorkReserveHub.releaseAll(owner) == 0,
                "Повторное снятие броней без них — не ошибка: выгрузка безопасна");

        TacticsScene.finish(helper, owner);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_work", timeoutTicks = 100)
    public static void workSpiritRemoved_ReservedTarget_ReleasedForOther(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity owner = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        SpiritEntity other = TacticsScene.spirit(helper, new BlockPos(6, 1, 5));
        WorkTarget target = tree(helper, new BlockPos(5, 1, 5));

        helper.assertTrue(WorkReserveHub.reserve(owner, target), "Дух занял цель");
        owner.discard();
        helper.assertTrue(WorkReserveHub.reserve(other, target),
                "Выгрузка духа освобождает его брони: цель не остаётся занятой навсегда");

        TacticsScene.finish(helper, other);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_lifecycle", timeoutTicks = 200)
    public static void coordinator_RegistryQuery_SameInstancePerLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SpiritCoordinator first = CoordinatorRegistry.get(level);

        helper.assertTrue(first == CoordinatorRegistry.get(level),
                "Два запроса на уровень отдают один координатор: иначе на уровень приходится два плана");
        CoordinatorRegistry.remove(level);
        helper.assertTrue(first != CoordinatorRegistry.get(level),
                "После remove реестр выдаёт новый координатор: выгруженный уровень не держит старое состояние");
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_snapshot", timeoutTicks = 100)
    public static void snapshotRead_AttackerJustBeyondThreatRadius_StillCountsAsThreat(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 22));
        husk.setPos(helper.absoluteVec(new Vec3(5.5, 1.0, 21.8)));
        husk.setTarget(spirit);

        SpiritSnapshot snapshot = SpiritSnapshotReader.read(spirit, helper.getLevel().getGameTime());

        helper.assertTrue(snapshot.attacked(),
                "Моб с целью на духа считается напавшим, даже стоя за радиусом угроз: иначе координатор не зовёт помощь");
        helper.assertTrue(snapshot.threatsNear() >= 1,
                "Атакованный дух видит хотя бы одну угрозу: иначе снимок противоречит сам себе и роняет тик");

        husk.discard();
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "coordinator_lifecycle", timeoutTicks = 200)
    public static void coordinator_ServerTicks_BuildsSnapshotWithoutManualTick(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        helper.succeedWhen(() -> {
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel()).snapshot().tick() > 0,
                    "Слушатель координатора зарегистрирован: снимок уровня строится сам, без ручного tick()");
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel()).snapshot().spirits().stream()
                            .anyMatch(snapshot -> snapshot.id().equals(spirit.getUUID())),
                    "Снимок координатора видит духа уровня: иначе координатор слеп");
            TacticsScene.finish(helper, spirit);
        });
    }
}
