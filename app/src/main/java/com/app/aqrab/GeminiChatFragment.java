package com.app.aqrab;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiChatFragment extends Fragment {

    private static final String TAG = "GeminiChatFragment";
    private static final String PREF_NAME = "gemini_chat_prefs";
    private static final String KEY_SESSIONS_INDEX = "sessions_index";

    private EditText etChatInput;
    private ImageView btnCamera;
    private ImageView btnSend;

    private RecyclerView rvChatMessages;
    private View welcomeContainer;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();

    private FrameLayout imagePreviewContainer;
    private ImageView ivSelectedImage;
    private ImageView btnRemoveImage;

    private String currentChatId = String.valueOf(System.currentTimeMillis());

    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private Uri cameraImageUri;
    private Uri selectedImageUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && cameraImageUri != null) {
                        attachImage(cameraImageUri);
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        attachImage(uri);
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (Boolean.TRUE.equals(isGranted)) {
                        openCamera();
                    } else {
                        Toast.makeText(getContext(), "إذن الكاميرا مطلوب لالتقاط الصور", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gemini_chat, container, false);

        etChatInput = view.findViewById(R.id.et_chat_input);
        btnCamera = view.findViewById(R.id.btn_camera);
        btnSend = view.findViewById(R.id.btn_send);

        ImageView btnNewChat = view.findViewById(R.id.btn_new_chat);
        ImageView btnRecentChats = view.findViewById(R.id.btn_recent_chats);
        View chatInputContainer = view.findViewById(R.id.chat_input_container);

        rvChatMessages = view.findViewById(R.id.rv_chat_messages);
        welcomeContainer = view.findViewById(R.id.welcome_container);

        imagePreviewContainer = view.findViewById(R.id.image_preview_container);
        ivSelectedImage = view.findViewById(R.id.iv_selected_image);
        btnRemoveImage = view.findViewById(R.id.btn_remove_image);

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter(messageList);
        rvChatMessages.setAdapter(chatAdapter);

        // Window Insets Listener for IME Keyboard
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            int keyboardHeight = imeInsets.bottom;
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) || keyboardHeight > 150;

            if (getActivity() != null) {
                View bottomNav = getActivity().findViewById(R.id.bottom_nav_container);
                if (bottomNav != null) {
                    bottomNav.setVisibility(isKeyboardVisible ? View.GONE : View.VISIBLE);
                }
            }

            if (chatInputContainer != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chatInputContainer.getLayoutParams();
                if (params != null) {
                    if (isKeyboardVisible) {
                        int marginInDp = (int) (12 * getResources().getDisplayMetrics().density);
                        params.bottomMargin = marginInDp;
                        chatInputContainer.setLayoutParams(params);
                        chatInputContainer.setTranslationY(-keyboardHeight);
                    } else {
                        int marginInDp = (int) (115 * getResources().getDisplayMetrics().density);
                        params.bottomMargin = marginInDp;
                        chatInputContainer.setLayoutParams(params);
                        chatInputContainer.setTranslationY(0);
                    }
                }
            }

            if (rvChatMessages != null) {
                int bottomPadding = isKeyboardVisible ? (keyboardHeight + (int) (80 * getResources().getDisplayMetrics().density)) : (int) (12 * getResources().getDisplayMetrics().density);
                rvChatMessages.setPadding(rvChatMessages.getPaddingLeft(), rvChatMessages.getPaddingTop(), rvChatMessages.getPaddingRight(), bottomPadding);
                if (isKeyboardVisible && !messageList.isEmpty()) {
                    rvChatMessages.postDelayed(() -> rvChatMessages.smoothScrollToPosition(messageList.size() - 1), 100);
                }
            }

            return insets;
        });

        // Global Layout Listener Fallback
        keyboardLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean isKeyboardAlreadyOpen = false;

            @Override
            public void onGlobalLayout() {
                if (getContext() == null || getView() == null) return;

                Rect r = new Rect();
                view.getWindowVisibleDisplayFrame(r);
                int screenHeight = view.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                boolean isKeyboardOpen = keypadHeight > screenHeight * 0.15;

                if (isKeyboardOpen != isKeyboardAlreadyOpen) {
                    isKeyboardAlreadyOpen = isKeyboardOpen;

                    if (getActivity() != null) {
                        View bottomNav = getActivity().findViewById(R.id.bottom_nav_container);
                        if (bottomNav != null) {
                            bottomNav.setVisibility(isKeyboardOpen ? View.GONE : View.VISIBLE);
                        }
                    }

                    if (chatInputContainer != null) {
                        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chatInputContainer.getLayoutParams();
                        if (params != null) {
                            if (isKeyboardOpen) {
                                int marginInDp = (int) (12 * getResources().getDisplayMetrics().density);
                                params.bottomMargin = marginInDp;
                                chatInputContainer.setLayoutParams(params);
                                chatInputContainer.setTranslationY(-keypadHeight);
                            } else {
                                int marginInDp = (int) (115 * getResources().getDisplayMetrics().density);
                                params.bottomMargin = marginInDp;
                                chatInputContainer.setLayoutParams(params);
                                chatInputContainer.setTranslationY(0);
                            }
                        }
                    }

                    if (rvChatMessages != null) {
                        int bottomPadding = isKeyboardOpen ? (keypadHeight + (int) (80 * getResources().getDisplayMetrics().density)) : (int) (12 * getResources().getDisplayMetrics().density);
                        rvChatMessages.setPadding(rvChatMessages.getPaddingLeft(), rvChatMessages.getPaddingTop(), rvChatMessages.getPaddingRight(), bottomPadding);
                        if (isKeyboardOpen && !messageList.isEmpty()) {
                            rvChatMessages.postDelayed(() -> rvChatMessages.smoothScrollToPosition(messageList.size() - 1), 100);
                        }
                    }
                }
            }
        };

        view.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);

        if (etChatInput != null) {
            etChatInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && !messageList.isEmpty() && rvChatMessages != null) {
                    rvChatMessages.postDelayed(() -> rvChatMessages.smoothScrollToPosition(messageList.size() - 1), 200);
                }
            });
        }

        btnSend.setOnClickListener(v -> handleSendAction());

        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> showImagePickerDialog());
        }

        if (btnRemoveImage != null) {
            btnRemoveImage.setOnClickListener(v -> removeAttachedImage());
        }

        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> startNewChat());
        }

        if (btnRecentChats != null) {
            btnRecentChats.setOnClickListener(v -> showRecentChatsDialog());
        }

        setupSuggestionChips(view);
        updateWelcomeVisibility();

        return view;
    }

    private void setupSuggestionChips(View view) {
        TextView chip1 = view.findViewById(R.id.chip_1);
        TextView chip2 = view.findViewById(R.id.chip_2);
        TextView chip3 = view.findViewById(R.id.chip_3);

        if (chip1 != null) {
            chip1.setOnClickListener(v -> sendPredefinedPrompt("ما هي دواعي استخدام دواء الباراسيتامول؟"));
        }
        if (chip2 != null) {
            chip2.setOnClickListener(v -> showImagePickerDialog());
        }
        if (chip3 != null) {
            chip3.setOnClickListener(v -> sendPredefinedPrompt("ما الفرق بين أدوية الضغط وأدوية السكري؟"));
        }
    }

    private void sendPredefinedPrompt(String promptText) {
        etChatInput.setText(promptText);
        handleSendAction();
    }

    private void updateWelcomeVisibility() {
        if (welcomeContainer != null && rvChatMessages != null) {
            if (messageList.isEmpty()) {
                welcomeContainer.setVisibility(View.VISIBLE);
                rvChatMessages.setVisibility(View.GONE);
            } else {
                welcomeContainer.setVisibility(View.GONE);
                rvChatMessages.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showImagePickerDialog() {
        if (getContext() == null) return;
        String[] options = {getString(R.string.take_photo), getString(R.string.choose_gallery)};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_prescription)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndOpen();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndOpen() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        if (getContext() == null) return;
        try {
            File dir = requireContext().getExternalFilesDir(null);
            File file = new File(dir, "chat_scan_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    file
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Log.e(TAG, "Camera launch failed", e);
            Toast.makeText(getContext(), "تعذر فتح الكاميرا", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    private void attachImage(Uri uri) {
        selectedImageUri = uri;
        if (ivSelectedImage != null) {
            ivSelectedImage.setImageURI(uri);
        }
        if (imagePreviewContainer != null) {
            imagePreviewContainer.setVisibility(View.VISIBLE);
        }
    }

    private void removeAttachedImage() {
        selectedImageUri = null;
        if (imagePreviewContainer != null) {
            imagePreviewContainer.setVisibility(View.GONE);
        }
        if (ivSelectedImage != null) {
            ivSelectedImage.setImageURI(null);
        }
    }

    private void handleSendAction() {
        String userText = etChatInput.getText().toString().trim();
        Uri imageUri = selectedImageUri;

        if (imageUri == null && userText.isEmpty()) {
            return;
        }

        // Add user message & AI placeholder message to list
        int startPos = messageList.size();
        ChatMessage userMsg = new ChatMessage(userText, true, imageUri);
        ChatMessage aiLoadingMsg = new ChatMessage(getString(R.string.thinking), false, true);

        messageList.add(userMsg);
        messageList.add(aiLoadingMsg);

        int aiMsgIndex = messageList.size() - 1;

        // Reset input controls
        etChatInput.setText("");
        removeAttachedImage();
        updateWelcomeVisibility();

        chatAdapter.notifyItemRangeInserted(startPos, 2);
        rvChatMessages.smoothScrollToPosition(aiMsgIndex);

        saveCurrentChatSession();

        if (imageUri != null) {
            processImageWithUserQuestion(imageUri, userText, aiMsgIndex);
        } else {
            fetchAiResponse(userText, aiMsgIndex);
        }
    }

    private void processImageWithUserQuestion(Uri uri, String userQuestion, int aiMsgIndex) {
        if (getContext() == null) return;

        try {
            InputImage image = InputImage.fromFilePath(requireContext(), uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String detectedText = visionText.getText().trim();
                        StringBuilder promptBuilder = new StringBuilder();

                        if (!userQuestion.isEmpty()) {
                            promptBuilder.append("سؤال المستخدم: ").append(userQuestion).append("\n\n");
                        }

                        if (!detectedText.isEmpty()) {
                            promptBuilder.append("النص المكتشف والمستخرج من صورة الدواء/الوصفة:\n").append(detectedText);
                        } else {
                            promptBuilder.append("(ملاحظة: لم يكتشف المحلل نصاً واضحاً في الصورة، الرجاء تقديم النصيحة الصيدلانية المناسبة بخصوص طلب المستخدم).");
                        }

                        fetchAiResponse(promptBuilder.toString(), aiMsgIndex);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR Failure", e);
                        if (!userQuestion.isEmpty()) {
                            fetchAiResponse("سؤال المستخدم: " + userQuestion, aiMsgIndex);
                        } else {
                            updateAiMessage(aiMsgIndex, "❌ حدث خطأ أثناء قراءة الصورة.");
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Image load failed", e);
            updateAiMessage(aiMsgIndex, "❌ تعذر فتح الصورة. يرجى المحاولة مرة أخرى.");
        }
    }

    private void fetchAiResponse(String userPrompt, int aiMsgIndex) {
        executorService.execute(() -> {
            try {
                String systemPrompt = "You are Aqrab AI Health and Pharmacy Assistant, a helpful assistant in the Aqrab Android application. Answer the user politely and concisely in the user's language.";
                String encodedPrompt = URLEncoder.encode(userPrompt, "UTF-8");
                String encodedSystem = URLEncoder.encode(systemPrompt, "UTF-8");

                String urlString = "https://text.pollinations.ai/" + encodedPrompt + "?system=" + encodedSystem;

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line).append("\n");
                    }
                    reader.close();
                    in.close();

                    String reply = responseBuilder.toString().trim();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateAiMessage(aiMsgIndex, reply));
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateAiMessage(aiMsgIndex, getString(R.string.error_chat)));
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error fetching AI response", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateAiMessage(aiMsgIndex, getString(R.string.error_chat)));
                }
            }
        });
    }

    private void updateAiMessage(int index, String responseText) {
        if (index >= 0 && index < messageList.size()) {
            ChatMessage message = messageList.get(index);
            message.setText(responseText);
            message.setLoading(false);
            chatAdapter.notifyItemChanged(index);
            if (rvChatMessages != null) {
                rvChatMessages.smoothScrollToPosition(index);
            }
            saveCurrentChatSession();
        }
    }

    private void startNewChat() {
        messageList.clear();
        currentChatId = String.valueOf(System.currentTimeMillis());
        chatAdapter.notifyDataSetChanged();
        updateWelcomeVisibility();
        removeAttachedImage();
        etChatInput.setText("");
        Toast.makeText(getContext(), "تم بدء محادثة جديدة", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentChatSession() {
        if (getContext() == null || messageList.isEmpty()) return;

        try {
            JSONArray messagesArray = new JSONArray();
            String sessionTitle = "محادثة جديدة";

            for (ChatMessage msg : messageList) {
                if (msg.isLoading()) continue;

                JSONObject msgObj = new JSONObject();
                msgObj.put("text", msg.getText());
                msgObj.put("isUser", msg.isUser());
                if (msg.getImageUri() != null) {
                    msgObj.put("imageUri", msg.getImageUri().toString());
                }
                messagesArray.put(msgObj);

                if (msg.isUser() && "محادثة جديدة".equals(sessionTitle) && msg.getText() != null && !msg.getText().trim().isEmpty()) {
                    sessionTitle = msg.getText().trim();
                    if (sessionTitle.length() > 30) {
                        sessionTitle = sessionTitle.substring(0, 30) + "...";
                    }
                }
            }

            if (messagesArray.length() == 0) return;

            SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString("chat_msgs_" + currentChatId, messagesArray.toString());

            String indexJsonStr = prefs.getString(KEY_SESSIONS_INDEX, "[]");
            JSONArray indexArray = new JSONArray(indexJsonStr);
            JSONArray updatedIndex = new JSONArray();

            JSONObject currentSessionObj = new JSONObject();
            currentSessionObj.put("id", currentChatId);
            currentSessionObj.put("title", sessionTitle);
            currentSessionObj.put("timestamp", System.currentTimeMillis());

            updatedIndex.put(currentSessionObj);

            for (int i = 0; i < indexArray.length(); i++) {
                JSONObject obj = indexArray.getJSONObject(i);
                if (!obj.optString("id").equals(currentChatId)) {
                    updatedIndex.put(obj);
                }
            }

            editor.putString(KEY_SESSIONS_INDEX, updatedIndex.toString());
            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Error saving chat session", e);
        }
    }

    private List<ChatSession> loadSavedChatSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        if (getContext() == null) return sessions;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String indexJsonStr = prefs.getString(KEY_SESSIONS_INDEX, "[]");

        try {
            JSONArray indexArray = new JSONArray(indexJsonStr);
            for (int i = 0; i < indexArray.length(); i++) {
                JSONObject obj = indexArray.getJSONObject(i);
                String id = obj.getString("id");
                String title = obj.optString("title", "محادثة سابقة");
                long ts = obj.optLong("timestamp", System.currentTimeMillis());
                String msgsJson = prefs.getString("chat_msgs_" + id, "[]");

                sessions.add(new ChatSession(id, title, ts, msgsJson));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading sessions", e);
        }

        return sessions;
    }

    private void loadChatSession(ChatSession session) {
        try {
            messageList.clear();
            currentChatId = session.getId();

            JSONArray msgsArray = new JSONArray(session.getMessagesJson());
            for (int i = 0; i < msgsArray.length(); i++) {
                JSONObject obj = msgsArray.getJSONObject(i);
                String text = obj.optString("text", "");
                boolean isUser = obj.optBoolean("isUser", false);
                String uriStr = obj.has("imageUri") ? obj.getString("imageUri") : "";
                Uri imageUri = (!uriStr.isEmpty()) ? Uri.parse(uriStr) : null;

                messageList.add(new ChatMessage(text, isUser, imageUri));
            }

            chatAdapter.notifyDataSetChanged();
            updateWelcomeVisibility();
            if (!messageList.isEmpty() && rvChatMessages != null) {
                rvChatMessages.smoothScrollToPosition(messageList.size() - 1);
            }
            Toast.makeText(getContext(), "تم استرجاع المحادثة", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error loading session detail", e);
            Toast.makeText(getContext(), "تعذر تحميل المحادثة", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteChatSession(ChatSession session) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.remove("chat_msgs_" + session.getId());

        String indexJsonStr = prefs.getString(KEY_SESSIONS_INDEX, "[]");
        try {
            JSONArray indexArray = new JSONArray(indexJsonStr);
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < indexArray.length(); i++) {
                JSONObject obj = indexArray.getJSONObject(i);
                if (!obj.optString("id").equals(session.getId())) {
                    newArray.put(obj);
                }
            }
            editor.putString(KEY_SESSIONS_INDEX, newArray.toString());
            editor.apply();

            if (session.getId().equals(currentChatId)) {
                startNewChat();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting session", e);
        }
    }

    private void showRecentChatsDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_recent_chats, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(dialogView);

        RecyclerView rvRecent = dialogView.findViewById(R.id.rv_recent_chats);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_empty_recent);
        View btnNewChatDialog = dialogView.findViewById(R.id.btn_new_chat_dialog);

        List<ChatSession> sessions = loadSavedChatSessions();

        if (sessions.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvRecent.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvRecent.setVisibility(View.VISIBLE);

            rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
            RecentChatsAdapter adapter = new RecentChatsAdapter(
                    sessions,
                    session -> {
                        loadChatSession(session);
                        dialog.dismiss();
                    },
                    (session, position) -> {
                        deleteChatSession(session);
                        sessions.remove(position);
                        RecyclerView.Adapter<?> currAdapter = rvRecent.getAdapter();
                        if (currAdapter != null) {
                            currAdapter.notifyItemRemoved(position);
                        }
                        if (sessions.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvRecent.setVisibility(View.GONE);
                        }
                    }
            );
            rvRecent.setAdapter(adapter);
        }

        btnNewChatDialog.setOnClickListener(v -> {
            startNewChat();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getView() != null && keyboardLayoutListener != null) {
            getView().getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
        }
        executorService.shutdown();
    }
}