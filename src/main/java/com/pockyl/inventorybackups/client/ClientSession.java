package com.pockyl.inventorybackups.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.InventoryBackups;
import com.pockyl.inventorybackups.client.screen.AdminScreen;
import com.pockyl.inventorybackups.network.Payloads;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The client side of the conversation with the server: sends requests with fresh ids and routes each answer to the
 * callback of its request. A notice for a request goes to that request's callback too, so a screen can show errors
 * where the admin is looking.
 */
@EventBusSubscriber(modid = InventoryBackups.MOD_ID, value = Dist.CLIENT)
public final class ClientSession {
    private static final Map<Integer, Consumer<CustomPacketPayload>> PENDING = new HashMap<>();
    private static int nextId = 1;
    @Nullable
    private static Payloads.Hello hello;
    @Nullable
    private static UUID lastPlayer;

    private ClientSession() {
    }

    /** Asks the server to open the screen; false when the server does not have the mod. */
    public static boolean requestOpen() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(Payloads.OpenRequest.TYPE)) {
            return false;
        }
        PacketDistributor.sendToServer(new Payloads.OpenRequest());
        return true;
    }

    /**
     * Sends a request built from a fresh id.
     *
     * @return the id, or -1 when the server cannot receive it
     */
    public static int send(IntFunction<CustomPacketPayload> request, Consumer<CustomPacketPayload> callback) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        int id = nextId++;
        CustomPacketPayload payload = request.apply(id);
        if (connection == null || !connection.hasChannel(payload)) {
            return -1;
        }
        PENDING.put(id, callback);
        PacketDistributor.sendToServer(payload);
        return id;
    }

    @Nullable
    public static Payloads.Hello hello() {
        return hello;
    }

    @Nullable
    public static UUID lastPlayer() {
        return lastPlayer;
    }

    public static void rememberPlayer(UUID player) {
        lastPlayer = player;
    }

    /** Called on the client thread for every payload from the server. */
    static void receive(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload instanceof Payloads.Hello newHello) {
            hello = newHello;
            if (minecraft.screen instanceof AdminScreen screen) {
                screen.updatePermissions(newHello);
            } else {
                minecraft.setScreen(new AdminScreen(newHello));
            }
            return;
        }
        int id = switch (payload) {
            case Payloads.Notice notice -> notice.id();
            case Payloads.PlayersResponse response -> response.id();
            case Payloads.TimelineResponse response -> response.id();
            case Payloads.StateResponse response -> response.id();
            case Payloads.SearchResponse response -> response.id();
            default -> -1;
        };
        Consumer<CustomPacketPayload> callback = PENDING.remove(id);
        if (callback != null) {
            callback.accept(payload);
        } else if (payload instanceof Payloads.Notice notice) {
            if (minecraft.screen instanceof AdminScreen screen) {
                screen.toast(notice.message(), notice.ok());
            } else if (minecraft.player != null) {
                minecraft.player.displayClientMessage(notice.message(), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PENDING.clear();
        hello = null;
    }
}
