package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungerGameTests {
    private static final int BREAD_STOCK = 8;
    private static final int PEACEFUL_OBSERVE_TICKS = 40;
    private static final int FIGHT_OBSERVE_TICKS = 20;
    private static final int TOUGH_HUSK_HEALTH = 200;
    private static final int HUNGRY_LEVEL = 2;
    private static final int SAVED_FOOD = 7;
    private static final double SAVED_SATURATION = 3.5;
    private static final double SAVED_EXHAUSTION = 1.25;

    private HungerGameTests() {
    }

    private static int bread(SpiritEntity spirit) {
        return GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD);
    }

    @GameTest(template = "arena", batch = "hunger_eat", timeoutTicks = 200)
    public static void hungry_WithBreadInInventory_EatsAndSatietyRises(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.BREAD, BREAD_STOCK));
        spirit.hunger().restore(SpiritHunger.of(HUNGRY_LEVEL, 0, 0));
        int threshold = spirit.hunger().rules().eatFoodThreshold();

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.hunger().foodLevel() > threshold,
                    "голодный дух с хлебом обязан поесть до сытости выше порога " + threshold + ", фактически "
                            + spirit.hunger().foodLevel() + ", вердикт " + spirit.policies().verdict()
                            + ", владелец " + spirit.policies().owner());
            helper.assertTrue(bread(spirit) < BREAD_STOCK,
                    "поедание обязано потратить хлеб из инвентаря: было " + BREAD_STOCK + ", стало " + bread(spirit));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "hunger_full", timeoutTicks = 200)
    public static void full_WithBreadInInventory_DoesNotEat(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.BREAD, BREAD_STOCK));
        int threshold = spirit.hunger().rules().eatFoodThreshold();

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.tickCount >= PEACEFUL_OBSERVE_TICKS,
                    "наблюдение ещё идёт: тик духа " + spirit.tickCount);
            helper.assertTrue(spirit.hunger().foodLevel() > threshold,
                    "дух на старте обязан быть сыт выше порога " + threshold + ", фактически "
                            + spirit.hunger().foodLevel());
            helper.assertTrue(bread(spirit) == BREAD_STOCK,
                    "сытый дух не ест: хлеб обязан остаться весь, было " + BREAD_STOCK + ", стало " + bread(spirit));
            TacticsScene.finish(helper, spirit);
        });
    }

    // [флак] сцена длиннее прочих сцен голода: сперва бой под тактикой, затем обед после него
    @GameTest(template = "arena", batch = "hunger_combat", timeoutTicks = 600)
    public static void combat_WhileFightingThenPeaceful_StopsEatingThenEats(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.getInventory().addItem(new ItemStack(Items.BREAD, BREAD_STOCK));
        spirit.hunger().restore(SpiritHunger.of(HUNGRY_LEVEL, 0, 0));
        spirit.tactics().setCombatMode(CombatMode.DEFENSIVE);
        spirit.startTask(new ScriptCall("wait", Map.of("ticks", 3000)));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(7, 1, 5));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TOUGH_HUSK_HEALTH);
        husk.setHealth(TOUGH_HUSK_HEALTH);
        husk.setTarget(spirit);
        int[] foughtTicks = {0};
        int[] breadAtFightStart = {-1};
        boolean[] ateDuringFight = {false};
        helper.onEachTick(() -> {
            if (!husk.isAlive() || !spirit.tactics().wantsControl()) {
                return;
            }
            foughtTicks[0]++;
            int now = bread(spirit);
            if (breadAtFightStart[0] < 0) {
                breadAtFightStart[0] = now;
            } else if (now < breadAtFightStart[0]) {
                ateDuringFight[0] = true;
            }
            if (foughtTicks[0] >= FIGHT_OBSERVE_TICKS) {
                husk.discard();
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(foughtTicks[0] >= FIGHT_OBSERVE_TICKS,
                    "бой обязан идти под управлением тактики не меньше " + FIGHT_OBSERVE_TICKS + " тиков: "
                            + foughtTicks[0] + ", хочет управления " + spirit.tactics().wantsControl());
            helper.assertTrue(!ateDuringFight[0],
                    "во время боя дух не ест: хлеб при начале боя " + breadAtFightStart[0] + ", стало " + bread(spirit));
            helper.assertTrue(!spirit.tactics().wantsControl(),
                    "после устранения врага тактика обязана отпустить управление: " + spirit.tactics().wantsControl());
            helper.assertTrue(spirit.hunger().foodLevel() > spirit.hunger().rules().eatFoodThreshold(),
                    "после боя голодный дух обязан поесть до сытости выше порога "
                            + spirit.hunger().rules().eatFoodThreshold() + ", фактически " + spirit.hunger().foodLevel());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "hunger_save", timeoutTicks = 100)
    public static void saveAndReload_AfterRestart_KeepsSatiety(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        SpiritHunger before = SpiritHunger.of(SAVED_FOOD, SAVED_SATURATION, SAVED_EXHAUSTION);
        spirit.hunger().restore(before);
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        spirit.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);

        helper.assertTrue(reloaded.hunger().state().equals(before),
                "сытость духа обязана пережить сохранение: до " + before + ", после " + reloaded.hunger().state());
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }
}
