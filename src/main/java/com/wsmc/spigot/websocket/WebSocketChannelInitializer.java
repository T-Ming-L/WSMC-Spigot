package com.wsmc.spigot.websocket;

import com.wsmc.spigot.WSMCConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.logging.Logger;

/**
 * Initializes Netty channels for WebSocket connections.
 * Pipeline: HTTP codec → Aggregator → ChunkedWrite → WS Protocol → Proxy
 * Handler
 */
public class WebSocketChannelInitializer extends ChannelInitializer<Channel> {

    private final WSMCConfig config;
    private final Logger logger;

    public WebSocketChannelInitializer(WSMCConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Override
    protected void initChannel(Channel ch) {
        String endpoint = config.getEndpoint();
        int maxFramePayloadLength = config.getMaxFramePayloadLength();

        if (config.isDebug()) {
            logger.info("[WSMC] New connection from " + ch.remoteAddress());
        }

        ch.pipeline().addLast(
                // HTTP codec for WebSocket handshake
                new HttpServerCodec(),
                // Aggregate HTTP chunks
                new HttpObjectAggregator(65536),
                // Write chunked data
                new ChunkedWriteHandler(),
                // WebSocket protocol handler (handles handshake & frame aggregation)
                new WebSocketServerProtocolHandler(
                        endpoint != null ? endpoint : "/",
                        null,
                        true,
                        maxFramePayloadLength),
                // Our proxy handler – bridges WebSocket frames to the Minecraft server
                new WebSocketProxyHandler(config, logger));
    }
}
