package org.neo4j.remoting.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.HostnamePort;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.remoting.msgpack.ExecutionResultMessagePack;
import org.neo4j.remoting.transaction.TransactionRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.buffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.neo4j.helpers.collection.MapUtil.map;

public class CypherWebsocketExtension implements Lifecycle, Runnable {
    public final static String TX_ID = "tx_id";
    public final static String TX = "tx";
    public final static String PARAMS = "params";
    public final static String QUERY = "query";
    public final static String STATS = "stats";
    public final static String NO_RESULTS = "no_results";
    private final int PORT;
    private final int BUFFER_SIZE;
    private final StringLogger logger;
    private final int numThreads;
    private final ExecutionEngine engine;
    private final TransactionRegistry transactionRegistry;
    private AtomicBoolean running = new AtomicBoolean(false);
    private ServerBootstrap server;
    private AtomicInteger counter=new AtomicInteger();

    public CypherWebsocketExtension(GraphDatabaseService db, StringLogger logger, HostnamePort hostnamePort, int bufferSize, int numThreads) {
        this.logger = logger;
        this.numThreads = numThreads;
        engine = new ExecutionEngine(db);
        transactionRegistry = new TransactionRegistry(db);
        this.PORT = hostnamePort.getPort();
        this.BUFFER_SIZE = bufferSize;
    }

    private static Map<String, Object> beforeQuery(TransactionRegistry transactionRegistry, String tx, Number txId) throws Exception {
        if ("begin".equals(tx)) {
            return map(TX_ID, transactionRegistry.createTransaction(), TX, "begin");
        }
        if (txId != null) {
            transactionRegistry.selectCurrentTransaction(txId.longValue());
            return map(TX_ID, txId);
        }
        if ("rollback".equals(tx)) {
            transactionRegistry.rollbackCurrentTransaction();
            return map(TX_ID, -1, TX, "rollback");
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> afterQuery(TransactionRegistry transactionRegistry, String tx) throws Exception {
        if ("commit".equals(tx)) {
            transactionRegistry.commitCurrentTransaction();
            return map(TX_ID, -1, TX, "commit");
        } else {
            transactionRegistry.suspendCurrentTransaction();
            return Collections.emptyMap();
        }
    }

    @Override
    public void init() throws Throwable {

    }

    @Override
    public void start() throws Throwable {

        server = new ServerBootstrap();
        server.group(new NioEventLoopGroup(), new NioEventLoopGroup(numThreads))
                .channel(NioServerSocketChannel.class)
                .localAddress(PORT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(final SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                                //new HttpContentDecompressor(),
                                new HttpRequestDecoder(),
                                new HttpObjectAggregator(65536),
                                new HttpResponseEncoder(),
                                new WebSocketServerProtocolHandler("/websocket"),
                                new CypherResponseHandler()
                                //,new HttpContentCompressor()
                        );
                    }
                });
        running.set(true);
        final Thread runner = new Thread(this);
        runner.start();
    }

    @Override
    public void stop() throws Throwable {
        running.set(false);
        server.shutdown();
    }

    @Override
    public void shutdown() throws Throwable {
        stop();
    }


    @Override
    public void run() {
        try {
            final Channel ch = server.bind().sync().channel();
            logger.info("Started Cypher Remoting on port " + PORT);
            ch.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ExecutionResult execute(Map input, Map<String, Object> info) throws Exception {
        final Number txId = (Number) input.get(TX_ID);
        final String tx = (String) input.get(TX);

        @SuppressWarnings("unchecked") Map<String, Object> params = input.containsKey(PARAMS) ? (Map<String, Object>) input.get(PARAMS) : Collections.<String, Object>emptyMap();
        final String query = (String) input.get(QUERY);

        info.putAll(beforeQuery(transactionRegistry, tx, txId));

        ExecutionResult result = null;
        if (query != null) result = engine.execute(query, params);

        if (input.containsKey(NO_RESULTS)) result = null;

        info.putAll(afterQuery(transactionRegistry, tx));
        return result;
    }

    private class CypherResponseHandler extends ChannelInboundMessageHandlerAdapter<BinaryWebSocketFrame> {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) throws Exception {
            final ByteBuf buffer = frame.data();
            Channel channel = ctx.channel();
            Object requestData = null;
            try {
                int size = buffer.readableBytes();
                byte[] request = new byte[size];
                buffer.readBytes(request);
                requestData = org.neo4j.remoting.msgpack.MsgPack.unpack(request, org.neo4j.remoting.msgpack.MsgPack.UNPACK_RAW_AS_STRING);
                if (logger.isDebugEnabled()) {
                    logger.debug("Cypher Remoting, got query " + requestData);
                }
                boolean stats = false;
                ExecutionResult result = null;
                Map<String, Object> info = new HashMap<String, Object>();
                if (requestData instanceof String) {
                    result = execute(Collections.singletonMap(QUERY, (String) requestData), info);
                }
                if (requestData instanceof Map) {
                    Map input = (Map) requestData;
                    result = execute(input, info);
                    stats = Boolean.TRUE.equals(input.get(STATS));
                }
                ExecutionResultMessagePack encodedResults = new ExecutionResultMessagePack(result, stats, info);

                if (!encodedResults.hasNext()) {
                    channel.write(new BinaryWebSocketFrame());
                } else {
                    sendResults(channel, encodedResults);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Cypher Remoting, result stats " + encodedResults.createResultInfo());
                }

            } catch (Exception e) {
                e.printStackTrace();
                logger.warn("Error during remote cypher execution for " + requestData, e);
                final Map<String, Object> result = map();
                ExecutionResultMessagePack.addException(result, e);
                channel.write(new BinaryWebSocketFrame(wrappedBuffer((org.neo4j.remoting.msgpack.MsgPack.pack(result)))));
            } finally {
//                System.out.print(".");
                int cnt = counter.incrementAndGet();
                if ((cnt % 100) == 0) System.out.println(" " +cnt);
                // ctx.flush();
                // ctx.close();
            }
        }

        private void sendResults(Channel channel, ExecutionResultMessagePack result) {
            boolean first = true;
            ByteBuf sendData = buffer(BUFFER_SIZE);
            while (result.hasNext()) {
                byte[] next = result.next();
                if (sendData.writableBytes() < next.length) {
                    sendData.retain();
                    write(channel, first, sendData, false);
                    first = false;
                }
                sendData.writeBytes(next);
            }
            write(channel, first, sendData, true);
        }

        private void write(Channel channel, boolean first, ByteBuf data, final boolean finalFragment) {
            WebSocketFrame message = first ?
                    new BinaryWebSocketFrame(finalFragment, 0, data) :
                    new ContinuationWebSocketFrame(finalFragment, 0, data);
            channel.write(message);
            data.clear();
        }
    }
}