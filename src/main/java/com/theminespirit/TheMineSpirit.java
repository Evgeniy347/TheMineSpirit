package com.theminespirit;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ModIdentity.MOD_ID)
public final class TheMineSpirit {
    private static final Logger LOGGER = LogUtils.getLogger();

    public TheMineSpirit(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("The Mine Spirit {} загружен", modContainer.getModInfo().getVersion());
    }
}
