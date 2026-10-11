package com.theminespirit;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritChunkTickets;
import com.theminespirit.neoforge.SpiritCommands;
import com.theminespirit.neoforge.SpiritConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ModIdentity.MOD_ID)
public final class TheMineSpirit {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TheMineSpirit(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(SpiritChunkTickets::registerControllers);
        modContainer.registerConfig(ModConfig.Type.SERVER, SpiritConfig.spec());
        NeoForge.EVENT_BUS.addListener(SpiritCommands::register);
        LOGGER.info("The Mine Spirit {} загружен", modContainer.getModInfo().getVersion());
    }
}
