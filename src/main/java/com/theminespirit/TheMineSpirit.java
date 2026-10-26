package com.theminespirit;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.DataReload;
import com.theminespirit.neoforge.MobTargetEvents;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SettlementChestWatch;
import com.theminespirit.neoforge.SettlementChunkLifecycle;
import com.theminespirit.neoforge.SettlementChunkTickets;
import com.theminespirit.neoforge.SpiritChunkTickets;
import com.theminespirit.neoforge.SpiritCommands;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritDropEvents;
import com.theminespirit.neoforge.board.BoardLifecycle;
import com.theminespirit.neoforge.board.CraterWatch;
import com.theminespirit.neoforge.board.FenceWatch;
import com.theminespirit.neoforge.coordinator.CoordinatorLifecycle;
import com.theminespirit.neoforge.crater.CraterEvents;
import com.theminespirit.neoforge.demo.DemoLifecycle;
import com.theminespirit.neoforge.doors.DoorWatchService;
import com.theminespirit.neoforge.experience.SpiritExperienceEvents;
import com.theminespirit.neoforge.inventory.ContainerOpenService;
import com.theminespirit.neoforge.movement.PathNetworkService;
import com.theminespirit.neoforge.net.BoardViewChannel;
import com.theminespirit.neoforge.net.BuildFingerprint;
import com.theminespirit.neoforge.net.ModProtocolHandshake;
import com.theminespirit.neoforge.net.TownMapChannel;
import com.theminespirit.neoforge.policy.PolicyEvents;
import com.theminespirit.neoforge.respawn.SpiritRespawnService;
import com.theminespirit.neoforge.rest.RestConfig;
import com.theminespirit.neoforge.rest.RestLifecycle;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(ModIdentity.MOD_ID)
public final class TheMineSpirit {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TheMineSpirit(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModProtocolHandshake.register(modEventBus);
        BoardViewChannel.register(modEventBus);
        TownMapChannel.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(SpiritChunkTickets::registerControllers);
        modEventBus.addListener(SettlementChunkTickets::registerControllers);
        modContainer.registerConfig(ModConfig.Type.SERVER, SpiritConfig.spec());
        modContainer.registerConfig(ModConfig.Type.SERVER, RestConfig.spec(), RestConfig.FILE_NAME);
        NeoForge.EVENT_BUS.addListener(SpiritCommands::register);
        NeoForge.EVENT_BUS.addListener(DataReload::register);
        DoorWatchService.register();
        ContainerOpenService.register();
        PolicyEvents.register(modContainer, modEventBus);
        RestLifecycle.register(NeoForge.EVENT_BUS);
        CoordinatorLifecycle.register(NeoForge.EVENT_BUS);
        BoardLifecycle.register(NeoForge.EVENT_BUS);
        DemoLifecycle.register(NeoForge.EVENT_BUS);
        SettlementChunkLifecycle.register(NeoForge.EVENT_BUS);
        SettlementChestWatch.register(NeoForge.EVENT_BUS);
        CraterWatch.register(NeoForge.EVENT_BUS);
        FenceWatch.register(NeoForge.EVENT_BUS);
        PathNetworkService.register(NeoForge.EVENT_BUS);
        MobTargetEvents.register(NeoForge.EVENT_BUS);
        CraterEvents.register(NeoForge.EVENT_BUS);
        new SpiritExperienceEvents(NeoForge.EVENT_BUS);
        new SpiritDropEvents(NeoForge.EVENT_BUS);
        SpiritRespawnService.register(NeoForge.EVENT_BUS);
        LOGGER.info("The Mine Spirit {} загружен", modContainer.getModInfo().getVersion());
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                LOGGER.info("The Mine Spirit {}: сервер запущен", BuildFingerprint.version()));
    }
}
