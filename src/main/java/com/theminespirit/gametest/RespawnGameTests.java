package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnGameTests {
    private static final int TEST_DELAY_TICKS = 40;
    private static final int OBSERVED_TICKS = 60;

    private RespawnGameTests() {}

    @GameTest(template = "arena", batch = "respawn", timeoutTicks = 200)
    public static void respawn_SpiritDies_ReturnsToPlotAnchor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        floor(helper, 5, 5);
        SpiritEntity dead = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        UUID id = dead.getUUID();
        String name = dead.displayName();
        BlockCoord anchor = absolute(helper, new BlockPos(5, 1, 5));
        SettlementPlotStore.get(helper.getLevel())
                .fix(new SettlementPlot(anchor, anchor.y(), anchor.y(), 1, WoodSpecies.OAK));

        RespawnRules saved = DataTables.INSTANCE.respawn().current();
        TableStatus status = DataTables.INSTANCE.respawn().status();
        try {
            DataTables.INSTANCE
                    .respawn()
                    .replace(
                            new RespawnRules(TEST_DELAY_TICKS),
                            status.source(),
                            status.origin(),
                            status.hash(),
                            Clock.systemUTC());
            dead.hurt(dead.damageSources().genericKill(), 1000.0F);
            helper.assertTrue(
                    !dead.isAlive(), "Погибший дух обязан перестать быть живым: иначе сцена не проверяет возрождение");
        } finally {
            DataTables.INSTANCE
                    .respawn()
                    .replace(saved, status.source(), status.origin(), status.hash(), Clock.systemUTC());
        }

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            Optional<SpiritEntity> born = alive(helper, id);
            helper.assertTrue(
                    born.isPresent(),
                    "Через задержку дух обязан возродиться на базе поселения: без этого поселение теряет духа навсегда");
            SpiritEntity revived = born.orElseThrow();
            helper.assertTrue(
                    name.equals(revived.displayName()),
                    "Возрождённый дух обязан сохранить имя '" + name + "', а не получить новое: "
                            + revived.displayName());
            helper.assertTrue(
                    revived.blockPosition().equals(helper.absolutePos(new BlockPos(5, 1, 5))),
                    "Дух обязан возродиться в якоре участка " + anchor + ", а не в " + revived.blockPosition());
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }

    private static void floor(GameTestHelper helper, int centerX, int centerZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(new BlockPos(centerX + dx, 0, centerZ + dz), Blocks.STONE);
            }
        }
    }

    private static Optional<SpiritEntity> alive(GameTestHelper helper, UUID id) {
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof SpiritEntity spirit
                    && spirit.isAlive()
                    && spirit.getUUID().equals(id)) {
                return Optional.of(spirit);
            }
        }
        return Optional.empty();
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
