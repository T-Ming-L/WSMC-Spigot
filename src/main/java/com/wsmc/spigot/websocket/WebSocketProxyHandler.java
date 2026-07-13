package com.wsmc.spigot.websocket;

import com.wsmc.spigot.WSMCConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Bridges a WebSocket connection into Spigot's normal Minecraft pipeline.
 *
 * <p>
 * Instead of handling Minecraft protocol directly, this handler connects to
 * <b>localhost</b> on Spigot's own listening port. Spigot then handles login,
 * authentication and gameplay normally.
 *
 * <p>
 * Data flow:
 * 
 * <pre>
 * Client WS BinaryFrame  ──►  localhost:spigotPort (Spigot)
 * Spigot                 ──►  BinaryFrame → Client
 * </pre>
 */
public class WebSocketProxyHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final WSMCConfig config;
    private final Logger logger;

    private Channel backendChannel;
    private NioEventLoopGroup backendGroup;
    /** Real client IP resolved from WebSocket handshake headers. */
    private InetSocketAddress realClientAddr;

    public WebSocketProxyHandler(WSMCConfig config, Logger logger) {
        super(false); // Do not auto-release – we forward the buffer
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (config.isDebug()) {
            logger.info("[WSMC] Client channel active: " + ctx.channel().remoteAddress());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // WebSocket handshake complete – capture real IP, then connect.
        if (evt instanceof io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete complete) {
            // Resolve real client IP
            this.realClientAddr = resolveRealClientAddress(ctx, complete);

            if (config.isDebug()) {
                logger.info("[WSMC] WebSocket handshake complete, URI: " + complete.requestUri()
                        + ", realClient: " + realClientAddr);
            }
            connectToBackend(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Connect to the local Minecraft server.
     */
    private void connectToBackend(ChannelHandlerContext ctx) {
        InetSocketAddress backendAddr = new InetSocketAddress("127.0.0.1", config.getSpigotPort());

        backendGroup = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(backendGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new BackendHandler(ctx.channel(), config, logger));
                    }
                });

        ChannelFuture future = bootstrap.connect(backendAddr);
        future.addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                backendChannel = f.channel();

                // ── Inject Proxy Protocol V2 header ───────────────────
                if (config.isProxyProtocolEnabled() && realClientAddr != null) {
                    ByteBuf ppHeader = backendChannel.alloc().buffer(52);
                    InetSocketAddress localAddr = (InetSocketAddress) backendChannel.localAddress();
                    ProxyProtocolV2Encoder.encode(ppHeader, realClientAddr, localAddr);
                    backendChannel.writeAndFlush(ppHeader);
                    if (config.isDebug()) {
                        logger.info("[WSMC] Sent Proxy Protocol V2 header (" + ppHeader.readableBytes()
                                + " bytes) for " + realClientAddr + " → " + localAddr);
                    }
                }

                if (config.isDebug()) {
                    logger.info("[WSMC] Connected to backend: " + backendAddr);
                }
            } else {
                logger.severe("[WSMC] Failed to connect to backend " + backendAddr + ": "
                        + f.cause().getMessage());
                ctx.channel().writeAndFlush(
                        new CloseWebSocketFrame(1011, "Failed to connect to backend"))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    // ── Real IP resolution ────────────────────────────────────────────

    /**
     * Resolve the real client IP address from the WebSocket handshake.
     * Checks the configured HTTP header first (e.g., X-Forwarded-For),
     * falls back to the direct socket remote address.
     */
    private InetSocketAddress resolveRealClientAddress(ChannelHandlerContext ctx,
            io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete complete) {
        if (config.isProxyProtocolEnabled()) {
            String headerName = config.getProxyProtocolRealIpHeader();
            String headerValue = complete.requestHeaders().get(headerName);
            if (headerValue != null && !headerValue.isEmpty()) {
                // X-Forwarded-For may contain proxy chain; take the first (original) IP
                String ip = headerValue.split(",")[0].trim();
                try {
                    // Parse with optional port
                    if (ip.contains(":")) {
                        String[] parts = ip.split(":");
                        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                    }
                    return new InetSocketAddress(ip, 0);
                } catch (Exception e) {
                    logger.warning("[WSMC] Failed to parse " + headerName + " header value '"
                            + headerValue + "', using direct address");
                }
            }
        }
        // Fallback: direct socket address
        java.net.SocketAddress remote = ctx.channel().remoteAddress();
        if (remote instanceof InetSocketAddress isa) {
            return isa;
        }
        return null;
    }

    // ── Client → Backend ───────────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            // Forward binary frame content to backend
            ByteBuf content = binaryFrame.content();
            if (config.isDumpBytes()) {
                logger.info("[WSMC] C→S (" + content.readableBytes() + " bytes):\n"
                        + ByteBufUtil.prettyHexDump(content));
            }

            if (backendChannel != null && backendChannel.isActive()) {
                // Retain before writing since auto-release is off
                backendChannel.writeAndFlush(content.retain());
            }
        } else if (frame instanceof CloseWebSocketFrame) {
            // Client closed WebSocket
            if (backendChannel != null) {
                backendChannel.close();
            }
            ctx.channel().writeAndFlush(frame.retain()).addListener(ChannelFutureListener.CLOSE);
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────────

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (backendChannel != null) {
            backendChannel.close();
        }
        if (backendGroup != null) {
            backendGroup.shutdownGracefully();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.severe("[WSMC] Error in WebSocket proxy: " + cause.getMessage());
        if (config.isDebug()) {
            cause.printStackTrace();
        }
        ctx.close();
    }

    // ── Backend → Client Handler (inner class) ─────────────────────────

    /**
     * Handles data from the Minecraft server and forwards it
     * as binary WebSocket frames back to the client.
     */
    private static class BackendHandler extends ChannelInboundHandlerAdapter {

        private final Channel clientChannel;
        private final WSMCConfig config;
        private final Logger logger;

        BackendHandler(Channel clientChannel, WSMCConfig config, Logger logger) {
            this.clientChannel = clientChannel;
            this.config = config;
            this.logger = logger;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                if (config.isDumpBytes()) {
                    logger.info("[WSMC] S→C (" + buf.readableBytes() + " bytes):\n"
                            + ByteBufUtil.prettyHexDump(buf));
                }

                if (clientChannel.isActive()) {
                    clientChannel.writeAndFlush(new BinaryWebSocketFrame(buf.retain()));
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // Backend disconnected – close the WebSocket
            if (clientChannel.isActive()) {
                clientChannel.writeAndFlush(new CloseWebSocketFrame())
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.severe("[WSMC] Backend connection error: " + cause.getMessage());
            ctx.close();
        }
    }
}
