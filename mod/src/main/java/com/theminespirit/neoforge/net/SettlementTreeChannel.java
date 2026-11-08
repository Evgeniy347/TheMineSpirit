package com.theminespirit.neoforge.net;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.SettlementTree;
import com.theminespirit.core.ui.SettlementTreeCodec;
import com.theminespirit.neoforge.board.SettlementTreeService;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SettlementTreeChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "settlement_tree_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "settlement_tree_reply"));

    private static final String PROTOCOL_VERSION = "1";
    private static volatile Consumer<SettlementTree> clientReceiver = tree -> {};

    private SettlementTreeChannel() {}

    public record Request(int stage) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, request) -> buf.writeVarInt(request.stage()), buf -> new Request(buf.readVarInt()));

        public Request {
            if (stage < 1) {
                throw new IllegalArgumentException(
                        "Запрос древа этапа " + stage + ": вкладки этапов начинаются с первого");
            }
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(SettlementTree tree) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(SettlementTreeChannel::write, SettlementTreeChannel::read);

        public Reply {
            Objects.requireNonNull(tree, "Древо ответа не может быть null: клиенту нечего рисовать");
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SettlementTreeChannel::registerPayloads);
    }

    public static void onClientReply(Consumer<SettlementTree> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "Получатель древа не может быть null");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, SettlementTreeChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply.tree()));
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.reply(new Reply(SettlementTreeService.tree(player.serverLevel(), request.stage())));
        }
    }

    private static void write(FriendlyByteBuf buf, Reply reply) {
        buf.writeByteArray(SettlementTreeCodec.encode(reply.tree()));
    }

    private static Reply read(FriendlyByteBuf buf) {
        return new Reply(SettlementTreeCodec.decode(buf.readByteArray()));
    }
}
