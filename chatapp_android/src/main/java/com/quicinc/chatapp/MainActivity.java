// ---------------------------------------------------------------------
// Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package com.quicinc.chatapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("chatapp");
    }

    // --- asset helpers --------------------------------------------------------

    void copyAssetsDir(String inputAssetRelPath, String outputPath) throws IOException {
        File outputAssetPath = new File(Paths.get(outputPath, inputAssetRelPath).toString());
        String[] subAssetList = this.getAssets().list(inputAssetRelPath);
        if (subAssetList.length == 0) {
            if (!outputAssetPath.exists()) {
                copyFile(inputAssetRelPath, outputAssetPath);
            }
            return;
        }
        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs();
        }
        for (String subAssetName : subAssetList) {
            copyAssetsDir(Paths.get(inputAssetRelPath, subAssetName).toString(), outputPath);
        }
    }

    void copyFile(String inputFilePath, File outputAssetFile) throws IOException {
        InputStream in = this.getAssets().open(inputFilePath);
        OutputStream out = new FileOutputStream(outputAssetFile);
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    // --- constants ------------------------------------------------------------

    private static final String PREFS_NAME = "chatapp_prefs";
    private static final String KEY_MODEL_BASE_PATH = "model_base_path";

    // SAF directory picker
    private final ActivityResultLauncher<Uri> storageDirPicker =
        registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                String path = getPathFromTreeUri(uri);
                if (path != null) {
                    saveBasePath(path);
                } else {
                    Toast.makeText(this, "Cannot determine path. Try manual input.", Toast.LENGTH_LONG).show();
                }
            }
        });

    // --- model discovery ------------------------------------------------------

    private boolean isValidModelDir(String path) {
        if (path == null || path.isEmpty()) return false;
        return new File(Paths.get(path, "genie_config.json").toString()).exists();
    }

    private String readModelDisplayName(String modelDir) {
        File metaFile = new File(Paths.get(modelDir, "metadata.json").toString());
        if (metaFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(metaFile.toPath()));
                org.json.JSONObject meta = new org.json.JSONObject(content);
                if (meta.has("model_name")) {
                    return meta.getString("model_name");
                }
            } catch (Exception e) {
                Log.w("ChatApp", "Failed to read metadata.json: " + e.getMessage());
            }
        }
        return null;
    }

    private List<ModelInfo> discoverModels(String basePath) {
        List<ModelInfo> result = new ArrayList<>();
        File base = new File(basePath);
        if (!base.isDirectory()) return result;

        File[] children = base.listFiles();
        if (children == null) return result;

        for (File child : children) {
            if (!child.isDirectory()) continue;
            String subDir = child.getName();
            String fullPath = child.getAbsolutePath();
            if (isValidModelDir(fullPath)) {
                String displayName = readModelDisplayName(fullPath);
                if (displayName == null) displayName = subDir;
                result.add(new ModelInfo(subDir, displayName, fullPath));
            }
        }
        return result;
    }

    // --- UI construction ------------------------------------------------------

    private void buildModelList(List<ModelInfo> models) {
        // Get HTP config path (only needed once per SoC)
        String htpConfigPath = getHtpConfigPath();
        LinearLayout container = findViewById(R.id.models_container);
        container.removeAllViews();

        if (models.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No models found. Please set a base path containing models.");
            empty.setTextSize(14);
            empty.setPadding(0, 8, 0, 8);
            container.addView(empty);
            return;
        }

        for (ModelInfo info : models) {
            // Card wrapper
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, 8, 0, 24);

            // Model name
            TextView nameView = new TextView(this);
            nameView.setText(info.displayName);
            nameView.setTextSize(18);
            nameView.setPadding(0, 0, 0, 8);
            card.addView(nameView);

            // Chat button
            Button chatBtn = new Button(this);
            chatBtn.setText("Chat with " + info.displayName);
            final String modelDir = info.fullPath;
            final String modelName = info.subDir;
            final String htpPath = htpConfigPath;
            chatBtn.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, Conversation.class);
                intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, htpPath);
                intent.putExtra(Conversation.cConversationActivityKeyModelDir, modelDir);
                intent.putExtra(Conversation.cConversationActivityKeyModelName, modelName);
                startActivity(intent);
            });
            card.addView(chatBtn);

            container.addView(card);
        }
    }

    private String getHtpConfigPath() {
        HashMap<String, String> socMap = new HashMap<>();
        socMap.put("SM8850", "qualcomm-snapdragon-8-elite.json");
        socMap.put("SM8750", "qualcomm-snapdragon-8-elite.json");
        socMap.put("SM8650", "qualcomm-snapdragon-8-gen3.json");
        socMap.put("QCS8550", "qualcomm-snapdragon-8-gen2.json");

        String socModel = android.os.Build.SOC_MODEL;
        String htpFile = socMap.getOrDefault(socModel, null);
        if (htpFile == null) return "";

        // Check external cache first, then copy from assets if missing
        Path cachedPath = Paths.get(getExternalCacheDir().getAbsolutePath(), "htp_config", htpFile);
        if (!cachedPath.toFile().exists()) {
            try {
                copyAssetsDir("htp_config", getExternalCacheDir().getAbsolutePath());
            } catch (IOException e) {
                Log.w("ChatApp", "Failed to copy htp_config: " + e.getMessage());
            }
        }
        return cachedPath.toString();
    }

    private void updateBasePathLabel(String path) {
        TextView label = findViewById(R.id.base_path_label);
        if (path != null && !path.isEmpty()) {
            label.setText(path);
        } else {
            label.setText("No base path set");
        }
    }

    private void refreshModelList() {
        String basePath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_MODEL_BASE_PATH, "");
        updateBasePathLabel(basePath);

        if (!basePath.isEmpty()) {
            List<ModelInfo> models = discoverModels(basePath);
            buildModelList(models);
        } else {
            buildModelList(new ArrayList<>());
        }
    }

    // --- path selection dialogs -----------------------------------------------

    private void showModelPathDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentPath = prefs.getString(KEY_MODEL_BASE_PATH, "");

        String message = currentPath.isEmpty()
            ? "No base path configured."
            : "Current: " + currentPath;

        new AlertDialog.Builder(this)
            .setTitle("Set Model Base Directory")
            .setMessage(message)
            .setPositiveButton("Browse", (d, w) -> storageDirPicker.launch(null))
            .setNeutralButton("Type Path", (d, w) -> showManualPathInput())
            .setNegativeButton("Clear", (d, w) -> {
                prefs.edit().remove(KEY_MODEL_BASE_PATH).apply();
                refreshModelList();
                Toast.makeText(this, "Cleared.", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showManualPathInput() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentPath = prefs.getString(KEY_MODEL_BASE_PATH, "");

        final EditText input = new EditText(this);
        input.setText(currentPath);
        input.setHint("e.g., /sdcard/ChatApp/models");
        input.setSingleLine();

        new AlertDialog.Builder(this)
            .setTitle("Enter Base Path")
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String path = input.getText().toString().trim();
                if (!path.isEmpty()) {
                    saveBasePath(path);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveBasePath(String path) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_MODEL_BASE_PATH, path).apply();
        refreshModelList();
        Toast.makeText(this, "Base path set.", Toast.LENGTH_SHORT).show();
    }

    private String getPathFromTreeUri(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            String[] split = docId.split(":", 2);
            if (split.length < 2) return null;
            String type = split[0];
            String subPath = split[1];
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + subPath;
            } else {
                return "/storage/" + type + "/" + subPath;
            }
        } catch (Exception e) {
            Log.e("ChatApp", "Error extracting path from tree URI: " + e.getMessage());
            return null;
        }
    }

    // --- permission -----------------------------------------------------------

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w("ChatApp", "MANAGE_EXTERNAL_STORAGE not granted, requesting...");
                try {
                    Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("ChatApp", "Failed to open storage permission settings: " + e.getMessage());
                }
            } else {
                Log.i("ChatApp", "MANAGE_EXTERNAL_STORAGE granted");
            }
        }
    }

    // --- lifecycle ------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkStoragePermission();

        setContentView(R.layout.activity_main);

        // Set Model Path button
        Button setModelPathBtn = findViewById(R.id.set_model_path);
        setModelPathBtn.setOnClickListener(v -> showModelPathDialog());

        // Check SoC support
        try {
            HashMap<String, String> socMap = new HashMap<>();
            socMap.put("SM8850", "qualcomm-snapdragon-8-elite.json");
            socMap.put("SM8750", "qualcomm-snapdragon-8-elite.json");
            socMap.put("SM8650", "qualcomm-snapdragon-8-gen3.json");
            socMap.put("QCS8550", "qualcomm-snapdragon-8-gen2.json");

            String socModel = android.os.Build.SOC_MODEL;
            if (!socMap.containsKey(socModel)) {
                Toast.makeText(this, "Unsupported device: " + socModel, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e("ChatApp", "SoC check failed: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Refresh model list from saved base path
        refreshModelList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh model list (handles returning from Settings permission grant)
        refreshModelList();
    }
}
