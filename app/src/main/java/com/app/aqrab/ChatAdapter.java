package com.app.aqrab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private final List<ChatMessage> messageList;

    public ChatAdapter(List<ChatMessage> messageList) {
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        if (messageList.get(position).isUser()) {
            return VIEW_TYPE_USER;
        } else {
            return VIEW_TYPE_AI;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);
        if (holder instanceof UserViewHolder) {
            UserViewHolder userHolder = (UserViewHolder) holder;
            if (message.getText() != null && !message.getText().isEmpty()) {
                userHolder.tvUserText.setVisibility(View.VISIBLE);
                userHolder.tvUserText.setText(message.getText());
            } else {
                userHolder.tvUserText.setVisibility(View.GONE);
            }

            if (message.getImageUri() != null) {
                userHolder.cvUserImage.setVisibility(View.VISIBLE);
                userHolder.ivUserImage.setImageURI(message.getImageUri());
            } else {
                userHolder.cvUserImage.setVisibility(View.GONE);
            }
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            if (message.isLoading()) {
                aiHolder.llAiLoading.setVisibility(View.VISIBLE);
                if (message.getText() != null && !message.getText().isEmpty()) {
                    aiHolder.tvLoadingText.setText(message.getText());
                } else {
                    aiHolder.tvLoadingText.setText(R.string.thinking);
                }
                aiHolder.tvAiText.setVisibility(View.GONE);
            } else {
                aiHolder.llAiLoading.setVisibility(View.GONE);
                aiHolder.tvAiText.setVisibility(View.VISIBLE);
                aiHolder.tvAiText.setText(message.getText());
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserText;
        CardView cvUserImage;
        ImageView ivUserImage;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserText = itemView.findViewById(R.id.tv_user_text);
            cvUserImage = itemView.findViewById(R.id.cv_user_image);
            ivUserImage = itemView.findViewById(R.id.iv_user_image);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvAiText;
        LinearLayout llAiLoading;
        TextView tvLoadingText;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAiText = itemView.findViewById(R.id.tv_ai_text);
            llAiLoading = itemView.findViewById(R.id.ll_ai_loading);
            tvLoadingText = itemView.findViewById(R.id.tv_loading_text);
        }
    }
}