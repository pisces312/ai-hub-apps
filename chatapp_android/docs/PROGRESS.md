
# ChatApp Android - 开发进度

## 当前状态

**日期**：2026-06-26
**版本**：1.0

---

## 已完成的功能

### 1. 外部存储模型支持

- **APK 大小优化**：从 3.0 GB 降至 ~90 MB
- **修改文件**：
  - `AndroidManifest.xml`：添加存储权限
  - `MainActivity.java`：新增外部路径检测、设置对话框
  - `Conversation.java`：接收模型路径参数
  - `activity_main.xml`：新增"Set Model Path"按钮
  - `build.gradle`：移除模型文件打包，清除 `.bin` 排除逻辑

- **模型管理**：
  ```
  PC 集中管理:  D:\models\qnn\<模型名>\     （配置 + .bin 完整 bundle）
  设备部署路径: /sdcard/ChatApp/models/    （adb push 推送）
  
  当前模型:     D:\models\qnn\qwen3_4b_instruct\
  ```
  1. 模型按类型分目录：`D:\models\qnn\qwen3_4b_instruct\`（含 genie_config.json / tokenizer.json / *.bin 等）
  2. 部署到设备：`adb push D:\models\qnn\qwen3_4b_instruct\ /sdcard/ChatApp/models/llm/`
  3. 启动 App → 点击"Set Model Path" → 输入 `/sdcard/ChatApp/models/llm`
  4. 重启 App 生效

### 2. SM8850 专用构建脚本

- **新增文件**：`build-sm8850.bat`（Windows）
- **功能**：
  - 仅包含 QNN HTP v81 库（SM8850 使用）
  - 排除 v68/v69/v73/v75/v81 以外的其他库
  - 更小的 APK 体积

---

## 项目文件变更

### 修改的文件

| 文件 | 变更内容 |
|------|----------|
| `AndroidManifest.xml` | 添加 `READ_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE` 权限 |
| `MainActivity.java` | 外部路径检测、SAF 文件夹选择器、启动时申请全文件权限 |
| `Conversation.java` | 接收传入的模型目录路径 |
| `activity_main.xml` | 新增"Set Model Path"按钮 |
| `strings.xml` | 添加新字符串资源 |
| `build.gradle` | 移除模型文件打包，模型全部外部管理 |

### 新增文件

| 文件 | 说明 |
|------|----------|
| `build-sm8850.bat` | SM8850 专用构建脚本（仅 v81 库） |
| `docs/PROGRESS.md` | 本文档 - 开发进度记录 |

---

## 构建命令

### 通用构建（包含所有 HTP 库版本）

```bash
cd chatapp_android
gradle assembleDebug
```

### SM8850 专用构建（仅 v81 库）

Windows:
```cmd
cd chatapp_android
build-sm8850.bat
```

---

## APK 输出

- **位置**：`build/outputs/apk/debug/app-debug.apk`
- **大小**：~90 MB（不含模型文件）
- **包含**：
  - 所有 native 库（Genie + QNN HTP）

---

## 安装到设备

```bash
# 1. 安装 APK
adb install -r build/outputs/apk/debug/app-debug.apk

# 2. 推送模型文件（从 PC 集中目录到设备）
adb push D:\models\qnn\qwen3_4b_instruct\ /sdcard/ChatApp/models/llm/

# 3. 启动 App → Set Model Path → Browse（选取 /sdcard/ChatApp/models/llm）
```

---

## 下一步计划

- [ ] 验证外部模型加载功能
- [ ] 添加更多模型版本支持
- [ ] UI 优化
