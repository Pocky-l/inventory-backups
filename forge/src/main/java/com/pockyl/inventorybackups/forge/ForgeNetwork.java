package com.pockyl.inventorybackups.forge;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.network.ClientBridge;
import com.pockyl.inventorybackups.network.Network;
import com.pockyl.inventorybackups.network.PayloadCodec;
import com.pockyl.inventorybackups.network.Payloads;

import java.util.function.BiConsumer;

/**
 * The payloads on one Forge channel, optional both ways, so clients without the mod can join a server with it and the
 * other way round.
 */
final class ForgeNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(InventoryBackups.id(Network.CHANNEL),
            () -> Network.VERSION, NetworkRegistry.acceptMissingOr(Network.VERSION), NetworkRegistry.acceptMissingOr(Network.VERSION));
    private static int nextId;

    private ForgeNetwork() {
    }

    static void register() {
        Network.register(new Network.Registrar() {
            // Handled on the server thread, only for messages from a player.
            @Override
            public <T extends Payloads.Payload> void toServer(Class<T> type, PayloadCodec<T> codec, BiConsumer<T, ServerPlayer> handler) {
                CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                        .encoder((payload, buf) -> codec.encode(buf, payload))
                        .decoder(codec::decode)
                        .consumerMainThread((payload, context) -> {
                            ServerPlayer sender = context.get().getSender();
                            if (sender != null) {
                                handler.accept(payload, sender);
                            }
                        })
                        .add();
            }

            // Handled on the client thread by whatever the client part installed in ClientBridge.
            @Override
            public <T extends Payloads.Payload> void toClient(Class<T> type, PayloadCodec<T> codec) {
                CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                        .encoder((payload, buf) -> codec.encode(buf, payload))
                        .decoder(codec::decode)
                        .consumerMainThread((payload, context) -> ClientBridge.receive(payload))
                        .add();
            }
        });
    }

    /** Whether the other end of a connection has the channel: it runs the mod. */
    static boolean present(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    static void sendToPlayer(ServerPlayer player, Payloads.Payload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    static void sendToServer(Payloads.Payload payload) {
        CHANNEL.sendToServer(payload);
    }
}
