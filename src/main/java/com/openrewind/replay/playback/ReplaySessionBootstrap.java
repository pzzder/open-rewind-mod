package com.openrewind.replay.playback;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;

import java.util.UUID;

/**
 * <b>[LIVE-INTEGRATION SEAM]</b> — the single place that constructs the
 * client-only network objects a replay is fed through. Everything else in the
 * replay engine (packet capture, container format, decode/dispatch mechanism,
 * camera, export loop) is deterministic and self-contained; this factory is the
 * one piece whose exact wiring must be validated inside a running 1.8.9 client,
 * because it depends on how Minecraft bootstraps a world from a connection.
 *
 * <h3>Intended construction (matches vanilla + ReplayMod)</h3>
 * <ol>
 *   <li>Create a client-direction {@link NetworkManager}
 *       ({@code new NetworkManager(EnumPacketDirection.CLIENTBOUND)}).</li>
 *   <li>Create a {@link NetHandlerPlayClient} bound to it with a dummy
 *       {@link GameProfile} and a placeholder {@link GuiScreen}. The vanilla
 *       constructor is
 *       {@code NetHandlerPlayClient(Minecraft, GuiScreen, NetworkManager, GameProfile)}.</li>
 *   <li>Return the handler. When the recorded {@code S01PacketJoinGame} is later
 *       dispatched through it, vanilla itself creates the {@code WorldClient} and
 *       calls {@code Minecraft.loadWorld(...)} — i.e. the world rebuilds exactly
 *       as during a normal join, with no real server.</li>
 * </ol>
 *
 * <p>The subtle, must-test-live details are: making sure Minecraft is on a screen
 * that allows a world to load, suppressing the outbound login/keepalive traffic
 * the handler would normally send (the fake manager's channel simply discards
 * it), and cleaning the world up on stop. These are exactly the areas ReplayMod
 * spent years hardening across dimensions / respawns / disconnects.</p>
 */
public final class ReplaySessionBootstrap {

    private ReplaySessionBootstrap() { }

    private static final GameProfile REPLAY_PROFILE =
            new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), "Replay");

    public static NetHandlerPlayClient createPlayHandler(Minecraft mc) {
        NetworkManager manager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        GuiScreen placeholder = mc.currentScreen; // any non-null screen is fine as a placeholder
        return new NetHandlerPlayClient(mc, placeholder, manager, REPLAY_PROFILE);
    }
}
