package com.wsmc.spigot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration for WSMC Spigot plugin.
 *
 * Properties are read from wsmc.properties in the plugin data directory.
 */
public class WSMCConfig {

    // ── Property keys ──────────────────────────────────────────────

    private static final String KEY_DISABLE_VANILLA_TCP = "wsmc.disableVanillaTCP";
    private static final String KEY_WSMC_ENDPOINT = "wsmc.wsmcEndpoint";
    private static final String KEY_DEBUG = "wsmc.debug";
    private static final String KEY_DUMP_BYTES = "wsmc.dumpBytes";
    private static final String KEY_MAX_FRAME_PAYLOAD_LENGTH = "wsmc.maxFramePayloadLength";

    // Spigot-specific keys
    private static final String KEY_WS_PORT = "wsmc.wsPort";
    /** Spigot's own Minecraft listen port (from server.properties). */
    private static final String KEY_SPIGOT_PORT = "wsmc.spigotPort";

    // Proxy Protocol V2 keys
    private static final String KEY_PROXY_PROTOCOL_ENABLED = "wsmc.proxyProtocol.enabled";
    private static final String KEY_PROXY_PROTOCOL_REAL_IP_HDR = "wsmc.proxyProtocol.realIpHeader";
    private static final String DEFAULT_REAL_IP_HEADER = "X-Forwarded-For";

    // ── Fields ─────────────────────────────────────────────────────

    private final boolean disableVanillaTCP;
    private final String endpoint;
    private final boolean debug;
    private final boolean dumpBytes;
    private final int maxFramePayloadLength;
    private final int wsPort;
    private final int spigotPort;
    private final boolean proxyProtocolEnabled;
    private final String proxyProtocolRealIpHeader;

    public WSMCConfig(boolean disableVanillaTCP, String endpoint, boolean debug,
            boolean dumpBytes, int maxFramePayloadLength,
            int wsPort, int spigotPort,
            boolean proxyProtocolEnabled, String proxyProtocolRealIpHeader) {
        this.disableVanillaTCP = disableVanillaTCP;
        this.endpoint = endpoint;
        this.debug = debug;
        this.dumpBytes = dumpBytes;
        this.maxFramePayloadLength = maxFramePayloadLength;
        this.wsPort = wsPort;
        this.spigotPort = spigotPort;
        this.proxyProtocolEnabled = proxyProtocolEnabled;
        this.proxyProtocolRealIpHeader = proxyProtocolRealIpHeader;
    }

    // ── Getters ────────────────────────────────────────────────────

    public boolean isDisableVanillaTCP() {
        return disableVanillaTCP;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDumpBytes() {
        return dumpBytes;
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public int getWsPort() {
        return wsPort;
    }

    public int getSpigotPort() {
        return spigotPort;
    }

    public boolean isProxyProtocolEnabled() {
        return proxyProtocolEnabled;
    }

    public String getProxyProtocolRealIpHeader() {
        return proxyProtocolRealIpHeader;
    }

    // ── Load ───────────────────────────────────────────────────────

    /**
     * Loads configuration from wsmc.properties in the plugin data directory.
     * Creates the file with defaults if it does not exist.
     */
    public static WSMCConfig load(Path dataDirectory, Logger logger) {
        Path configFile = dataDirectory.resolve("wsmc.properties");
        Properties props = new Properties();

        // Defaults
        boolean disableVanillaTCP = false;
        String endpoint = null;
        boolean debug = false;
        boolean dumpBytes = false;
        int maxFramePayloadLength = 65536;
        int wsPort = 25566;
        int spigotPort = 25565;
        boolean proxyProtocolEnabled = false;
        String proxyProtocolRealIpHeader = DEFAULT_REAL_IP_HEADER;

        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);

                disableVanillaTCP = parseBoolean(props, KEY_DISABLE_VANILLA_TCP, false);
                endpoint = parseString(props, KEY_WSMC_ENDPOINT, null);
                debug = parseBoolean(props, KEY_DEBUG, false);
                dumpBytes = parseBoolean(props, KEY_DUMP_BYTES, false);
                maxFramePayloadLength = parseInt(props, KEY_MAX_FRAME_PAYLOAD_LENGTH, 65536);
                wsPort = parseInt(props, KEY_WS_PORT, 25566);
                spigotPort = parseInt(props, KEY_SPIGOT_PORT, 25565);
                proxyProtocolEnabled = parseBoolean(props, KEY_PROXY_PROTOCOL_ENABLED, false);
                proxyProtocolRealIpHeader = parseString(props, KEY_PROXY_PROTOCOL_REAL_IP_HDR, DEFAULT_REAL_IP_HEADER);

            } catch (IOException e) {
                logger.severe("Failed to load wsmc.properties, using defaults: " + e.getMessage());
            }
        } else {
            // Create default config file
            try {
                Files.createDirectories(dataDirectory);
                StringBuilder sb = new StringBuilder();
                sb.append("# WSMC Spigot Plugin Configuration\n");
                sb.append("# See https://github.com/rikka0w0/wsmc for original mod documentation\n\n");
                sb.append("# Disable vanilla TCP login.\n");
                sb.append(KEY_DISABLE_VANILLA_TCP).append("=false\n\n");
                sb.append("# WebSocket endpoint path. Must start with /. Case-sensitive.\n");
                sb.append("# ").append(KEY_WSMC_ENDPOINT).append("=/mc\n\n");
                sb.append("# Show debug logs.\n");
                sb.append(KEY_DEBUG).append("=false\n\n");
                sb.append("# Dump raw WebSocket binary frames. Works only if wsmc.debug=true.\n");
                sb.append(KEY_DUMP_BYTES).append("=false\n\n");
                sb.append("# Maximum allowable frame payload length.\n");
                sb.append(KEY_MAX_FRAME_PAYLOAD_LENGTH).append("=65536\n\n");
                sb.append("# [Spigot-specific] WebSocket listen port.\n");
                sb.append(KEY_WS_PORT).append("=25566\n\n");
                sb.append("# [Spigot-specific] Spigot's own Minecraft listen port (from server.properties).\n");
                sb.append("# The plugin connects to localhost on this port to bridge WebSocket data.\n");
                sb.append(KEY_SPIGOT_PORT).append("=25565\n\n");
                sb.append("# ── Proxy Protocol V2 ──\n");
                sb.append("# Enable Proxy Protocol V2 to pass real client IP to Spigot.\n");
                sb.append(KEY_PROXY_PROTOCOL_ENABLED).append("=false\n");
                sb.append("# HTTP header containing the real client IP (e.g., X-Forwarded-For, CF-Connecting-IP).\n");
                sb.append(KEY_PROXY_PROTOCOL_REAL_IP_HDR).append("=").append(DEFAULT_REAL_IP_HEADER).append("\n");

                Files.writeString(configFile, sb.toString());
                logger.info("Created default configuration at " + configFile);
            } catch (IOException e) {
                logger.severe("Failed to create default config: " + e.getMessage());
            }
        }

        return new WSMCConfig(disableVanillaTCP, endpoint, debug, dumpBytes,
                maxFramePayloadLength, wsPort, spigotPort,
                proxyProtocolEnabled, proxyProtocolRealIpHeader);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static String parseString(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty())
            return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty())
            return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
