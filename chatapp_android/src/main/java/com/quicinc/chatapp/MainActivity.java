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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.EditText;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("chatapp");
    }

    /**
     * copyAssetsDir: Copies provided assets to output path
     */
    void copyAssetsDir(String inputAssetRelPath, String outputPath) throws IOException, NullPointerException {
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
            String input_sub_asset_path = Paths.get(inputAssetRelPath, subAssetName).toString();
            copyAssetsDir(input_sub_asset_path, outputPath);
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

    private static final String PREFS_NAME = "chatapp_prefs";
    private static final String KEY_MODEL_PATH = "model_path";

    // SAF directory picker for model path selection
    private final ActivityResultLauncher<Uri> storageDirPicker =
        registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                String path = getPathFromTreeUri(uri);
                if (path != null) {
                    saveAndApplyModelPath(path);
                } else {
                    Toast.makeText(this, "Cannot determine path. Try manual input.", Toast.LENGTH_LONG).show();
                }
            }
        });

    private boolean isValidModelDir(String path) {
        if (path == null || path.isEmpty()) return false;
        File configFile = new File(Paths.get(path, "genie_config.json").toString());
        return configFile.exists();
    }

    /**
     * Three-option dialog: Browse (SAF) / Type Path / Clear
     */
    private void showModelPathDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentPath = prefs.getString(KEY_MODEL_PATH, "");

        String message = currentPath.isEmpty()
            ? "No model path configured."
            : "Current: " + currentPath;

        new AlertDialog.Builder(this)
            .setTitle("Set Model Directory")
            .setMessage(message)
            .setPositiveButton("Browse", (dialog, which) -> {
                storageDirPicker.launch(null);
            })
            .setNeutralButton("Type Path", (dialog, which) -> {
                showManualPathInput();
            })
            .setNegativeButton("Clear", (dialog, which) -> {
                prefs.edit().remove(KEY_MODEL_PATH).apply();
                Toast.makeText(this, "Cleared.", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showManualPathInput() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentPath = prefs.getString(KEY_MODEL_PATH, "");

        final EditText input = new EditText(this);
        input.setText(currentPath);
        input.setHint("e.g., /sdcard/ChatApp/models/llm");
        input.setSingleLine();

        new AlertDialog.Builder(this)
            .setTitle("Enter Model Path")
            .setView(input)
            .setPositiveButton("OK", (dialog, which) -> {
                String path = input.getText().toString().trim();
                if (!path.isEmpty()) {
                    saveAndApplyModelPath(path);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveAndApplyModelPath(String path) {
        if (isValidModelDir(path)) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_MODEL_PATH, path).apply();
            Toast.makeText(this, "Model path set. Restarting...", Toast.LENGTH_SHORT).show();
            recreate();
        } else {
            Toast.makeText(this,
                "genie_config.json not found at " + path, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Extract real filesystem path from a SAF tree URI.
     */
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

    /**
     * Request MANAGE_EXTERNAL_STORAGE at startup (MNN-style).
     */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request all-files access at startup (like MNN)
        checkStoragePermission();

        setContentView(R.layout.activity_main);

        // Set up button listeners (always, regardless of model state)
        Button setModelPathBtn = (Button) findViewById(R.id.set_model_path);
        setModelPathBtn.setOnClickListener(view -> showModelPathDialog());

        try {
            HashMap<String, String> supportedSocModel = new HashMap<>();
            supportedSocModel.putIfAbsent("SM8850", "qualcomm-snapdragon-8-elite.json");
            supportedSocModel.putIfAbsent("SM8750", "qualcomm-snapdragon-8-elite.json");
            supportedSocModel.putIfAbsent("SM8650", "qualcomm-snapdragon-8-gen3.json");
            supportedSocModel.putIfAbsent("QCS8550", "qualcomm-snapdragon-8-gen2.json");

            String socModel = android.os.Build.SOC_MODEL;
            if (!supportedSocModel.containsKey(socModel)) {
                String errorMsg = "Unsupported device: " + socModel;
                Log.e("ChatApp", errorMsg);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String customModelPath = prefs.getString(KEY_MODEL_PATH, "");
            String modelDirPath;
            boolean usingExternalPath = false;

            if (!customModelPath.isEmpty() && isValidModelDir(customModelPath)) {
                modelDirPath = customModelPath;
                usingExternalPath = true;
                Log.i("ChatApp", "Using external model path: " + modelDirPath);
                Toast.makeText(this, "Using external model: " + modelDirPath, Toast.LENGTH_SHORT).show();
            } else {
                String errorMsg = "No model path configured. Please click 'Set Model Path' to configure.";
                Log.e("ChatApp", errorMsg);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            Path htpExtConfigPath = Paths.get(getExternalCacheDir().getAbsolutePath(), "htp_config", supportedSocModel.get(socModel));

            if (usingExternalPath && !htpExtConfigPath.toFile().exists()) {
                String externalDir = getExternalCacheDir().getAbsolutePath();
                try {
                    copyAssetsDir("htp_config", externalDir);
                } catch (IOException e) {
                    Log.w("ChatApp", "Failed to copy htp_config: " + e.getMessage());
                }
                htpExtConfigPath = Paths.get(externalDir, "htp_config", supportedSocModel.get(socModel));
            }

            String modelName = "No Model Found";
            File metadataFile = new File(Paths.get(modelDirPath, "metadata.json").toString());
            if (metadataFile.exists()) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(metadataFile.toPath()));
                    org.json.JSONObject metadata = new org.json.JSONObject(content);
                    if (metadata.has("model_name")) {
                        modelName = metadata.getString("model_name");
                    }
                } catch (Exception e) {
                    Log.w("ChatApp", "Could not read model_name from metadata.json: " + e.getMessage());
                }
            } else {
                Log.w("ChatApp", "metadata.json not found at: " + metadataFile.getAbsolutePath());
            }
            final String finalModelName = modelName;
            final String finalModelDirPath = modelDirPath;
            final String finalHtpConfigPath = htpExtConfigPath.toString();

            Button llm = (Button) findViewById(R.id.llm);
            llm.setText("Chat with " + finalModelName);
            llm.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, Conversation.class);
                intent.putExtra(Conversation.cConversationActivityKeyHtpConfig, finalHtpConfigPath);
                intent.putExtra(Conversation.cConversationActivityKeyModelDir, finalModelDirPath);
                intent.putExtra(Conversation.cConversationActivityKeyModelName, "llm");
                startActivity(intent);
            });
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.toString();
            Log.e("ChatApp", errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
