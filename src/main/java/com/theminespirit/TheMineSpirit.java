package com.theminespirit;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ModIdentity.MOD_ID)
public final class TheMineSpirit {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TheMineSpirit(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        NeoForge.EVENT_BUS.addListener(SpiritCommands::register);
        LOGGER.info("The Mine Spirit {} загружен", modContainer.getModInfo().getVersion());
    }
}
