package pro.glideim.sdk.im;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.internal.operators.observable.ObservableSubscribeOn;
import pro.glideim.sdk.GlideException;
import pro.glideim.sdk.Logger;
import pro.glideim.sdk.ParameterizedTypeImpl;
import pro.glideim.sdk.http.RetrofitManager;
import pro.glideim.sdk.messages.AckMessage;
import pro.glideim.sdk.messages.AckRequest;
import pro.glideim.sdk.messages.Actions;
import pro.glideim.sdk.messages.ChatMessage;
import pro.glideim.sdk.messages.CommMessage;
import pro.glideim.sdk.messages.GroupMessage;
import pro.glideim.sdk.utils.SLogger;
import pro.glideim.sdk.ws.RetrofitWsClient;
import pro.glideim.sdk.ws.WsClient;

public class IMClientImpl implements IMClient {

    private static final String TAG = "WsIMClientImp";
    private static final int MESSAGE_VER = 1;
    private static final int TIMEOUT_REQUEST_SEC = 5;
    private static final int TIMEOUT_MESSAGE = 5;
    private static final int SEND_MESSAGE_RETRY_TIMES = 5;

    private final Logger logger;
    private final WsClient connection;
    private final Map<Long, RequestEmitter> requests = new ConcurrentHashMap<>();
    private final Map<Long, MessageEmitter> messageSending = new ConcurrentHashMap<>();
    private final Type typeCommMsg = new TypeToken<CommMessage<Object>>() {
    }.getType();
    private final Heartbeat heartbeat;
    private final KeepAlive keepAlive;
    private MessageListener messageListener;
    private long seq;

