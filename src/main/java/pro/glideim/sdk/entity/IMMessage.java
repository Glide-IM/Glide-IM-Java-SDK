package pro.glideim.sdk.entity;

import java.util.Objects;

import pro.glideim.sdk.GlideIM;
import pro.glideim.sdk.api.msg.GroupMessageBean;
import pro.glideim.sdk.api.msg.MessageBean;
import pro.glideim.sdk.protocol.ChatMessage;

public class IMMessage {
    private long mid;
    private long cliSeq;
    private long from;
    private long to;
    private int type;
    private long sendAt;
    private long createAt;
    private String content;

    private long targetId;
    private int targetType;
    private int state;

    private IMMessage() {
    }

    public static IMMessage fromChatMessage(ChatMessage message) {
        IMMessage m = new IMMessage();
        m.setMid(message.getMid());
        m.setCliSeq(message.getcSeq());
        m.setFrom(message.getFrom());
        m.setTo(message.getTo());
        m.setType(m.getType());
        m.setSendAt(message.getcTime());
        m.setCreateAt(message.getcTime());
        m.setContent(message.getContent());
        m.setState(message.getState());
        long id = 0;
        if (m.from == GlideIM.getInstance().getMyUID()) {
            id = m.to;
        } else {
            id = m.from;
        }
        m.setTarget(1, id);
        return m;
    }

    public static IMMessage fromMessage(MessageBean messageBean) {
        IMMessage m = new IMMessage();
        m.setMid(messageBean.getMid());
        m.setCliSeq(messageBean.getCliSeq());
        m.setFrom(messageBean.getFrom());
        m.setTo(messageBean.getTo());
        m.setType(messageBean.getType());
        m.setSendAt(messageBean.getSendAt());
        m.setCreateAt(messageBean.getCreateAt());
        m.setContent(messageBean.getContent());
        long id = 0;
        if (m.from == GlideIM.getInstance().getMyUID()) {
            id = m.to;
        } else {
            id = m.from;
        }
        m.setTarget(1, id);
        return m;
    }

    public static IMMessage fromGroupMessage(GroupMessageBean messageBean) {
        IMMessage m = new IMMessage();
        m.setMid(messageBean.getMid());
        m.setCliSeq(0);
        m.setFrom(messageBean.getSender());
        m.setTo(0);
        m.setType(messageBean.getType());
        m.setSendAt(messageBean.getSentAt());
        m.setContent(messageBean.getContent());
        m.setTarget(2, m.to);
        return m;
    }

    private void setTarget(int type, long id) {
        this.targetType = type;
        this.targetId = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IMMessage imMessage = (IMMessage) o;
        return imMessage.mid == this.mid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mid);
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getTargetType() {
        return targetType;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public long getMid() {
        return mid;
    }

    public void setMid(long mid) {
        this.mid = mid;
    }

    public long getCliSeq() {
        return cliSeq;
    }

    public void setCliSeq(long cliSeq) {
        this.cliSeq = cliSeq;
    }

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

    public long getTargetId() {
        return targetId;
    }

    public void setTargetId(long targetId) {
        this.targetId = targetId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getSendAt() {
        return sendAt;
    }

    public void setSendAt(long sendAt) {
        this.sendAt = sendAt;
    }

    public long getCreateAt() {
        return createAt;
    }

    public void setCreateAt(long createAt) {
        this.createAt = createAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "IMMessage{" +
                "mid=" + mid +
                ", cliSeq=" + cliSeq +
                ", from=" + from +
                ", to=" + to +
                ", type=" + type +
                ", sendAt=" + sendAt +
                ", createAt=" + createAt +
                ", content='" + content + '\'' +
                '}';
    }
}
