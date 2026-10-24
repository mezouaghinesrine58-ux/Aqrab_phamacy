package com.app.aqrab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentChatsAdapter extends RecyclerView.Adapter<RecentChatsAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(ChatSession session, int position);
    }

    private final List<ChatSession> sessionList;
    private final OnSessionClickListener clickListener;
    private final OnDeleteClickListener deleteListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

    public RecentChatsAdapter(List<ChatSession> sessionList, OnSessionClickListener clickListener, OnDeleteClickListener deleteListener) {
        this.sessionList = sessionList;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_chat, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ChatSession session = sessionList.get(position);
        holder.tvTitle.setText(session.getTitle());
        holder.tvTime.setText(dateFormat.format(new Date(session.getTimestamp())));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSessionClick(session);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(session, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvTime;
        ImageView btnDelete;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_session_title);
            tvTime = itemView.findViewById(R.id.tv_session_time);
            btnDelete = itemView.findViewById(R.id.btn_delete_session);
        }
    }
}