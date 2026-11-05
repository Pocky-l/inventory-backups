package com.pockyl.inventorybackups.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jetbrains.annotations.Nullable;

import com.pockyl.inventorybackups.client.screen.AdminScreen;
import com.pockyl.inventorybackups.network.Payloads;
import com.pockyl.inventorybackups.platform.ClientPlatform;

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
public final class ClientSession {
    private static final Map<Integer, Consumer<Payloads.Payload>> PENDING = new HashMap<>();
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
        if (connection == null || !ClientPlatform.INSTANCE.hasChannel()) {
            return false;
        }
        ClientPlatform.INSTANCE.sendToServer(new Payloads.OpenRequest());
        return true;
    }

    /**
     * Sends a request built from a fresh id.
     *
     * @return the id, or -1 when the server cannot receive it
     */
    public static int send(IntFunction<Payloads.Payload> request, Consumer<Payloads.Payload> callback) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        int id = nextId++;
        Payloads.Payload payload = request.apply(id);
        if (connection == null || !ClientPlatform.INSTANCE.hasChannel()) {
            return -1;
        }
        PENDING.put(id, callback);
        ClientPlatform.INSTANCE.sendToServer(payload);
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
    public static void receive(Payloads.Payload payload) {
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
        if (payload instanceof Payloads.StateResponse state) {
            ModSlotNames.remember(state.slotKeys());
        }
        int id = -1;
        if (payload instanceof Payloads.Notice notice) {
            id = notice.id();
        } else if (payload instanceof Payloads.PlayersResponse response) {
            id = response.id();
        } else if (payload instanceof Payloads.TimelineResponse response) {
            id = response.id();
        } else if (payload instanceof Payloads.StateResponse response) {
            id = response.id();
        } else if (payload instanceof Payloads.SearchResponse response) {
            id = response.id();
        }
        Consumer<Payloads.Payload> callback = PENDING.remove(id);
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

    /** When the client leaves a server or world. */
    public static void onLoggingOut() {
        PENDING.clear();
        hello = null;
        ModSlotNames.forget();
    }
}