    private IMClientImpl(String wsUrl) {
        connection = new RetrofitWsClient(wsUrl);
        heartbeat = Heartbeat.start(this);
        keepAlive = KeepAlive.create(connection);
        connection.setMessageListener(msg -> {
            try {
                onMessage(new Message(msg));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        logger = SLogger.getLogger();
    }

    public static IMClientImpl create(String wsUrl) {
        return new IMClientImpl(wsUrl);
    }

    private static <T> T deserialize(Type t, Message msg) {
        return RetrofitManager.fromJson(t, msg.message);
    }

    @Override
    public void addConnStateListener(ConnStateListener connStateListener) {
        this.connection.addStateListener(connStateListener);
    }

    @Override
    public void removeConnStateListener(ConnStateListener connStateListener) {
        this.connection.removeStateListener(connStateListener);
    }

    @Override
    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    public boolean isConnected() {
        return connection.isConnected();
    }

    @Override
    public Single<Boolean> connect() {
        return connection.connect()
                .doOnSuccess(aBoolean -> {
                    heartbeat.start();
                    keepAlive.start();
                });
    }

    @Override
    public void disconnect() {
        heartbeat.stop();
        keepAlive.stop();
        connection.disconnect();
    }

    @Override
    public boolean send(Object obj) {
        if (!connection.isConnected()) {
            return false;
        }
        logger.d(TAG, "send message:" + RetrofitManager.toJson(obj));
        return connection.write(obj);
    }

    @Override
    public WsClient getWebSocketClient() {
        return this.connection;
    }

    public Observable<ChatMessage> resendMessage(ChatMessage message) {
        return sendMessage(Actions.Cli.ACTION_MESSAGE_CHAT_RESEND, message);
    }

    public Observable<ChatMessage> sendChatMessage(ChatMessage message) {
        return sendMessage(Actions.Srv.ACTION_MESSAGE_CHAT, message);
    }

    public Observable<ChatMessage> sendGroupMessage(ChatMessage message) {
        return sendMessage(Actions.Srv.ACTION_MESSAGE_GROUP, message);
    }

    @Override
    public <T> Observable<CommMessage<T>> request(String action, Class<T> clazz, boolean isArray, Object data) {
        if (!connection.isConnected()) {
            return Observable.error(new Exception("the server is not connected"));
        }
        CommMessage<Object> m = new CommMessage<>(MESSAGE_VER, action, ++seq, data);

        Type t;
        if (isArray) {
            t = new ParameterizedTypeImpl(List.class, new Class[]{clazz});
            t = new ParameterizedTypeImpl(CommMessage.class, new Type[]{t});
        } else {
            t = new ParameterizedTypeImpl(CommMessage.class, new Class[]{clazz});
        }
        final Type finalT = t;
        RequestEmitter e = new RequestEmitter(null, finalT);
        requests.put(m.getSeq(), e);
        return ObservableSubscribeOn.<CommMessage<T>>create(emitter -> {
            e.emitter = emitter;
            e.send(m);
        }).timeout(TIMEOUT_REQUEST_SEC, TimeUnit.SECONDS).doOnError(throwable ->
                keepAlive.check()
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public Observable<ChatMessage> sendMessage(final String action, final ChatMessage message) {
        if (!connection.isConnected()) {
            return Observable.error(new Exception("the server is not connected"));
        }
        if (message.getMid() == 0) {
            return Observable.error(new Exception("the message id is not initialized"));
        }

        MessageEmitter e = new MessageEmitter(null, message);
        messageSending.put(message.getMid(), e);
        Observable<ChatMessage> ob = ObservableSubscribeOn.create(emitter -> {
            e.emitter = emitter;
            e.send(action);
        });
        Observable<ChatMessage> flat = ob
                .timeout(TIMEOUT_MESSAGE, TimeUnit.SECONDS)
                .doOnNext(msg -> {
                    if (msg.getState() == ChatMessage.STATE_RCV_RECEIVED) {
                        messageSending.remove(msg.getMid());
                    }
                })
                .doOnError(throwable -> keepAlive.check());
        return flat;
    }


    private void onMessage(Message msg) {
        logger.d(TAG, "new message:" + msg.message);
        CommMessage<Object> m = deserialize(typeCommMsg, msg);
        m.setOrigin(msg.message);
        if (m.getAction().startsWith("api")) {
            if (requests.containsKey(m.getSeq())) {
                requests.get(m.getSeq()).respond(m, msg);
                requests.remove(m.getSeq());
            } else {
                logger.d(TAG, "unknown api response");
            }
            return;
        }
        logger.d(TAG, m.toString());
        switch (m.getAction()) {
            case Actions.Srv.ACTION_MESSAGE_FAILED:
                onSendFailed(msg);
                return;
            case Actions.Srv.ACTION_MESSAGE_CHAT:
                onChatMessage(msg);
                return;
            case Actions.Srv.ACTION_MESSAGE_GROUP:
                onGroupMessage(msg);
                return;
            case Actions.Srv.ACTION_ACK_MESSAGE:
            case Actions.Srv.ACTION_ACK_NOTIFY:
                onAck(msg);
                return;
            case Actions.ACTION_HEARTBEAT:
                send(new CommMessage<>(MESSAGE_VER, Actions.ACTION_HEARTBEAT, 0, ""));
                return;
            case Actions.Srv.ACTION_NOTIFY_NEED_AUTH:
                disconnect();
                connect();
                return;
            default:
                // not chat message
                onMessage(m);
        }
    }

    private void onMessage(CommMessage<Object> m) {
        switch (m.getAction()) {
            case Actions.Srv.ACTION_NOTIFY_ERROR:
            case Actions.Srv.ACTION_NOTIFY_LOGIN:
            case Actions.Srv.ACTION_NOTIFY_LOGOUT:
            case Actions.Srv.ACTION_NOTIFY_GROUP:
            case Actions.Srv.ACTION_KICK_OUT:
            case Actions.Srv.ACTION_NEW_CONTACT:
                messageListener.onControlMessage(m);
                return;
            default:
                logger.d(TAG, "UNKNOWN ACTION: " + m.getAction());
        }
    }

    private void onSendFailed(Message msg) {
        Type type = new TypeToken<CommMessage<AckMessage>>() {
        }.getType();
        CommMessage<AckMessage> m = deserialize(type, msg);
        AckMessage ack = m.getData();
        if (ack == null) {
            throw new NullPointerException("message data is null");
        }
        if (!messageSending.containsKey(ack.getMid())) {
            logger.d(TAG, "mid not exist");
            return;
        }
        MessageEmitter emitter = messageSending.get(m.getData().getMid());
        if (emitter != null) {
            emitter.onFailed(m);
        } else {
            logger.d(TAG, "message emitter null");
        }
    }

    private void onAck(Message msg) {
        Type type = new TypeToken<CommMessage<AckMessage>>() {
        }.getType();
        CommMessage<AckMessage> m = deserialize(type, msg);
        AckMessage ack = m.getData();
        if (ack == null) {
            throw new NullPointerException("ack message data is null");
        }
        if (!messageSending.containsKey(ack.getMid())) {
            logger.d(TAG, "ack mid not exist");
            return;
        }
        MessageEmitter emitter = messageSending.get(m.getData().getMid());
        if (emitter != null) {
            logger.d(TAG, "ack message mid=" + m.getData().getMid());
            emitter.onAck(m);
        } else {
            logger.d(TAG, "ack message emitter null");
        }
    }

    private void onGroupMessage(Message msg) {
        Type type = new TypeToken<CommMessage<GroupMessage>>() {
        }.getType();
        CommMessage<GroupMessage> c = deserialize(type, msg);
        GroupMessage cm = c.getData();
        if (messageListener != null) {
            messageListener.onGroupMessage(cm);
        }
        // ACK
        AckRequest a = new AckRequest(cm.getMid(), cm.getFrom(), 0);
        send(new CommMessage<>(MESSAGE_VER, Actions.Cli.ACTION_ACK_REQUEST, 0, a));
    }

    private void onChatMessage(Message msg) {
        Type type = new TypeToken<CommMessage<ChatMessage>>() {
        }.getType();
        CommMessage<ChatMessage> c = deserialize(type, msg);
        ChatMessage cm = c.getData();
        if (messageListener != null) {
            messageListener.onNewMessage(cm);
        }
        // ACK
        AckRequest a = new AckRequest(cm.getMid(), cm.getFrom(), 0);
        send(new CommMessage<>(MESSAGE_VER, Actions.Cli.ACTION_ACK_GROUP_MSG, 0, a));
    }

    @SuppressWarnings("rawtypes")
    private class RequestEmitter {
        private final Type type;
        private ObservableEmitter emitter;

        public RequestEmitter(ObservableEmitter emitter, Type t) {
            this.emitter = emitter;
            this.type = t;
        }

        void send(CommMessage<Object> m) {
            boolean success = IMClientImpl.this.send(m);
            if (!success) {
                emitter.onError(new Exception("message send failed"));
            }
        }

        void respond(CommMessage<Object> m, Message msg) {
            if (emitter.isDisposed()) {
                return;
            }
            if (m.getAction().equals("api.success")) {
                try {
                    CommMessage<Object> o = deserialize(type, msg);
                    //noinspection unchecked
                    emitter.onNext(o);
                } catch (Throwable e) {
                    this.emitter.onError(new Exception("json parse error, " + e.getMessage()));
                }
            } else {
                emitter.onError(new GlideException(m.getData().toString()));
            }
            emitter.onComplete();
        }
    }

    private class MessageEmitter {
        int retry;
        ChatMessage msg;
        private ObservableEmitter<ChatMessage> emitter;

        public MessageEmitter(ObservableEmitter<ChatMessage> emitter, ChatMessage msg) {
            this.emitter = emitter;
            this.msg = msg;
        }

        void send(String action) {
            CommMessage<ChatMessage> c = new CommMessage<>(MESSAGE_VER, action, 0, msg);
            emitter.onNext(msg.setState(ChatMessage.STATE_SRV_SENDING));
            boolean send = IMClientImpl.this.send(c);
            if (!send) {
                if (IMClientImpl.this.connection.isConnected()) {
                    emitter.onError(new Exception("send message failed"));
                } else {
                    emitter.onError(new Exception("send message failed due to socket closed"));
                }
            }
        }

        void onFailed(CommMessage<AckMessage> m) {
            if (emitter.isDisposed()) {
                return;
            }
            emitter.onNext(msg.setState(ChatMessage.STATE_SRV_FAILED));
            emitter.onComplete();
        }

        void onAck(CommMessage<AckMessage> a) {
            if (emitter.isDisposed()) {
                return;
            }
            switch (a.getAction()) {
                case Actions.Srv.ACTION_ACK_MESSAGE:
                    emitter.onNext(msg.setState(ChatMessage.STATE_SRV_RECEIVED));
                    break;
                case Actions.Srv.ACTION_ACK_NOTIFY:
                    emitter.onNext(msg.setState(ChatMessage.STATE_RCV_RECEIVED));
                    emitter.onComplete();
                    break;
            }
        }
    }
}
