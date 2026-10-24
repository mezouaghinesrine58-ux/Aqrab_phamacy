package com.app.aqrab;

public class ChatSession {
    private final String id;
    private final String title;
    private final long timestamp;
    private final String messagesJson;

    public ChatSession(String id, String title, long timestamp, String messagesJson) {
        this.id = id;
        this.title = title;
        this.timestamp = timestamp;
        this.messagesJson = messagesJson;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessagesJson() {
        return messagesJson;
    }
}