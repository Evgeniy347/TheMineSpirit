package com.theminespirit.gametest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.Map;
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

    private MovementScene() {
    }

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
        return PlanValidator.validate(new ScriptCall(script, params), SpiritScripts.catalog(),
                SpiritScripts.conditionCatalog());
    }

    static TaskNode pointCall(GameTestHelper helper, String script, BlockPos relative) {
        BlockCoord at = coord(helper, relative);
        return call(script, Map.of("x", at.x(), "y", at.y(), "z", at.z()));
    }

    static double distanceTo(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        return spirit.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
    }

    static double eyeDistanceTo(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        Vec3 eye = spirit.position().add(0, EYE_HEIGHT, 0);
        return eye.distanceTo(Vec3.atCenterOf(helper.absolutePos(relative)));
    }

    // NeoForge 21.1 не даёт иного способа получить ServerPlayer, попавшего в players() уровня; метод помечен
    // deprecated, но в этой версии работает и удаления не произошло.
    @SuppressWarnings("removal")
    static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    static void finish(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
    }
}
