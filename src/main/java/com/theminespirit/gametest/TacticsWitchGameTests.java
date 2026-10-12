package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsWitchGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OBSERVED_TICKS = 100;
    private static final int ARROWS = 32;
    private static final int MIN_SHOTS = 2;

    private TacticsWitchGameTests() {
    }

    private static void coverWallNorthWest(GameTestHelper helper) {
        for (int z = 8; z <= 10; z++) {
            helper.setBlock(3, 1, z, Blocks.STONE);
            helper.setBlock(3, 2, z, Blocks.STONE);
        }
    }

    private static SpiritEntity swordsman(GameTestHelper helper, CombatMode mode) {
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(0, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(mode);
        return spirit;
    }

    @GameTest(template = "arena", batch = "tactics_witch_sword", timeoutTicks = 300)
    public static void witch_TenBlocksCoverFourFullHpSword_SpiritFightsAndNeverRetreats(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        coverWallNorthWest(helper);
        SpiritEntity spirit = swordsman(helper, CombatMode.DEFENSIVE);
        Witch witch = TacticsScene.watcher(helper, EntityType.WITCH, new BlockPos(10, 1, 5));
        PolicyScene scene = PolicyScene.launch(helper, spirit, TacticsScene.SETTLE_TICKS,
                () -> spirit.tactics().setCombatMode(CombatMode.AGGRESSIVE));
        boolean[] retreated = {false};
        boolean[] fought = {false};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            spirit.tactics().lastOutcome().ifPresent(outcome -> {
                retreated[0] |= outcome.decision() == TacticalOption.RETREAT;
                fought[0] |= outcome.decision() == TacticalOption.FIGHT && outcome.action() == TacticalAction.ENGAGE;
                if (retreated[0]) {
                    helper.fail("полное HP и меч против ведьмы в 10 блоках с укрытием рядом: дух обязан драться, а не уходить: "
                            + spirit.tactics().explain().orElse("нет"));
                }
            });
            if (scene.ticks() % 10 == 2 && scene.ticks() < 40) {
                LOGGER.info("witch_sword t={}: {}", scene.ticks(), spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= OBSERVED_TICKS, "наблюдение за боем ещё идёт: " + scene.ticks());
            helper.assertTrue(fought[0], "дух обязан решить FIGHT/ENGAGE");
            helper.assertTrue(!retreated[0], "дух ни разу не отступил");
            helper.assertTrue(spirit.tactics().combat().strikes() >= 1 || witch.getHealth() < witch.getMaxHealth(),
                    "дух дошёл до ведьмы и бьёт: ударов " + spirit.tactics().combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "tactics_witch_bow", timeoutTicks = 300)
    public static void witch_TenBlocksCoverFourFullHpBowAndSword_SpiritShootsAndNeverRetreats(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        coverWallNorthWest(helper);
        SpiritEntity spirit = swordsman(helper, CombatMode.AGGRESSIVE);
        spirit.getInventory().addItem(new ItemStack(Items.BOW));
        spirit.getInventory().addItem(new ItemStack(Items.ARROW, ARROWS));
        TacticsScene.watcher(helper, EntityType.WITCH, new BlockPos(10, 1, 5));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {
        });
        boolean[] retreated = {false};
        boolean[] shooting = {false};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            spirit.tactics().lastOutcome().ifPresent(outcome -> {
                retreated[0] |= outcome.decision() == TacticalOption.RETREAT;
                shooting[0] |= outcome.decision() == TacticalOption.FIGHT && outcome.action() == TacticalAction.SHOOT;
                if (retreated[0]) {
                    helper.fail("полное HP, лук и меч против ведьмы в 10 блоках с укрытием рядом: дух обязан драться: "
                            + spirit.tactics().explain().orElse("нет"));
                }
            });
            if (scene.ticks() % 10 == 2 && scene.ticks() < 40) {
                LOGGER.info("witch_bow t={}: {}", scene.ticks(), spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(scene.ticks() >= OBSERVED_TICKS, "наблюдение за боем ещё идёт: " + scene.ticks());
            helper.assertTrue(shooting[0], "с луком против ведьмы дальше 5 блоков дух решает FIGHT/SHOOT");
            helper.assertTrue(spirit.tactics().combat().shots() >= MIN_SHOTS,
                    "дух стреляет: выстрелов " + spirit.tactics().combat().shots());
            TacticsScene.finish(helper, spirit);
        });
    }
}
