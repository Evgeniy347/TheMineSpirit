package com.theminespirit.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

final class GameTestPlayers {
    private GameTestPlayers() {}

    static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos relative, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player =
                new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        removeAtEnd(helper, server, player);
        return player;
    }

    static ServerPlayer mockPlayer(
            GameTestHelper helper, BlockPos relative, String name, List<Component> capturedMessages) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player =
                new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
                    @Override
                    public void sendSystemMessage(Component message) {
                        capturedMessages.add(message);
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        removeAtEnd(helper, server, player);
        return player;
    }

    static void removeAtEnd(GameTestHelper helper, MinecraftServer server, ServerPlayer player) {
        if (helper instanceof StepHelper step) {
            step.atStepEnd(() -> server.getPlayerList().remove(player));
            return;
        }
        helper.testInfo.addListener(new PlayerCleanup(server, player));
    }

    private record PlayerCleanup(MinecraftServer server, ServerPlayer player) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            server.getPlayerList().remove(player);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            server.getPlayerList().remove(player);
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
    }
}
