package com.app.aqrab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiChatFragment extends Fragment {

    private EditText etChatInput;
    private ImageView btnSend;
    private TextView tvChatResponse;
    private GenerativeModelFutures model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gemini_chat, container, false);

        etChatInput = view.findViewById(R.id.et_chat_input);
        btnSend = view.findViewById(R.id.btn_send);
        tvChatResponse = view.findViewById(R.id.tv_chat_response);

        // Initialize Gemini
        String apiKey = getString(R.string.google_api_key);
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", apiKey);
        model = GenerativeModelFutures.from(gm);

        btnSend.setOnClickListener(v -> {
            String message = etChatInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message, view);
            }
        });

        return view;
    }

    private void sendMessage(String userMessage, View view) {
        etChatInput.setText("");
        tvChatResponse.setVisibility(View.VISIBLE);
        tvChatResponse.setText(R.string.thinking);
        hideWelcomeMessages(view);

        Content content = new Content.Builder()
                .addText(userMessage)
                .build();

        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = result.getText();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvChatResponse.setText(resultText));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvChatResponse.setText(R.string.error_chat));
                }
            }
        }, executor);
    }

    private void hideWelcomeMessages(View v) {
        View welcomePlant = v.findViewById(R.id.tv_welcome_plant);
        View welcomeTitle = v.findViewById(R.id.tv_welcome_title);
        View welcomeSubtitle = v.findViewById(R.id.tv_welcome_subtitle);
        if (welcomePlant != null) welcomePlant.setVisibility(View.GONE);
        if (welcomeTitle != null) welcomeTitle.setVisibility(View.GONE);
        if (welcomeSubtitle != null) welcomeSubtitle.setVisibility(View.GONE);
    }
}
