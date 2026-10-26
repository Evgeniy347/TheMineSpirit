package com.theminespirit.neoforge.net;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.TerrainTile;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.board.TerrainTiles;
import com.theminespirit.neoforge.board.TownMarkers;
import com.theminespirit.neoforge.board.TownSnapshots;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TownMapChannel {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "town_map_request"));
    public static final CustomPacketPayload.Type<Reply> REPLY =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "town_map_reply"));
    public static final CustomPacketPayload.Type<MarkersRequest> MARKERS_REQUEST = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "town_markers_request"));
    public static final CustomPacketPayload.Type<MarkersReply> MARKERS_REPLY = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "town_markers_reply"));

    private static final String PROTOCOL_VERSION = "3";
    private static final int MAX_RECTS = 32_768;
    private static final int MAX_POINTS = 16_384;
    private static final int MAX_TILES = 4096;
    private static final int MAX_MARKERS = 512;
    private static final List<TownMapView.Kind> KINDS = List.of(TownMapView.Kind.values());
    private static final List<TownMapView.LineKind> LINE_KINDS = List.of(TownMapView.LineKind.values());
    private static final List<MapMarker.Kind> MARKER_KINDS = List.of(MapMarker.Kind.values());
    private static volatile Consumer<Reply> clientReceiver = reply -> {};
    private static volatile Consumer<MarkersReply> markerReceiver = reply -> {};

    private TownMapChannel() {}

    public record Request() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

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

    public record MarkersReply(List<MapMarker> markers) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MarkersReply> STREAM_CODEC =
                StreamCodec.of(TownMapChannel::writeMarkersReply, TownMapChannel::readMarkersReply);

        public MarkersReply {
            markers = List.copyOf(Objects.requireNonNull(markers, "Метки карты не могут быть null"));
        }

        @Override
        public CustomPacketPayload.Type<MarkersReply> type() {
            return MARKERS_REPLY;
        }
    }

    public record Reply(Optional<TownMapView> view, List<TerrainTile> tiles) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.of(TownMapChannel::write, TownMapChannel::read);

        public Reply {
            Objects.requireNonNull(view, "Снимок карты не может быть null: пусто — Optional.empty()");
            tiles = List.copyOf(Objects.requireNonNull(tiles, "Тайлы местности не могут быть null"));
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TownMapChannel::registerPayloads);
    }

    public static void onClientReply(Consumer<Reply> receiver) {
        clientReceiver = receiver;
    }

    public static void onClientMarkers(Consumer<MarkersReply> receiver) {
        markerReceiver = receiver;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(REQUEST, Request.STREAM_CODEC, TownMapChannel::handleRequest);
        registrar.playToClient(REPLY, Reply.STREAM_CODEC, (reply, context) -> clientReceiver.accept(reply));
        registrar.playToServer(MARKERS_REQUEST, MarkersRequest.STREAM_CODEC, TownMapChannel::handleMarkersRequest);
        registrar.playToClient(
                MARKERS_REPLY, MarkersReply.STREAM_CODEC, (reply, context) -> markerReceiver.accept(reply));
    }

    private static void handleRequest(Request ignoredRequest, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel().getServer().overworld();
            Optional<TownMapView> view = TownSnapshots.current(level)
                    .map(TownMapView::of)
                    .map(map -> map.withMarkers(TownMarkers.around(level, map.bounds())));
            List<TerrainTile> tiles =
                    view.map(map -> TerrainTiles.around(level, map.bounds())).orElse(List.of());
            context.reply(new Reply(view, tiles));
        }
    }

    private static void handleMarkersRequest(MarkersRequest ignoredRequest, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel().getServer().overworld();
            List<MapMarker> markers = TownSnapshots.markerBounds(level)
                    .map(bounds -> TownMarkers.around(level, bounds))
                    .orElse(List.of());
            context.reply(new MarkersReply(markers));
        }
    }

    private static void write(FriendlyByteBuf buf, Reply reply) {
        buf.writeBoolean(reply.view().isPresent());
        reply.view().ifPresent(view -> writeView(buf, view));
        writeTiles(buf, reply.tiles());
    }

    private static void writeView(FriendlyByteBuf buf, TownMapView view) {
        List<TownMapView.MapRect> rects =
                view.rects().subList(0, Math.min(view.rects().size(), MAX_RECTS));
        buf.writeVarInt(rects.size());
        for (TownMapView.MapRect rect : rects) {
            buf.writeVarInt(KINDS.indexOf(rect.kind()));
            buf.writeInt(rect.x1());
            buf.writeInt(rect.z1());
            buf.writeInt(rect.x2());
            buf.writeInt(rect.z2());
            buf.writeUtf(rect.label());
            buf.writeVarInt(rect.percent());
            buf.writeBoolean(rect.damaged());
        }
        buf.writeVarInt(view.lines().size());
        for (TownMapView.MapLine line : view.lines()) {
            buf.writeVarInt(LINE_KINDS.indexOf(line.kind()));
            writePoints(buf, line.points());
            writePoints(buf, line.gates());
        }
        writeMarkers(buf, view.markers());
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

    private static void writePoints(FriendlyByteBuf buf, List<TownMapView.MapPoint> all) {
        List<TownMapView.MapPoint> points = all.subList(0, Math.min(all.size(), MAX_POINTS));
        buf.writeVarInt(points.size());
        for (TownMapView.MapPoint point : points) {
            buf.writeInt(point.x());
            buf.writeInt(point.z());
        }
    }

    private static void writeTiles(FriendlyByteBuf buf, List<TerrainTile> all) {
        List<TerrainTile> tiles = all.subList(0, Math.min(all.size(), MAX_TILES));
        buf.writeVarInt(tiles.size());
        for (TerrainTile tile : tiles) {
            buf.writeInt(tile.chunkX());
            buf.writeInt(tile.chunkZ());
            buf.writeBytes(tile.ids());
        }
    }

    private static Reply read(FriendlyByteBuf buf) {
        Optional<TownMapView> view = buf.readBoolean() ? Optional.of(readView(buf)) : Optional.empty();
        List<TerrainTile> tiles = readTiles(buf);
        return new Reply(view, tiles);
    }

    private static TownMapView readView(FriendlyByteBuf buf) {
        int count = checked(buf.readVarInt(), MAX_RECTS, "прямоугольников");
        List<TownMapView.MapRect> rects = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            TownMapView.Kind kind = KINDS.get(checked(buf.readVarInt(), KINDS.size() - 1, "вид прямоугольника"));
            rects.add(new TownMapView.MapRect(
                    kind,
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean()));
        }
        int lineCount = checked(buf.readVarInt(), LINE_KINDS.size(), "линий");
        List<TownMapView.MapLine> lines = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            TownMapView.LineKind kind = LINE_KINDS.get(checked(buf.readVarInt(), LINE_KINDS.size() - 1, "вид линии"));
            List<TownMapView.MapPoint> points = readPoints(buf, "точек линии");
            lines.add(new TownMapView.MapLine(kind, points, readPoints(buf, "калиток линии")));
        }
        return new TownMapView(rects, lines, readMarkers(buf));
    }

    private static List<MapMarker> readMarkers(FriendlyByteBuf buf) {
        int count = checked(buf.readVarInt(), MAX_MARKERS, "меток карты");
        List<MapMarker> markers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            MapMarker.Kind kind = MARKER_KINDS.get(checked(buf.readVarInt(), MARKER_KINDS.size() - 1, "вид метки"));
            markers.add(new MapMarker(kind, buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt()));
        }
        return markers;
    }

    private static List<TerrainTile> readTiles(FriendlyByteBuf buf) {
        int count = checked(buf.readVarInt(), MAX_TILES, "тайлов местности");
        List<TerrainTile> tiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            byte[] ids = new byte[TerrainTile.COLUMNS];
            buf.readBytes(ids);
            tiles.add(new TerrainTile(chunkX, chunkZ, ids));
        }
        return tiles;
    }

    private static List<TownMapView.MapPoint> readPoints(FriendlyByteBuf buf, String what) {
        int count = checked(buf.readVarInt(), MAX_POINTS, what);
        List<TownMapView.MapPoint> points = new ArrayList<>(count);
        for (int point = 0; point < count; point++) {
            points.add(new TownMapView.MapPoint(buf.readInt(), buf.readInt()));
        }
        return points;
    }

    private static int checked(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Снимок карты несёт " + value + " (" + what + "), предел " + max
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        return value;
    }
}
