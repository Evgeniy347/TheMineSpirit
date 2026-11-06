package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsRetreatGameTests {
    private static final int KITE_LIMIT_TICKS = 150;
    private static final double SLOW_ENEMY_SPEED = 0.05;
    private static final int ARROWS = 32;

    private TacticsRetreatGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void retreatKite_LowHpWithBowSlowHusk_BacksAwayAndHitsItWithArrows(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.BOW));
        spirit.getInventory().addItem(new ItemStack(Items.ARROW, ARROWS));
        BlockPos huskAt = new BlockPos(0, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        TacticsScene.slowDown(husk, SLOW_ENEMY_SPEED);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, TacticsScene.LOW_HP);
        float startHealth = husk.getHealth();
        int[] hitAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (hitAt[0] < 0 && husk.getHealth() < startHealth) {
                hitAt[0] = scene.ticks();
            }
            if (hitAt[0] < 0 && scene.ticks() > KITE_LIMIT_TICKS) {
                helper.fail("стрела не попала во врага за " + KITE_LIMIT_TICKS + " тиков, выстрелов "
                        + spirit.tactics().combat().shots() + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(hitAt[0] >= 0, "стрела ещё не попала во врага");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.KITE,
                    "исполняемый отход — KITE, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(spirit.tactics().combat().strikes() == 0, "кайт не рукопашная: ударов мечом нет");
            helper.assertTrue(
                    spirit.distanceTo(husk) > 4.0,
                    "дух держит дистанцию больше начальной, сейчас " + spirit.distanceTo(husk));
            TacticsScene.finish(helper, spirit);
        });
    }
}
