package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
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
    private TacticsModesGameTests() {}

    private static boolean dead(Husk husk) {
        return husk.isDeadOrDying() || husk.isRemoved();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void protect_HuskAttacksWardSpirit_ProtectorKillsItAndWardLives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity ward = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        ward.tactics().setCombatMode(CombatMode.PASSIVE);
        SpiritEntity protector = TacticsScene.spirit(helper, new BlockPos(3, 1, 5));
        protector.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        TacticsScene.giveHome(helper, protector, new BlockPos(0, 1, 0));
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
            helper.assertTrue(
                    dead(husk), "напавший на подопечного враг обязан пасть от защитника, здоровье " + husk.getHealth());
            helper.assertTrue(ward.isAlive(), "подопечный жив");
            helper.assertTrue(
                    protector.tactics().combat().strikes() >= 2,
                    "защитник бил: ударов " + protector.tactics().combat().strikes());
            ward.discard();
            TacticsScene.finish(helper, protector);
        });
    }
}
