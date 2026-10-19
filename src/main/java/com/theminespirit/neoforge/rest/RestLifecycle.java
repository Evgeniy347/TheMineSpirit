package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.task.TaskRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

public final class RestLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static IEventBus gameBus;
    private static SpiritIndex index;
    private static RestServer server;

    private RestLifecycle() {}

    public static void register(IEventBus bus) {
        gameBus = bus;
        bus.addListener(RestLifecycle::onAboutToStart);
        bus.addListener(RestLifecycle::onStarted);
        bus.addListener(RestLifecycle::onStopping);
        bus.addListener(RestLifecycle::onStopped);
    }

    public static Optional<RestServer> tryStart(
            MinecraftServer minecraft, SpiritIndex spirits, String bindAddress, int port) {
        InetAddress address;
        try {
            address = InetAddress.getByName(bindAddress);
        } catch (UnknownHostException e) {
            LOGGER.error(
                    "REST-мост не запущен: адрес привязки '{}' из {} не разобран ({}). Укажите IP-адрес "
                            + "этого компьютера, 0.0.0.0 или 127.0.0.1",
                    bindAddress,
                    RestConfig.FILE_NAME,
                    e.toString());
            return Optional.empty();
        }
        SpiritBotGateway gateway = new SpiritBotGateway(minecraft, spirits, gameBus, new TaskRegistry());
        try {
            RestServer started = RestServer.start(address, port, RestRouter.create(gateway), gateway::stop);
            LOGGER.info(
                    "REST-мост слушает {}:{} (без авторизации: доступ ограничивайте адресом привязки и файрволом)",
                    address.getHostAddress(),
                    started.port());
            return Optional.of(started);
        } catch (IOException | RuntimeException e) {
            gateway.stop();
            LOGGER.error(
                    "REST-мост не запущен: не удалось занять {}:{} ({}). Проверьте, что порт свободен "
                            + "(ss -ltn) и адрес принадлежит этому компьютеру, либо измените port и bindAddress в {}",
                    address.getHostAddress(),
                    port,
                    e.toString(),
                    RestConfig.FILE_NAME);
            return Optional.empty();
        }
    }

    private static void onAboutToStart(ServerAboutToStartEvent event) {
        index = new SpiritIndex(event.getServer());
        index.register(gameBus);
    }

    private static void onStarted(ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            LOGGER.info("REST-мост не запущен: он поднимается только на выделенном сервере");
            return;
        }
        if (!RestConfig.enabled()) {
            LOGGER.info("REST-мост выключен: enabled=false в {}", RestConfig.FILE_NAME);
            return;
        }
        if (index == null) {
            LOGGER.error("REST-мост не запущен: индекс духов не создан, ServerAboutToStartEvent не был получен");
            return;
        }
        server = tryStart(event.getServer(), index, RestConfig.bindAddress(), RestConfig.port())
                .orElse(null);
    }

    private static void onStopping(ServerStoppingEvent ignoredEvent) {
        if (server != null) {
            server.stop();
            server = null;
            LOGGER.info("REST-мост остановлен");
        }
    }

    private static void onStopped(ServerStoppedEvent ignoredEvent) {
        if (index != null) {
            index.unregister(gameBus);
            index = null;
        }
    }
}
