package com.theminespirit.gametest;

import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EventBusSubscriber(modid = "theminespirit", value = Dist.DEDICATED_SERVER)
public final class GameTestSeeding {
    private static final Logger LOGGER = LogManager.getLogger();

    private GameTestSeeding() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer server) || !GeneratedScene.fixedSeed()) {
            return;
        }
        long seed = GeneratedScene.seed();
        server.overworld().random.setSeed(seed);
        LOGGER.info(
                "[scene] сид {}: случайность мира засеяна до старта тестов — место сетки тестов и случайные тики "
                        + "повторяются от прогона к прогону",
                seed);
    }
}
