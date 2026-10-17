package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import com.theminespirit.neoforge.defense.DefenseService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(coordinator = true)
public final class DefenseScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int OBSERVED_TICKS = 130;

    private DefenseScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "defense_scale", timeoutTicks = 400)
    public static void scale_TwentySpiritsDefense_QueriesBounded(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        long started = helper.getLevel().getGameTime();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                spirits.add(TacticsScene.spirit(helper, new BlockPos(2 + column, 1, 2 + row)));
            }
        }
        helper.assertTrue(spirits.size() == SPIRITS,
                "В тесте на масштаб " + SPIRITS + " духов: иначе предел ничего не доказывает");
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ());
        for (SpiritEntity spirit : spirits) {
            spirit.setHome(home);
            spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        }
        Zombie east = TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(8, 1, 4));
        Zombie eastSecond = TacticsScene.watcher(helper, EntityType.ZOMBIE, new BlockPos(8, 1, 6));
        Spider north = TacticsScene.watcher(helper, EntityType.SPIDER, new BlockPos(5, 1, 1));

        helper.succeedWhen(() -> {
            SettlementKey key = SettlementGroups.keyOf(spirits.get(0).home(), spirits.get(0).getUUID());
            SpiritCoordinator coordinator = CoordinatorRegistry.get(helper.getLevel(), key);
            DefenseService defense = coordinator.defense();
            long elapsed = helper.getLevel().getGameTime() - started;
            if (elapsed < OBSERVED_TICKS) {
                helper.assertTrue(false,
                        "Оборона ещё считает: прошло " + elapsed + " из " + OBSERVED_TICKS + " тиков наблюдения");
            }
            helper.assertTrue(CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS == 10,
                    "Интервал пересборки 10 тиков: число в тесте, факт — из production");
            helper.assertTrue(defense.planCalls() >= 2,
                    "Оборона пересчиталась дважды: иначе бюджеты делить не на что");
            helper.assertTrue(defense.planCalls() * CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS
                            <= elapsed + CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS,
                    "Пересчётов не больше одного на 10 тиков: иначе план собирается каждый тик");
            helper.assertTrue(defense.entityQueries() <= defense.planCalls(),
                    "Запросов сущностей не больше числа пересчётов: один сбор врагов на решение, "
                            + "а не проход «духи × все сущности»");
            helper.assertTrue(defense.blockReads() == 0,
                    "Без скелета рядом чтений блоков нет: зонд укрытий спит, пока не нужен");
            helper.assertTrue(coordinator.snapshot().spirits().size() == SPIRITS,
                    "В снимке все " + SPIRITS + " духов: иначе решение делят без части поселения");
            helper.assertTrue(defense.lastPlan().defendedPoint().isPresent(),
                    "Точка обороны — дом поселения: иначе посты встанут вокруг ничего");
            east.discard();
            eastSecond.discard();
            north.discard();
            spirits.forEach(SpiritEntity::discard);
            TacticsScene.finish(helper, spirits.get(0));
        });
    }
}
