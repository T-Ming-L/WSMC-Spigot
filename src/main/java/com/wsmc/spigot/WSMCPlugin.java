package com.wsmc.spigot;

import com.wsmc.spigot.websocket.WebSocketServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WSMC Spigot/Paper plugin — enables WebSocket access to a Minecraft server.
 *
 * <p>
 * <b>Supported versions:</b> Paper/Spigot 1.20.4 and above.
 *
 * <p>
 * Architecture:
 * <ol>
 * <li>Start a Netty WebSocket server on {@code wsmc.wsPort} (default
 * 25566).</li>
 * <li>When a WebSocket client connects, bridge binary frames to
 * {@code localhost:spigotPort} — the server's own Minecraft port.</li>
 * <li>Spigot sees the connection as coming from localhost and processes it
 * normally (login, play, etc.).</li>
 * </ol>
 *
 * <p>
 * When {@code wsmc.disableVanillaTCP} is enabled, non-localhost connections
 * are denied at the login event level so that only WebSocket clients can join.
 */
public class WSMCPlugin extends JavaPlugin implements Listener {

    /** Minimum supported Minecraft version (minor version, e.g. 20 for 1.20.x). */
    private static final int MIN_MINOR_VERSION = 20;
    /** Minimum supported patch version within the minor series. */
    private static final int MIN_PATCH_VERSION = 4;

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "1\\.(\\d+)(?:\\.(\\d+))?");

    private WSMCConfig config;
    private WebSocketServer wsServer;

    @Override
    public void onEnable() {
        // ── Version compatibility check ─────────────────────────────
        checkServerVersion();

        // Load configuration
        this.config = WSMCConfig.load(getDataFolder().toPath(), getLogger());

        if (config.isDebug()) {
            getLogger().info("WSMC debug logging enabled.");
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Start WebSocket server
        this.wsServer = new WebSocketServer(config, getLogger());
        try {
            wsServer.start();
            getLogger().info("WSMC WebSocket server started on port " + config.getWsPort());
            if (config.getEndpoint() != null) {
                getLogger().info("WSMC WebSocket endpoint: " + config.getEndpoint());
            }
            getLogger().info("WSMC max frame payload length: " + config.getMaxFramePayloadLength());
        } catch (Exception e) {
            getLogger().severe("Failed to start WSMC WebSocket server: " + e.getMessage());
            if (config.isDebug()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check that the server version meets the minimum requirement (1.20.4).
     * Logs a warning but does not disable the plugin if the version is too old.
     */
    private void checkServerVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion);

        if (matcher.find()) {
            int minor = Integer.parseInt(matcher.group(1));
            int patch = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;

            getLogger().info("Detected server version: 1." + minor + "." + patch
                    + " (raw: " + bukkitVersion + ")");
            getLogger().info("WSMC requires Paper/Spigot 1." + MIN_MINOR_VERSION
                    + "." + MIN_PATCH_VERSION + "+");

            if (minor < MIN_MINOR_VERSION || (minor == MIN_MINOR_VERSION && patch < MIN_PATCH_VERSION)) {
                getLogger().warning("==============================================");
                getLogger().warning("WSMC may not work correctly on this version!");
                getLogger().warning("Minimum supported: 1." + MIN_MINOR_VERSION + "." + MIN_PATCH_VERSION);
                getLogger().warning("Current version:   1." + minor + "." + patch);
                getLogger().warning("==============================================");
            }
        } else {
            getLogger().warning("Could not parse server version from: " + bukkitVersion);
        }
    }

    @Override
    public void onDisable() {
        if (wsServer != null) {
            wsServer.shutdown();
            getLogger().info("WSMC WebSocket server stopped.");
        }
    }

    /**
     * Block vanilla TCP logins when {@code wsmc.disableVanillaTCP} is enabled.
     * WebSocket connections arrive from localhost, so only those are permitted.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config.isDisableVanillaTCP()) {
            return;
        }
        InetAddress addr = event.getAddress();
        if (addr == null || !"127.0.0.1".equals(addr.getHostAddress())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text(
                            "Vanilla TCP is disabled on this server. Please connect via WebSocket (ws:// or wss://)."));
            getLogger().info("[WSMC] Blocked vanilla TCP login from " + addr);
        }
    }

    /**
     * Optionally hide the server from the multiplayer server list when
     * vanilla TCP is disabled (external clients cannot connect anyway).
     */
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (config.isDisableVanillaTCP()) {
            // Still allow ping but the address shown will be unreachable via TCP.
            // Server ops may want to completely hide the server.
        }
    }
}
