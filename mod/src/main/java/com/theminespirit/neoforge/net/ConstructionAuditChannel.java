package com.theminespirit.neoforge.net;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.board.ConstructionAuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class ConstructionAuditChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "construction_audit_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "construction_audit_reply"));

    private static final String PROTOCOL_VERSION = "1";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Consumer<Reply> clientReceiver = reply -> {};

    private ConstructionAuditChannel() {}

    public record Request(BlockPos center) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.of(ConstructionAuditChannel::writeRequest, ConstructionAuditChannel::readRequest);

        public Request {
            Objects.requireNonNull(center, "Центр запроса аудита не может быть null: от него считается радиус");
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(List<BlockPos> demolish, List<BlockPos> place) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(ConstructionAuditChannel::writeReply, ConstructionAuditChannel::readReply);

        public Reply {
            demolish = List.copyOf(
                    Objects.requireNonNull(demolish, "Набор на снос не может быть null: пустой — List.of()"));
            place = List.copyOf(
                    Objects.requireNonNull(place, "Набор к постановке не может быть null: пустой — List.of()"));
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ConstructionAuditChannel::registerPayloads);
    }

    public static void onClientReply(Consumer<Reply> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "Получатель аудита подсветки не может быть null");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, ConstructionAuditChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply));
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            long started = System.nanoTime();
            ConstructionAuditService.Marks marks =
                    ConstructionAuditService.around(player.serverLevel(), request.center());
            LOGGER.debug(
                    "Аудит подсветки у {}: снести {}, поставить {}, {} мс",
                    player.getName().getString(),
                    marks.demolish().size(),
                    marks.place().size(),
                    (System.nanoTime() - started) / 1_000_000L);
            context.reply(new Reply(marks.demolish(), marks.place()));
        }
    }

    private static void writeRequest(FriendlyByteBuf buf, Request request) {
        buf.writeBlockPos(request.center());
    }

    private static Request readRequest(FriendlyByteBuf buf) {
        return new Request(buf.readBlockPos());
    }

    private static void writeReply(FriendlyByteBuf buf, Reply reply) {
        writePositions(buf, reply.demolish());
        writePositions(buf, reply.place());
    }

    private static Reply readReply(FriendlyByteBuf buf) {
        return new Reply(readPositions(buf), readPositions(buf));
    }

    private static void writePositions(FriendlyByteBuf buf, List<BlockPos> positions) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
    }

    private static List<BlockPos> readPositions(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(buf.readBlockPos());
        }
        return positions;
    }
}
