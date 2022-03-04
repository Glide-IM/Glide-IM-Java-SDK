package pro.glideim.sdk.ws;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import pro.glideim.sdk.im.ConnStateListener;

public class WsInboundChHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    ChannelPromise handshakeFuture;

    List<ConnStateListener> connStateListener = new CopyOnWriteArrayList<>();
    MessageListener messageListener;
    int connectionState = WsClient.STATE_CLOSED;
    private URI uri;

    private WsInboundChHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public WsInboundChHandler(URI uri) {
        this.uri = uri;
        this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true,
                new DefaultHttpHeaders());
    }

    public WsInboundChHandler copy() {
        WsInboundChHandler inboundChHandler = new WsInboundChHandler(this.uri);
        inboundChHandler.connStateListener = this.connStateListener;
        inboundChHandler.messageListener = this.messageListener;
        return inboundChHandler;
    }

    void onStateChanged(int state) {
        if (connectionState == state) {
            return;
        }
        connectionState = state;
        for (ConnStateListener stateListener : connStateListener) {
            stateListener.onStateChange(state, "");
        }
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        System.out.println("handlerAdded");
        handshakeFuture = ctx.newPromise();
        onStateChanged(WsClient.STATE_CONNECTING);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("channelActive");
        handshaker.handshake(ctx.channel());
        onStateChanged(WsClient.STATE_OPENED);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("channelInactive");
        onStateChanged(WsClient.STATE_CLOSED);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("channelRead0");
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                System.out.println("WebSocket Client connected!");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                System.out.println("WebSocket Client failed to connect");
                onStateChanged(WsClient.STATE_CLOSED);
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException("Unexpected FullHttpResponse (getStatus=" + response.getStatus()
                    + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        if (msg instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) msg;
//            ctx.writeAndFlush(textFrame.text());
            textMsg(ctx, textFrame);
        } else if (msg instanceof PongWebSocketFrame) {
            System.out.println("WebSocket Client received pong");
        } else if (msg instanceof CloseWebSocketFrame) {
            System.out.println("WebSocket Client received closing");
            ch.close();
        }
    }

    private void textMsg(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        System.out.println("WebSocket Client received message: " + frame.text());
        if (messageListener != null) {
            messageListener.onNewMessage(frame.text());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
        onStateChanged(WsClient.STATE_CLOSED);
    }

}