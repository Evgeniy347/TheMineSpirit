package com.theminespirit.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoCity;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DemoCityGameTests {
    private static final int CITY_STAGE_ZERO = 1;
    private static final int SEARCH_RADIUS = 80;

    private DemoCityGameTests() {}

    private static int expectedBuildings() {
        return DemoCity.pick(
                        DemoBuildings.all(),
                        scheme -> DataTables.INSTANCE
                                .presets()
                                .current()
                                .byId(scheme)
                                .map(Preset::stage)
                                .orElse(Integer.MAX_VALUE),
                        CITY_STAGE_ZERO)
                .size();
    }

    private static List<SpiritEntity> spirits(GameTestHelper helper, BlockPos centre) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        SpiritEntity.class, new AABB(centre).inflate(SEARCH_RADIUS), spirit -> !spirit.isRemoved());
    }

    @SceneStep(template = "demo_arena", timeoutTicks = 200)
    public static void demoCity_StageZero_StartsOneSpiritPerBuildingAtOnce(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        BlockPos start = helper.absolutePos(new BlockPos(15, 1, 2));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(15, 1, 0), "gt_demo_city");
        CommandSourceStack source = player.server
                .createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
        int before = spirits(helper, start).size();
        int expected = expectedBuildings();

        int started = helper.getLevel()
                .getServer()
                .getCommands()
                .getDispatcher()
                .execute(
                        "spirit demo city " + CITY_STAGE_ZERO + " " + start.getX() + " " + start.getY() + " "
                                + start.getZ(),
                        source);

        helper.assertTrue(
                expected >= 2,
                "Этап 0 обязан показывать несколько построек (дом игрока, площадь, светильник, вход в шахту): "
                        + "в витрине " + expected);
        helper.assertTrue(
                started == expected,
                "Витрина обязана начать стройку каждой постройки этапа сразу: начато " + started + " из " + expected);
        int spawned = spirits(helper, start).size() - before;
        helper.assertTrue(
                spawned == expected,
                "На каждую постройку витрины — свой дух, все одновременно: появилось " + spawned + " из " + expected);
        int stopped =
                helper.getLevel().getServer().getCommands().getDispatcher().execute("spirit demo stop", source);
        helper.assertTrue(
                stopped == expected,
                "/spirit demo stop обязан убрать всех духов витрины: убрано " + stopped + " из " + expected);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
