package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.defense.DefenderOrder;
import com.theminespirit.core.intellect.defense.DefensePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PolicyVerdict;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefenseGameTests {
    private static final float SWELL_CAP = 0.5F;

    private DefenseGameTests() {}

    private static BlockCoord homeOf(GameTestHelper helper, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static SpiritEntity swordsman(GameTestHelper helper, BlockPos at, BlockCoord home, boolean armored) {
        SpiritEntity spirit = TacticsScene.spirit(helper, at);
        spirit.setHome(home);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        if (armored) {
            TacticsScene.wearIronArmor(spirit);
        }
        return spirit;
    }

    private static Optional<DefenderOrder> orderOf(DefensePlan plan, SpiritEntity spirit) {
        return plan.orders().stream()
                .filter(order -> order.defender().equals(spirit.getUUID()))
                .findFirst();
    }

    private static String taskFacts(SpiritEntity... spirits) {
        StringBuilder facts = new StringBuilder();
        for (SpiritEntity spirit : spirits) {
            if (facts.length() > 0) {
                facts.append("; ");
            }
            facts.append(spirit.displayName())
                    .append(" задача=")
                    .append(spirit.taskStatus().map(RunStatus::name).orElse("нет"))
                    .append("/")
                    .append(spirit.currentTaskScript().orElse("?"));
        }
        return facts.toString();
    }

    @SceneStep(template = "arena", timeoutTicks = 400, coordinator = true, board = true)
    public static void creeper_StrikeAndRetreat_ArmoredEngagesNakedHolds(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockCoord home = homeOf(helper, new BlockPos(5, 1, 5));
        SpiritEntity armored = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        armored.setHome(home);
        armored.getInventory().addItem(new ItemStack(Items.DIAMOND_SWORD));
        TacticsScene.wearIronArmor(armored);
        SpiritEntity naked = swordsman(helper, new BlockPos(6, 1, 4), home, false);
        Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(4, 1, 5));
        creeper.setPersistenceRequired();
        creeper.targetSelector.removeAllGoals(goal -> true);
        creeper.setTarget(armored);
        TacticsScene.slowDown(creeper, 0.04);
        boolean[] swelled = {false};
        boolean[] retreated = {false};
        boolean[] approachedAgain = {false};
        boolean[] nakedHolds = {false};
        long[] firstSwell = {-1};
        long[] firstReflex = {-1};
        String[] reflexText = {null};
        long[] nakedReflex = {-1};
        String[] nakedReflexText = {null};
        long[] creeperGone = {-1};
        String[] creeperTarget = {"?"};
        float[] maxSwell = {0.0F};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (creeper.isAlive()) {
                holdCreeperOnArmored(creeper, armored, naked, maxSwell, creeperTarget);
            }
            if (firstSwell[0] < 0 && creeper.isAlive() && creeper.getSwelling(1.0F) > 0.0F) {
                firstSwell[0] = tick;
            }
            noteFirstReflex(tick, armored, firstReflex, reflexText);
            noteFirstReflex(tick, naked, nakedReflex, nakedReflexText);
            if (creeperGone[0] < 0 && !creeper.isAlive()) {
                creeperGone[0] = tick;
            }
        });

        helper.succeedWhen(() -> {
            String duel = "тик " + helper.getTick() + ", свелл@" + firstSwell[0] + " макс " + maxSwell[0]
                    + ", рефлекс брони@" + firstReflex[0] + " (" + reflexText[0] + "), рефлекс без брони@"
                    + nakedReflex[0] + " (" + nakedReflexText[0] + "), крипер целит " + creeperTarget[0]
                    + ", крипера нет@" + creeperGone[0] + ", дистанции " + armored.distanceTo(creeper) + "/"
                    + naked.distanceTo(creeper) + ", " + taskFacts(armored, naked);
            helper.assertTrue(
                    creeper.equals(armored.getTarget()),
                    "Мечник в железной броне идёт на крипера первым: иначе «ударь и беги» делает слабый и " + "гибнет; "
                            + duel);
            if (nakedOrderedOffCreeper(helper, naked, creeper)) {
                nakedHolds[0] = true;
            }
            helper.assertTrue(
                    nakedHolds[0],
                    "Мечник без брони на крипера не идёт: иначе приказ подвёл слабого первым вместо "
                            + "бронированного; " + taskFacts(armored, naked));
            assertStrikeAndRetreatCycle(helper, armored, creeper, duel, swelled, retreated, approachedAgain);
            helper.assertTrue(
                    armored.isAlive() && naked.isAlive(),
                    "Оба мечника пережили крипера: иначе дух стоял вплотную и поймал взрыв");
            creeper.discard();
            armored.discard();
            TacticsScene.finish(helper, naked);
        });
    }

    private static void holdCreeperOnArmored(
            Creeper creeper, SpiritEntity armored, SpiritEntity naked, float[] maxSwell, String[] creeperTarget) {
        creeper.setTarget(armored);
        if (creeper.getSwelling(1.0F) >= SWELL_CAP) {
            creeper.setSwellDir(-1);
        }
        maxSwell[0] = Math.max(maxSwell[0], creeper.getSwelling(1.0F));
        creeperTarget[0] = armored.equals(creeper.getTarget())
                ? "броня"
                : naked.equals(creeper.getTarget()) ? "без брони" : String.valueOf(creeper.getTarget());
    }

    private static void noteFirstReflex(long tick, SpiritEntity spirit, long[] firstTick, String[] text) {
        if (firstTick[0] < 0 && spirit.policies().verdict() instanceof PolicyVerdict.Reflex reflex) {
            firstTick[0] = tick;
            text[0] = reflex.policy() + ": " + reflex.reason();
        }
    }

    private static boolean nakedOrderedOffCreeper(GameTestHelper helper, SpiritEntity naked, Creeper creeper) {
        DefensePlan duelPlan = BoardRegistry.defense(helper.getLevel()).lastPlan();
        return orderOf(duelPlan, naked)
                .map(order -> order.target().isEmpty()
                        || !order.target().get().id().equals(creeper.getUUID().toString()))
                .orElse(false);
    }

    private static void assertStrikeAndRetreatCycle(
            GameTestHelper helper,
            SpiritEntity armored,
            Creeper creeper,
            String duel,
            boolean[] swelled,
            boolean[] retreated,
            boolean[] approachedAgain) {
        double distance = armored.distanceTo(creeper);
        if (creeper.getSwelling(1.0F) > 0.0F) {
            swelled[0] = true;
        }
        if (swelled[0] && distance >= 7.0) {
            retreated[0] = true;
        }
        if (retreated[0] && distance < 6.5) {
            approachedAgain[0] = true;
        }
        helper.assertTrue(
                swelled[0],
                "Крипер набухает рядом с духом в броне: иначе сближения не было и проверять нечего; " + duel);
        helper.assertTrue(
                retreated[0],
                "При набухании дух отходит за радиус взрыва: рефлекс ExplosionAvoidance не перебит приказом; " + duel);
        helper.assertTrue(
                approachedAgain[0],
                "Дух возвращается для нового удара: «ударь и беги» — цикл, а не бесконечный отход; " + duel);
    }
}
