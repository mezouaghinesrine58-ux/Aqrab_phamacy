package com.app.aqrab;

import android.net.Uri;

public class ChatMessage {
    private String text;
    private boolean isUser;
    private Uri imageUri;
    private boolean isLoading;

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.imageUri = null;
        this.isLoading = false;
    }

    public ChatMessage(String text, boolean isUser, Uri imageUri) {
        this.text = text;
        this.isUser = isUser;
        this.imageUri = imageUri;
        this.isLoading = false;
    }

    public ChatMessage(String text, boolean isUser, boolean isLoading) {
        this.text = text;
        this.isUser = isUser;
        this.imageUri = null;
        this.isLoading = isLoading;
    }

    public ChatMessage(String text, boolean isUser, Uri imageUri, boolean isLoading) {
        this.text = text;
        this.isUser = isUser;
        this.imageUri = imageUri;
        this.isLoading = isLoading;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isUser() {
        return isUser;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }
}