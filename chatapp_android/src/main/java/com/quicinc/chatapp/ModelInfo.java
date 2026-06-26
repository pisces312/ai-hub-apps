package com.quicinc.chatapp;

/**
 * Descriptor for a discovered model under the base path.
 */
public class ModelInfo {
    public final String subDir;       // e.g. "qwen3_4b_instruct"
    public final String displayName;  // from metadata.json or fallback to subDir
    public final String fullPath;     // base + "/" + subDir

    public ModelInfo(String subDir, String displayName, String fullPath) {
        this.subDir = subDir;
        this.displayName = displayName;
        this.fullPath = fullPath;
    }
}
