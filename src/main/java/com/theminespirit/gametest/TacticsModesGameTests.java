package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsModesGameTests {
    private static final float FULL_HP = 20.0F;
    private static final int PASSIVE_OBSERVED_TICKS = 100;
    private static final int KILL_LIMIT_TICKS = 200;
    private static final int GUARD_ZONE_RADIUS = 1;
    private static final double GUARD_LEASH = 5.0;

    private TacticsModesGameTests() {
    }

    private static boolean dead(Husk husk) {
        return husk.isDeadOrDying() || husk.isRemoved();
    }

    @GameTest(template = "arena", batch = "tactics_mode_passive", timeoutTicks = 300)
    public static void passive_HuskAttacksArmedSpirit_SpiritNeverStrikesAndRunsAway(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(CombatMode.PASSIVE);
        BlockPos huskAt = new BlockPos(5, 1, 10);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, FULL_HP);
        boolean[] retreated = {false};
        helper.onEachTick(() -> {
            if (scene.started()) {
                retreated[0] |= spirit.tactics().lastOutcome().map(o -> o.decision() == TacticalOption.RETREAT).orElse(false);
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= PASSIVE_OBSERVED_TICKS, "наблюдение за пассивным духом ещё идёт: " + scene.ticks());
            helper.assertTrue(retreated[0], "пассивный дух обязан отходить от нападающего, иначе тест ничего не доказывает");
            helper.assertTrue(spirit.tactics().combat().strikes() == 0, "PASSIVE не бьёт никогда: ударов "
                    + spirit.tactics().combat().strikes());
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "враг не ранен, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_mode_defensive", timeoutTicks = 300)
    public static void defensive_TwoHusksOnlyFarOneAttacks_SpiritKillsAttackerAndLeavesPeacefulOne(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        TacticsScene.wearIronArmor(spirit);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(CombatMode.DEFENSIVE);
        Husk peaceful = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 1));
        BlockPos attackerAt = new BlockPos(5, 1, 10);
        Husk attacker = TacticsScene.watcher(helper, EntityType.HUSK, attackerAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, attacker, attackerAt, true, FULL_HP);

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(dead(attacker), "бьющий первым враг обязан пасть, его здоровье " + attacker.getHealth() + ", решение "
                    + spirit.tactics().explain().orElse("нет") + ", ударов " + spirit.tactics().combat().strikes()
                    + ", позиция " + helper.relativeVec(spirit.position()) + ", враг " + helper.relativeVec(attacker.position()));
            helper.assertTrue(peaceful.getHealth() == peaceful.getMaxHealth(),
                    "DEFENSIVE не трогает того, кто не бил: здоровье мирного " + peaceful.getHealth());
            helper.assertTrue(spirit.tactics().combat().strikes() >= 2, "убить за несколько ударов: ударов "
                    + spirit.tactics().combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_mode_protect", timeoutTicks = 400)
    public static void protect_HuskAttacksWardSpirit_ProtectorKillsItAndWardLives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity ward = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        ward.tactics().setCombatMode(CombatMode.PASSIVE);
        SpiritEntity protector = TacticsScene.spirit(helper, new BlockPos(3, 1, 5));
        protector.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        protector.tactics().setCombatMode(CombatMode.PROTECT);
        protector.tactics().setWard(ward.getUUID());
        BlockPos huskAt = new BlockPos(9, 1, 9);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = PolicyScene.launch(helper, protector, TacticsScene.SETTLE_TICKS, () -> {
            husk.setPos(helper.absoluteVec(new Vec3(huskAt.getX() + 0.5, huskAt.getY(), huskAt.getZ() + 0.5)));
            husk.setNoAi(false);
            husk.setTarget(ward);
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(dead(husk), "напавший на подопечного враг обязан пасть от защитника, здоровье " + husk.getHealth());
            helper.assertTrue(ward.isAlive(), "подопечный жив");
            helper.assertTrue(protector.tactics().combat().strikes() >= 2, "защитник бил: ударов "
                    + protector.tactics().combat().strikes());
            ward.discard();
            TacticsScene.finish(helper, protector);
        });
    }

    @GameTest(template = "arena", batch = "tactics_mode_guard", timeoutTicks = 300)
    public static void guardArea_HuskInsideZoneAndHuskFarOutside_SpiritKillsInsideOneAndStaysOnPost(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos post = new BlockPos(1, 1, 5);
        SpiritEntity spirit = TacticsScene.spirit(helper, post);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        BlockPos absolute = helper.absolutePos(post);
        spirit.tactics().setGuardZone(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()), GUARD_ZONE_RADIUS);
        spirit.tactics().setCombatMode(CombatMode.GUARD_AREA);
        Husk inside = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(2, 1, 5));
        Husk outside = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(9, 1, 5));

        helper.succeedWhen(() -> {
            helper.assertTrue(dead(inside), "враг в зоне охраны обязан пасть, здоровье " + inside.getHealth());
            helper.assertTrue(outside.getHealth() == outside.getMaxHealth(),
                    "враг далеко за зоной и поводком не тронут, здоровье " + outside.getHealth());
            helper.assertTrue(TacticsScene.distance(helper, spirit, post) <= GUARD_LEASH,
                    "охранник не уходит от поста дальше поводка " + GUARD_LEASH + ": " + TacticsScene.distance(helper, spirit, post));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_mode_aggressive", timeoutTicks = 400)
    public static void aggressive_PeacefulHuskAtElevenBlocks_SpiritGoesAndKillsIt(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(CombatMode.AGGRESSIVE);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(9, 1, 9));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() <= KILL_LIMIT_TICKS, "AGGRESSIVE обязан убить врага за " + KILL_LIMIT_TICKS + " тиков");
            helper.assertTrue(dead(husk), "AGGRESSIVE нападает на любого врага в радиусе, здоровье " + husk.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }
}
