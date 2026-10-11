package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.rest.RestConfig;
import com.theminespirit.neoforge.rest.RestLifecycle;
import com.theminespirit.neoforge.rest.RestServer;
import com.theminespirit.neoforge.rest.SpiritIndex;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RestLifecycleGameTests {
    private static final String LOOPBACK = "127.0.0.1";

    private RestLifecycleGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void config_Defaults_MatchDocumentedValues(GameTestHelper helper) {
        helper.assertTrue(Boolean.TRUE.equals(defaultOf("enabled")),
                "REST-мост по умолчанию включён (enabled=true): иначе внешний агент не достучится до свежего сервера, "
                        + "получено " + defaultOf("enabled"));
        helper.assertTrue(Integer.valueOf(8765).equals(defaultOf("port")),
                "Порт REST-моста по умолчанию 8765 (8099 занят раздачей клиента, 25565 — игра), получен "
                        + defaultOf("port"));
        helper.assertTrue("0.0.0.0".equals(defaultOf("bindAddress")),
                "Адрес привязки по умолчанию 0.0.0.0 (вся локальная сеть), получен " + defaultOf("bindAddress"));
        helper.succeed();
    }

    private static Object defaultOf(String key) {
        ModConfigSpec.ValueSpec value = RestConfig.spec().getSpec().get(List.of("rest", key));
        return value.getDefault();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tryStart_PortAlreadyTaken_ReturnsEmptyAndServerKeepsRunning(GameTestHelper helper)
            throws IOException {
        MinecraftServer minecraft = helper.getLevel().getServer();
        try (ServerSocket occupied = new ServerSocket(0, 1, RestFixture.loopback())) {
            Optional<RestServer> started = RestLifecycle.tryStart(minecraft, new SpiritIndex(minecraft), LOOPBACK,
                    occupied.getLocalPort());

            helper.assertTrue(started.isEmpty(),
                    "Занятый порт " + occupied.getLocalPort() + " не должен ронять сервер Minecraft: запуск моста "
                            + "возвращает пустой результат и пишет ERROR в лог");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tryStart_UnresolvableAddress_ReturnsEmptyAndServerKeepsRunning(GameTestHelper helper) {
        MinecraftServer minecraft = helper.getLevel().getServer();

        Optional<RestServer> started = RestLifecycle.tryStart(minecraft, new SpiritIndex(minecraft),
                "no-such-host.invalid", 0);

        helper.assertTrue(started.isEmpty(),
                "Неразбираемый bindAddress не должен ронять сервер Minecraft: запуск моста возвращает пустой "
                        + "результат и пишет ERROR в лог");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tryStart_FreePort_ReturnsRunningServerThatStops(GameTestHelper helper) {
        MinecraftServer minecraft = helper.getLevel().getServer();

        Optional<RestServer> started = RestLifecycle.tryStart(minecraft, new SpiritIndex(minecraft), LOOPBACK, 0);

        helper.assertTrue(started.isPresent() && started.get().port() > 0,
                "Свободный порт обязан давать запущенный мост с известным портом");
        started.ifPresent(RestServer::stop);
        helper.succeed();
    }
}
