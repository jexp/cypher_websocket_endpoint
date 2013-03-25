package org.neo4j.remoting.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * @author mh
 * @since 15.02.13
 */
public class NettyWebsocket {
    public static class CustomTextFrameHandler extends ChannelInboundMessageHandlerAdapter<TextWebSocketFrame> {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
            String request = frame.text();
            System.out.println(request);
            ctx.channel().write(new TextWebSocketFrame(request.toUpperCase()));
        }
    }

    public static void main(String[] args) throws Exception {
        final ServerBootstrap sb = new ServerBootstrap();
        int port=8080;
        try {
            sb.group(new NioEventLoopGroup(), new NioEventLoopGroup())
             .channel(NioServerSocketChannel.class)
             .localAddress(port)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(final SocketChannel ch) throws Exception {
                     ch.pipeline().addLast(
                             new HttpRequestDecoder(),
                             new HttpObjectAggregator(65536),
                             new HttpResponseEncoder(),
                             new WebSocketServerProtocolHandler("/websocket"),
                             new CustomTextFrameHandler());
                 }
             });

            final Channel ch = sb.bind().sync().channel();
            System.out.println("Web socket server started at port " + port);

            ch.closeFuture().sync();
        } finally {
            sb.shutdown();
        }
    }
}
