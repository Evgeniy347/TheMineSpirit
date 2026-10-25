package com.theminespirit.gametest;

import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class MovementScene {
    static final int SETTLE_TICKS = 5;
    static final double ARRIVAL = 1.25;
    static final double REACH = 4.5;
    static final double EYE_HEIGHT = 1.62;

    private static final AtomicInteger PLAYER_COUNTER = new AtomicInteger();

    private MovementScene() {}

    static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    static SpiritEntity spirit(GameTestHelper helper, BlockPos relative) {
        return helper.spawn(ModEntities.SPIRIT.get(), relative);
    }

    static BlockCoord coord(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    static TaskNode call(String script, Map<String, Object> params) {
        return PlanValidator.validate(
                new ScriptCall(script, params), SpiritScripts.catalog(), SpiritScripts.conditionCatalog());
    }

    static TaskNode pointCall(GameTestHelper helper, String script, BlockPos relative) {
        return call(script, pointParams(helper, relative));
    }

    static Map<String, Object> pointParams(GameTestHelper helper, BlockPos relative) {
        BlockCoord at = coord(helper, relative);
        return Map.of("x", at.x(), "y", at.y(), "z", at.z());
    }

    static double distanceTo(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        return spirit.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
    }

    static double eyeDistanceTo(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        Vec3 eye = spirit.position().add(0, EYE_HEIGHT, 0);
        return eye.distanceTo(Vec3.atCenterOf(helper.absolutePos(relative)));
    }

    static String facts(GameTestHelper helper, SpiritEntity spirit, long sceneDayTime) {
        NightLeash leash = NightLeash.withDefaultRules();
        long worldDayTime = helper.getLevel().getDayTime();
        double worldFraction = Math.floorMod(worldDayTime, 24000L) / 24000.0;
        double sceneFraction = Math.floorMod(sceneDayTime, 24000L) / 24000.0;
        BlockPos feet = spirit.blockPosition();
        BlockCoord cell = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        String inside = spirit.home()
                .map(home -> Boolean.toString(leash.insideSettlement(cell, home, leash.settlementRadius())))
                .orElse("нет дома");
        return "факты: мир t=" + worldDayTime + " (" + worldFraction + ", вечер="
                + leash.isEvening(worldFraction) + ")"
                + ", сцена t=" + sceneDayTime + " (" + sceneFraction + ")"
                + ", в поселении=" + inside
                + ", поводок взведён=" + spirit.nightLeashArmed()
                + ", режим тактики=" + spirit.tactics().combatMode()
                + ", тактика владеет=" + spirit.tactics().wantsControl()
                + ", мозг заморожен="
                + spirit.brainIfPresent()
                        .map(brain -> brain.suspendReason().orElse("нет"))
                        .orElse("нет мозга")
                + ", дом=" + spirit.home().map(Object::toString).orElse("нет")
                + ", лагерь=" + spirit.camp().map(Object::toString).orElse("нет");
    }

    static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative) {
        return GameTestFixtures.mockPlayer(helper, relative, "gt_move_player_" + PLAYER_COUNTER.incrementAndGet());
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    static void finish(GameTestHelper helper) {
        GameTestFixtures.finish(helper);
    }
}
