package io.github.samera2022.chinese_chess.server.net;

import io.github.samera2022.chinese_chess.server.match.MatchmakingServiceImpl;
import io.github.samera2022.chinese_chess.server.room.RoomManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty WebSocket 服务端启动器。
 * 绑定端口，配置 Netty 管道：HTTP 编解码 → HTTP 聚合器 → WebSocket 协议升级 → 业务处理器。
 */
public class NettyWsServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyWsServer.class);

    private final int port;
    private final RoomManager roomManager;
    private final MatchmakingServiceImpl matchmakingService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 构造 Netty WebSocket 服务端。
     *
     * @param port               监听端口
     * @param roomManager        房间管理器（注入）
     * @param matchmakingService 匹配服务实现（注入）
     */
    public NettyWsServer(int port, RoomManager roomManager, MatchmakingServiceImpl matchmakingService) {
        this.port = port;
        this.roomManager = roomManager;
        this.matchmakingService = matchmakingService;
    }

    /**
     * 启动服务端：创建 EventLoopGroup，配置 ServerBootstrap 管道，绑定端口。
     *
     * @throws Exception 绑定失败时抛出
     */
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        int threads = Runtime.getRuntime().availableProcessors() * 2;
        workerGroup = new NioEventLoopGroup(threads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new WebSocketServerProtocolHandler("/chess"))
                                .addLast(new WsSessionHandler(roomManager, matchmakingService));
                    }
                });

        Channel future = bootstrap.bind(port).sync().channel();
        this.serverChannel = future;
        logger.info("WebSocket 服务端已启动，端口: {}", port);
    }

    /**
     * 停止服务端：关闭 server channel 并优雅关闭 EventLoopGroup。
     */
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("WebSocket 服务端已停止");
    }
}
