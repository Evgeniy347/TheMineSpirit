package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsLeashGameTests {
    private static final int ZONE_RADIUS = 2;
    private static final int KILL_LIMIT_TICKS = 250;
    private static final int OBSERVED_TICKS = 120;
    private static final double RETURN_TOLERANCE = 2.0;

    private TacticsLeashGameTests() {}

    private static SpiritEntity guard(GameTestHelper helper, BlockPos post, BlockPos spiritAt) {
        SpiritEntity spirit = TacticsScene.spirit(helper, spiritAt);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        BlockPos absolute = helper.absolutePos(post);
        spirit.tactics().setGuardZone(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()), ZONE_RADIUS);
        spirit.tactics().setCombatMode(CombatMode.GUARD_AREA);
        return spirit;
    }

    private static Husk hostile(GameTestHelper helper, BlockPos at, SpiritEntity target) {
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, at);
        husk.setTarget(target);
        return husk;
    }

    private static boolean dead(Husk husk) {
        return husk.isDeadOrDying() || husk.isRemoved();
    }

    @GameTest(template = "arena", batch = "tactics_leash_inside", timeoutTicks = 400)
    public static void leash_TargetFourBlocksFromPostOfSix_SpiritChasesAndKills(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos post = new BlockPos(3, 1, 5);
        SpiritEntity spirit = guard(helper, post, post);
        Husk husk = hostile(helper, new BlockPos(7, 1, 5), spirit);
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {});

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    scene.ticks() <= KILL_LIMIT_TICKS, "цель внутри поводка убивают за " + KILL_LIMIT_TICKS + " тиков");
            helper.assertTrue(
                    dead(husk), "цель внутри поводка (4 из 6 блоков) обязана быть убита, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_leash_border", timeoutTicks = 300)
    public static void leash_TargetExactlyAtSixBlocksFromPostOfSix_SpiritDoesNotChaseBecauseBorderPressureIsMaximal(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos post = new BlockPos(3, 1, 5);
        SpiritEntity spirit = guard(helper, post, post);
        Husk husk = hostile(helper, new BlockPos(9, 1, 5), spirit);
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {});

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= OBSERVED_TICKS, "наблюдение за охранником ещё идёт: " + scene.ticks());
            helper.assertTrue(
                    spirit.tactics().combat().strikes() == 0,
                    "на границе поводка (запас 0) давление поводка максимально, и охранник не гонится: ударов "
                            + spirit.tactics().combat().strikes() + ", решение "
                            + spirit.tactics().explain().orElse("нет"));
            helper.assertTrue(
                    husk.getHealth() == husk.getMaxHealth(), "враг на границе не ранен, здоровье " + husk.getHealth());
            helper.assertTrue(
                    TacticsScene.distance(helper, spirit, post) <= RETURN_TOLERANCE,
                    "охранник остался на посту, расстояние " + TacticsScene.distance(helper, spirit, post));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_leash_outside", timeoutTicks = 400)
    public static void leash_TargetSevenBlocksFromPostOfSix_SpiritReturnsToPostAndDoesNotChase(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos post = new BlockPos(3, 1, 5);
        SpiritEntity spirit = guard(helper, post, new BlockPos(3, 1, 8));
        Husk husk = hostile(helper, new BlockPos(10, 1, 5), spirit);
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {});

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= OBSERVED_TICKS, "наблюдение за охранником ещё идёт: " + scene.ticks());
            helper.assertTrue(
                    TacticsScene.distance(helper, spirit, post) <= RETURN_TOLERANCE,
                    "цель за поводком (7 из 6 блоков): охранник вернулся к посту, расстояние "
                            + TacticsScene.distance(helper, spirit, post));
            helper.assertTrue(
                    spirit.tactics().combat().strikes() == 0,
                    "за поводок не гонятся: ударов " + spirit.tactics().combat().strikes());
            helper.assertTrue(
                    husk.getHealth() == husk.getMaxHealth(), "враг за поводком не ранен, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_leash_nozone", timeoutTicks = 400)
    public static void leash_GuardModeWithoutZone_FallsBackToDefensiveAndFightsAttackerSixBlocksAway(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(CombatMode.GUARD_AREA);
        Husk husk = hostile(helper, new BlockPos(8, 1, 5), spirit);
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {});

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    scene.ticks() <= KILL_LIMIT_TICKS,
                    "без зоны поводок режима не мешает: бой за " + KILL_LIMIT_TICKS + " тиков");
            helper.assertTrue(
                    dead(husk),
                    "без зоны охрана превращается в защиту на месте и бьёт напавшего, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }
}
