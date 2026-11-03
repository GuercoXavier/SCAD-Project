package scad.sod;

import java.io.Serializable;

public class Message implements Serializable {
    public enum Type { REQUEST, REPLY, BERKELEY_TIME_REQ, BERKELEY_TIME_RES, BERKELEY_ADJUST }

    public Type type;
    public int senderId;
    public long timestamp;
    public long timeValue; // Usado para o Berkeley

    public Message(Type type, int senderId, long timestamp) {
        this.type = type;
        this.senderId = senderId;
        this.timestamp = timestamp;
    }
}