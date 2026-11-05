package com.pockyl.inventorybackups.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.network.ClientBridge;
import com.pockyl.inventorybackups.network.Network;
import com.pockyl.inventorybackups.network.PayloadCodec;
import com.pockyl.inventorybackups.network.Payloads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The payloads on one Fabric channel, each written as its number (the registration order of {@link Network}) and its
 * fields. A side without the mod simply does not have the channel; both sides check before sending. Payloads are read
 * on the network thread and handled on the game thread.
 */
final class FabricNetwork {
    private static final ResourceLocation CHANNEL = InventoryBackups.id(Network.CHANNEL);
    private static final List<Entry<?>> ENTRIES = new ArrayList<>();
    private static final Map<Class<?>, Entry<?>> BY_TYPE = new HashMap<>();

    private record Entry<T extends Payloads.Payload>(int id, Class<T> type, PayloadCodec<T> codec,
                                                     BiConsumer<T, ServerPlayer> serverHandler) {
        void write(FriendlyByteBuf buf, Payloads.Payload payload) {
            buf.writeVarInt(id);
            codec.encode(buf, type.cast(payload));
        }

        void handleOnServer(Payloads.Payload payload, ServerPlayer player) {
            serverHandler.accept(type.cast(payload), player);
        }
    }

    private FabricNetwork() {
    }

    /** Both sides: the payload numbers and the server receiver. */
    static void register() {
        Network.register(new Network.Registrar() {
            @Override
            public <T extends Payloads.Payload> void toServer(Class<T> type, PayloadCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
                add(new Entry<>(ENTRIES.size(), type, codec, handler));
            }

            @Override
            public <T extends Payloads.Payload> void toClient(Class<T> type, PayloadCodec<T> codec) {
                add(new Entry<>(ENTRIES.size(), type, codec, null));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            Entry<?> entry = entry(buf.readVarInt());
            if (entry == null || entry.serverHandler() == null) {
                return;
            }
            Payloads.Payload payload = entry.codec().decode(buf);
            server.execute(() -> entry.handleOnServer(payload, player));
        });
    }

    /** Client side; only called on clients. */
    static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            Entry<?> entry = entry(buf.readVarInt());
            if (entry == null || entry.serverHandler() != null) {
                return;
            }
            Payloads.Payload payload = entry.codec().decode(buf);
            client.execute(() -> ClientBridge.receive(payload));
        });
    }

    static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, CHANNEL);
    }

    static void send(ServerPlayer player, Payloads.Payload payload) {
        ServerPlayNetworking.send(player, CHANNEL, write(payload));
    }

    /** Client side; only called on clients. */
    static boolean canSendToServer() {
        return ClientPlayNetworking.canSend(CHANNEL);
    }

    /** Client side; only called on clients. */
    static void sendToServer(Payloads.Payload payload) {
        ClientPlayNetworking.send(CHANNEL, write(payload));
    }

    private static void add(Entry<?> entry) {
        ENTRIES.add(entry);
        BY_TYPE.put(entry.type(), entry);
    }

    private static Entry<?> entry(int id) {
        return id >= 0 && id < ENTRIES.size() ? ENTRIES.get(id) : null;
    }

    private static FriendlyByteBuf write(Payloads.Payload payload) {
        Entry<?> entry = BY_TYPE.get(payload.getClass());
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered payload " + payload.getClass().getName());
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        entry.write(buf, payload);
        return buf;
    }
}
