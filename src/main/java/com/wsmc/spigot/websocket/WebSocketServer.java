package com.wsmc.spigot.websocket;

import com.wsmc.spigot.WSMCConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.logging.Logger;

/**
 * Netty-based WebSocket server that listens for incoming WebSocket connections
 * and proxies them to the local Minecraft server.
 */
public class WebSocketServer {

    private final WSMCConfig config;
    private final Logger logger;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebSocketServer(WSMCConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Start the WebSocket server.
     */
    public ChannelFuture start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new WebSocketChannelInitializer(config, logger))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        ChannelFuture future = bootstrap.bind(config.getWsPort()).sync();
        serverChannel = future.channel();

        if (config.isDebug()) {
            logger.info("[WSMC] WebSocket server bound to port " + config.getWsPort());
        }

        return future;
    }

    /**
     * Gracefully shut down the WebSocket server.
     */
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("[WSMC] WebSocket server shut down.");
    }
}
