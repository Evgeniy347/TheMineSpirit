package com.theminespirit.neoforge.net;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MineSnapshot;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.board.MineSnapshots;
import com.theminespirit.neoforge.board.TownMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MineMapChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "mine_map_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "mine_map_reply"));
    public static final CustomPacketPayload.Type<MarkersRequest> MARKERS_REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "mine_markers_request"));
    public static final CustomPacketPayload.Type<MarkersReply> MARKERS_REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "mine_markers_reply"));
    private static final int MAX_CELLS = 32_768;
    private static final int MAX_RECTS = 64;
    private static final int MAX_MARKERS = 512;
    private static final List<MineSnapshot.Kind> KINDS = List.of(MineSnapshot.Kind.values());
    private static final List<MapMarker.Kind> MARKER_KINDS = List.of(MapMarker.Kind.values());
    private static volatile Consumer<Reply> clientReceiver = reply -> {};
    private static volatile Consumer<MarkersReply> markerReceiver = reply -> {};

    private MineMapChannel() {}

    public record Request(int y) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.of((buf, request) -> buf.writeInt(request.y()), buf -> new Request(buf.readInt()));

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record MarkersRequest() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MarkersRequest> STREAM_CODEC =
                StreamCodec.unit(new MarkersRequest());

        @Override
        public CustomPacketPayload.Type<MarkersRequest> type() {
            return MARKERS_REQUEST;
        }
    }

    public record Reply(Optional<MineSnapshot> view) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(MineMapChannel::write, MineMapChannel::read);

        public Reply {
            Objects.requireNonNull(view, "Срез шахты не может быть null: пусто — Optional.empty()");
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public record MarkersReply(List<MapMarker> markers) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MarkersReply> STREAM_CODEC =
                StreamCodec.of(MineMapChannel::writeMarkersReply, MineMapChannel::readMarkersReply);

        public MarkersReply {
            markers = List.copyOf(Objects.requireNonNull(markers, "Метки шахты не могут быть null"));
        }

        @Override
        public CustomPacketPayload.Type<MarkersReply> type() {
            return MARKERS_REPLY;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, MineMapChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply));
        registrar.playToServer(MARKERS_REQUEST, MarkersRequest.STREAM_CODEC, MineMapChannel::handleMarkersRequest);
        registrar.playToClient(
                MARKERS_REPLY, MarkersReply.STREAM_CODEC, (reply, context) -> markerReceiver.accept(reply));
    }

    public static void onClientReply(Consumer<Reply> receiver) {
        clientReceiver = receiver;
    }

    public static void onClientMarkers(Consumer<MarkersReply> receiver) {
        markerReceiver = receiver;
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel().getServer().overworld();
            Optional<MineSnapshot> snapshot =
                    MineSnapshots.snapshot(level, request.y()).map(found -> withMarkers(level, found));
            context.reply(new Reply(snapshot));
        }
    }

    private static void handleMarkersRequest(MarkersRequest ignoredRequest, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel().getServer().overworld();
            List<MapMarker> markers = MineSnapshots.geometry(level)
                    .map(found -> TownMarkers.around(level, found.bounds(), found.minY(), found.maxY()))
                    .orElse(List.of());
            context.reply(new MarkersReply(markers));
        }
    }

    private static MineSnapshot withMarkers(ServerLevel level, MineSnapshot snapshot) {
        return snapshot.withMarkers(TownMarkers.around(level, snapshot.bounds(), snapshot.minY(), snapshot.maxY()));
    }

    private static void write(FriendlyByteBuf buf, Reply reply) {
        buf.writeBoolean(reply.view().isPresent());
        reply.view().ifPresent(view -> writeView(buf, view));
    }

    private static void writeView(FriendlyByteBuf buf, MineSnapshot view) {
        TownMapView.Bounds bounds = view.bounds();
        buf.writeInt(bounds.x1());
        buf.writeInt(bounds.z1());
        buf.writeInt(bounds.x2());
        buf.writeInt(bounds.z2());
        buf.writeInt(view.y());
        buf.writeInt(view.minY());
        buf.writeInt(view.maxY());
        List<MineSnapshot.Cell> cells =
                view.cells().subList(0, Math.min(view.cells().size(), MAX_CELLS));
        buf.writeVarInt(cells.size());
        for (MineSnapshot.Cell cell : cells) {
            buf.writeInt(cell.x());
            buf.writeInt(cell.z());
            buf.writeVarInt(KINDS.indexOf(cell.kind()));
        }
        List<MineSnapshot.Rect> rects =
                view.rects().subList(0, Math.min(view.rects().size(), MAX_RECTS));
        buf.writeVarInt(rects.size());
        for (MineSnapshot.Rect rect : rects) {
            buf.writeInt(rect.x1());
            buf.writeInt(rect.z1());
            buf.writeInt(rect.x2());
            buf.writeInt(rect.z2());
            buf.writeUtf(rect.label());
            buf.writeVarInt(rect.percent());
        }
        writeMarkers(buf, view.markers());
    }

    private static Reply read(FriendlyByteBuf buf) {
        return new Reply(buf.readBoolean() ? Optional.of(readView(buf)) : Optional.empty());
    }

    private static MineSnapshot readView(FriendlyByteBuf buf) {
        TownMapView.Bounds bounds = new TownMapView.Bounds(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        int y = buf.readInt();
        int minY = buf.readInt();
        int maxY = buf.readInt();
        int count = checked(buf.readVarInt(), MAX_CELLS, "клеток среза шахты");
        List<MineSnapshot.Cell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int x = buf.readInt();
            int z = buf.readInt();
            MineSnapshot.Kind kind = KINDS.get(checked(buf.readVarInt(), KINDS.size() - 1, "вид клетки шахты"));
            cells.add(new MineSnapshot.Cell(x, z, kind));
        }
        int rectCount = checked(buf.readVarInt(), MAX_RECTS, "построек шахты");
        List<MineSnapshot.Rect> rects = new ArrayList<>(rectCount);
        for (int index = 0; index < rectCount; index++) {
            rects.add(new MineSnapshot.Rect(
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readVarInt()));
        }
        return new MineSnapshot(bounds, minY, maxY, y, cells, rects, readMarkers(buf));
    }

    private static void writeMarkersReply(FriendlyByteBuf buf, MarkersReply reply) {
        writeMarkers(buf, reply.markers());
    }

    private static MarkersReply readMarkersReply(FriendlyByteBuf buf) {
        return new MarkersReply(readMarkers(buf));
    }

    private static void writeMarkers(FriendlyByteBuf buf, List<MapMarker> all) {
        List<MapMarker> markers = all.subList(0, Math.min(all.size(), MAX_MARKERS));
        buf.writeVarInt(markers.size());
        for (MapMarker marker : markers) {
            buf.writeVarInt(MARKER_KINDS.indexOf(marker.kind()));
            buf.writeUtf(marker.name());
            buf.writeUtf(marker.occupation());
            buf.writeInt(marker.x());
            buf.writeInt(marker.z());
        }
    }

    private static List<MapMarker> readMarkers(FriendlyByteBuf buf) {
        int count = checked(buf.readVarInt(), MAX_MARKERS, "меток шахты");
        List<MapMarker> markers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            MapMarker.Kind kind = MARKER_KINDS.get(checked(buf.readVarInt(), MARKER_KINDS.size() - 1, "вид метки"));
            markers.add(new MapMarker(kind, buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt()));
        }
        return markers;
    }

    private static int checked(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Срез шахты несёт " + value + " (" + what + "), предел " + max
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        return value;
    }
}
