package com.theminespirit.neoforge.net;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ModProtocol;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class ModProtocolHandshake {
    public static final ConfigurationTask.Type TASK =
            new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "protocol"));
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "protocol_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "protocol_reply"));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    private static volatile boolean registered;

    private ModProtocolHandshake() {}

    public record Request(String build) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Request::build, Request::new);

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(String build) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Reply::build, Reply::new);

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModProtocolHandshake::registerPayloads);
        modBus.addListener(ModProtocolHandshake::registerTask);
    }

    public static boolean registered() {
        return registered;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.configurationToClient(REQUEST, Request.STREAM_CODEC, ModProtocolHandshake::handleRequest);
        registrar.configurationToServer(REPLY, Reply.STREAM_CODEC, ModProtocolHandshake::handleReply);
        registered = true;
        LOGGER.info("Сетевой протокол мода v{}: сборка {}", PROTOCOL_VERSION, BuildFingerprint.current());
    }

    private static void registerTask(RegisterConfigurationTasksEvent event) {
        event.register(new ProtocolTask(event.getListener()));
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        ModProtocol client = local();
        ModProtocol server = new ModProtocol(request.build());
        if (!server.matches(client)) {
            context.disconnect(Component.literal(ModProtocol.refusal(client.build(), server.build())));
            return;
        }
        context.reply(new Reply(client.build()));
    }

    private static void handleReply(Reply reply, IPayloadContext context) {
        ModProtocol server = local();
        ModProtocol client = new ModProtocol(reply.build());
        if (!server.matches(client)) {
            context.disconnect(Component.literal(ModProtocol.refusal(client.build(), server.build())));
            return;
        }
        context.finishCurrentTask(TASK);
    }

    private static ModProtocol local() {
        return new ModProtocol(BuildFingerprint.current());
    }

    private record ProtocolTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
        @Override
        public ConfigurationTask.Type type() {
            return TASK;
        }

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            if (!listener.hasChannel(REQUEST)) {
                listener.disconnect(Component.literal(
                        ModProtocol.refusal("старая сборка без сверки протокола", BuildFingerprint.current())));
                return;
            }
            sender.accept(new Request(BuildFingerprint.current()));
        }
    }
}
