package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.RallyTuning;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(coordinator = true)
public final class CombatRallyGameTests {
    private static final BlockPos HOME = new BlockPos(1, 1, 1);
    private static final BlockPos CALLER_AT = new BlockPos(3, 1, 5);
    private static final BlockPos ALLY_AT = new BlockPos(9, 1, 5);
    private static final BlockPos FIRST_ZOMBIE_AT = new BlockPos(3, 1, 2);
    private static final BlockPos SECOND_ZOMBIE_AT = new BlockPos(3, 1, 9);
    private static final float LOW_HP = 4.0F;
    private static final double RALLY_DISTANCE = 3.0;
    private static final int DISCOVERY_LIMIT_TICKS = 200;

    private CombatRallyGameTests() {}

    @GameTest(template = "arena", batch = "combat_rally", timeoutTicks = 400)
    public static void rally_LowHpBesideTwoZombies_RunsToAllyAndAllyJoinsFight(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity caller = TacticsScene.spirit(helper, CALLER_AT);
        SpiritEntity ally = swordsman(helper, ALLY_AT);
        caller.setHome(MovementScene.coord(helper, HOME));
        ally.setHome(MovementScene.coord(helper, HOME));
        ally.startTask(new ScriptCall("wait", Map.of("ticks", 6000)));
        Zombie first = TacticsScene.watcher(helper, EntityType.ZOMBIE, FIRST_ZOMBIE_AT);
        Zombie second = TacticsScene.watcher(helper, EntityType.ZOMBIE, SECOND_ZOMBIE_AT);
        first.setTarget(caller);
        second.setTarget(caller);
        double startDistance = caller.position().distanceTo(ally.position());
        PolicyScene scene = TacticsScene.ambush(helper, caller, first, FIRST_ZOMBIE_AT, false, LOW_HP);
        int[] ralliedAt = {-1};
        int[] arrivedAt = {-1};
        int[] helpedAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (ralliedAt[0] < 0 && caller.tactics().retreat().method() == RetreatMethod.RALLY) {
                ralliedAt[0] = scene.ticks();
            }
            if (arrivedAt[0] < 0 && caller.position().distanceTo(ally.position()) <= RALLY_DISTANCE) {
                arrivedAt[0] = scene.ticks();
            }
            if (helpedAt[0] < 0 && ally.tactics().combatMode() == CombatMode.PROTECT) {
                helpedAt[0] = scene.ticks();
            }
            if (ralliedAt[0] < 0 && scene.ticks() > DISCOVERY_LIMIT_TICKS) {
                helper.fail("дух с двумя зомби не выбрал сбор к союзнику за " + DISCOVERY_LIMIT_TICKS + " тиков, "
                        + "решение " + caller.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    ralliedAt[0] >= 0,
                    "отход обязан идти к союзнику (RALLY), а не просто от врага; решение "
                            + caller.tactics().explain().orElse("нет"));
            helper.assertTrue(
                    arrivedAt[0] >= 0,
                    "дух не дошёл до союзника: расстояние " + caller.position().distanceTo(ally.position()) + " из "
                            + startDistance);
            helper.assertTrue(
                    helpedAt[0] >= 0,
                    "союзник не получил зов через координатор и не встал на защиту: режим "
                            + ally.tactics().combatMode());
            helper.assertTrue(
                    ally.currentTaskScript().isPresent(),
                    "занятый дух обязан сохранить свою задачу, пока помогает: иначе после боя ему нечего возобновлять");
            helper.assertTrue(
                    ally.getTarget() != null || ally.tactics().combat().strikes() > 0,
                    "союзник обязан вступить в бой, а не только сменить режим; цель " + ally.getTarget() + ", ударов "
                            + ally.tactics().combat().strikes());
            TacticsScene.finish(helper, caller);
            ally.discard();
        });
    }

    // [флак] зов повторяется не раньше 200 тиков: первый и второй разнесены на интервал, наблюдение
    // занимает ~300 тиков со сдвигом набора игрока — потолок 500 держит двукратный запас.
    @GameTest(template = "arena", batch = "combat_rally_call", timeoutTicks = 500)
    public static void call_PlayerNearAndTwoZombies_WrittenNotMoreOftenThanInterval(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<Component> messages = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 5), "gt_rally_owner", messages);
        SpiritEntity caller = TacticsScene.spirit(helper, CALLER_AT);
        caller.setHome(MovementScene.coord(helper, HOME));
        Zombie first = TacticsScene.watcher(helper, EntityType.ZOMBIE, FIRST_ZOMBIE_AT);
        Zombie second = TacticsScene.watcher(helper, EntityType.ZOMBIE, SECOND_ZOMBIE_AT);
        first.setTarget(caller);
        second.setTarget(caller);
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        List<Integer> callTicks = new ArrayList<>();
        int[] seen = {0};
        helper.onEachTick(() -> {
            if (caller.tickCount >= TacticsScene.SETTLE_TICKS && caller.getHealth() != LOW_HP) {
                caller.setHealth(LOW_HP);
            }
            if (messages.size() > seen[0]) {
                callTicks.add(caller.tickCount);
                seen[0] = messages.size();
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    callTicks.size() >= 2,
                    "за время наблюдения зов обязан повториться: иначе частота не проверена, " + "сообщений "
                            + messages.size());
            for (int index = 1; index < callTicks.size(); index++) {
                int gap = callTicks.get(index) - callTicks.get(index - 1);
                helper.assertTrue(
                        gap >= tuning.callIntervalTicks(),
                        "зовы в чат " + callTicks.get(index - 1) + " и " + callTicks.get(index) + " ближе предела "
                                + tuning.callIntervalTicks() + " тиков: игрок получает спам вместо просьбы");
            }
            helper.assertTrue(
                    messages.stream().allMatch(message -> message.getString().startsWith("нужна помощь у ")),
                    "текст зова обязан называть место, получено " + messages);
            TacticsScene.finish(helper, caller);
        });
    }

    private static SpiritEntity swordsman(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        TacticsScene.wearIronArmor(spirit);
        return spirit;
    }
}
