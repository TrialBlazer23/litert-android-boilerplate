# Model Formats

This page explains the model formats supported by this boilerplate, when to use each, and how
to obtain them.

---

## Summary

| Format | Module | Use Case | Delivery |
|---|---|---|---|
| `.tflite` | `inference/` | Classical ML (classification, detection, embedding) | APK assets or device path |
| `.litertlm` | `inference-lm/` | Generative AI / LLM | Device path only |
| `.task` | (MediaPipe, not used here) | MediaPipe task bundle | N/A |

---

## .tflite — TensorFlow Lite / LiteRT Model

### What It Is

A FlatBuffers-serialized binary containing:
- Model graph (operations and their connectivity)
- Weights (either full precision or quantized)
- Input/output tensor metadata

`.tflite` is the original TensorFlow Lite format, now also consumed by Google LiteRT under the
`com.google.ai.edge.litert` namespace. The file format is identical — only the runtime library
changed names.

### When to Use It

Use `.tflite` for any task that is **not** generative AI or LLM:
- Image classification
- Object detection and segmentation
- Audio classification
- Pose estimation
- Text embedding and classification
- Tabular / structured data inference
- Custom neural networks

### Quantization in .tflite

For QNN NPU execution, models must be **INT8 quantized** (full integer or per-channel quantization).

| Quantization | QNN HTP | GPU | CPU |
|---|---|---|---|
| INT8 (full integer) | Yes (best performance) | Yes | Yes |
| INT8 (per-channel) | Yes | Yes | Yes |
| Float16 | No | Yes | Yes |
| Float32 | No | No | Yes |

If your model is float32 and you need QNN performance, convert it with INT8 quantization using
the `litert-torch` pipeline in `tools/conversion/working/`.

### Where to Get .tflite Models

**Pre-built (fastest path):**
- [litert-community on HuggingFace](https://huggingface.co/litert-community) — Google-maintained collection of LiteRT-compatible models, many with QNN optimization already applied
- [TensorFlow Hub](https://tfhub.dev) — filter by TFLite format; classic models (MobileNet, EfficientNet, BERT)
- [Kaggle Models](https://www.kaggle.com/models) — filter by TFLite; includes detection and classification models
- [Qualcomm AI Hub](https://aihub.qualcomm.com) — Snapdragon-optimized models, pre-compiled for QNN; download the `.tflite` artifact (not the `.qnn` context binary, which is device-locked)

**Convert your own:**
- HuggingFace PyTorch model → `litert-torch` → `.tflite` (see `tools/conversion/working/huggingface_to_tflite.py`)
- TensorFlow SavedModel → `tf.lite.TFLiteConverter` → `.tflite` (see `tools/conversion/working/savedmodel_to_tflite.py`)
- ONNX → `onnx2tf` → `.tflite` (see `tools/conversion/experimental/onnx_to_tflite.py`) — community tool, variable quality, not recommended for production

### Where to Place .tflite Models

```
app/src/main/assets/models/your_model.tflite
```

Models up to ~100 MB are appropriate for APK assets. Larger models should be placed on device
storage and loaded via absolute path — update `ModelConfig.modelPath` accordingly.

---

## .litertlm — LiteRT Language Model Format

### What It Is

A proprietary Google format for packaging Large Language Models for on-device inference via the
LiteRT-LM API. It contains:
- Model weights (quantized, typically INT4 or INT8)
- Tokenizer (SentencePiece or similar)
- KV cache configuration
- Backend-specific optimization metadata

`.litertlm` is not a `.tflite` file and cannot be loaded by the LiteRT `InterpreterApi`. It
requires the `com.google.ai.edge.litertlm` runtime.

### When to Use It

Use `.litertlm` for:
- Chat and question-answering
- Text summarization
- Code completion
- Any task requiring multi-turn conversation with a generative model

### Supported Models

As of April 2026, the following models are available in `.litertlm` format:
- **Gemma 2B** (Google DeepMind) — general purpose, well-supported
- **Gemma 7B** (requires high-end device; 4–6 GB RAM for INT4)
- **Phi-2 2.7B** (Microsoft, via litert-community)
- **Llama 3.2 1B and 3B** (Meta, via litert-community)
- **Falcon 1B** (TII, via litert-community)

### Where to Get .litertlm Models

**HuggingFace litert-community:**
The [litert-community organization](https://huggingface.co/litert-community) maintains a growing
collection of `.litertlm` models. Each model card specifies device requirements and benchmark numbers.

Download example:
```bash
# Install HuggingFace CLI
pip install huggingface_hub

# Download Gemma 2B INT4 for LiteRT-LM
huggingface-cli download litert-community/gemma-2b-it-litert-lm \
    --include "*.litertlm" \
    --local-dir ./models/
```

**Google AI Edge model collection:**
Google publishes reference `.litertlm` models at:
https://ai.google.dev/edge/litert/inference/litert-lm#models

**Convert your own:**
Use the `litert-torch` conversion pipeline:
```bash
# HuggingFace ID → .litertlm (see tools/conversion/working/)
python tools/conversion/working/huggingface_to_litertlm.py \
    --model_id meta-llama/Llama-3.2-1B-Instruct \
    --output ./models/llama3.2-1b.litertlm \
    --quantization int4
```

**GGUF → .litertlm (experimental):**
GGUF models (from llama.cpp community) can be converted via dequantization → safetensors →
litert-torch, but this is lossy and unreliable. Use only when no other source exists.
See `tools/conversion/experimental/gguf_to_litertlm.py`.

### Placement and Loading

`.litertlm` models must be at **absolute device paths**. The LiteRT-LM `Engine` does not support
`assets://` URIs.

Push to device:
```bash
# Create directory
adb shell mkdir -p /sdcard/models/

# Push model
adb push your_model.litertlm /sdcard/models/

# Verify
adb shell ls -lh /sdcard/models/
```

Configure in `AppConfig`:
```kotlin
const val LM_MODEL_PATH = "/sdcard/models/your_model.litertlm"
```

**For production distribution:** Use your own model download and storage infrastructure. Do not
distribute `.litertlm` files in your APK. Implement download-on-first-launch with progress
tracking and store to `context.filesDir` (app-private, no storage permission required).

---

## .task — MediaPipe Task Bundle (Not Used in This Boilerplate)

### What It Is

`.task` is a bundle format used by the [MediaPipe Tasks API](https://ai.google.dev/edge/mediapipe/solutions/guide).
It packages a `.litertlm` or `.tflite` model together with task-specific pre/post-processing
metadata into a single ZIP archive.

### Relationship to .litertlm

The LLM Inference Task in MediaPipe (the `LlmInference` class) uses `.task` files that internally
contain `.litertlm` model weights. The task bundle adds tokenizer config and sampling parameters
on top.

### Why .task Is Not Used Here

This boilerplate uses the LiteRT-LM API directly (`inference-lm/`) rather than MediaPipe Tasks.
The direct API gives more control over backend selection, streaming behavior, and conversation
management.

If you want the MediaPipe Tasks higher-level API instead, you would:
1. Replace `inference-lm/` with the MediaPipe Tasks dependency
2. Use `LlmInference.create(context, options)` with a `.task` file
3. Call `llmInference.generateResponseAsync(prompt, callback)` for streaming

See [MediaPipe LLM Inference documentation](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
for the MediaPipe path.

---

## Which Format for Which Use Case

| Task | Format | Module | Notes |
|---|---|---|---|
| Image classification | `.tflite` | `inference/` | MobileNetV3, EfficientNet |
| Object detection | `.tflite` | `inference/` | EfficientDet, MobileNet-SSD |
| Pose estimation | `.tflite` | `inference/` | MoveNet, BlazePose |
| Face detection | `.tflite` | `inference/` | BlazeFace |
| Text embedding | `.tflite` | `inference/` | BERT, MiniLM |
| Text classification | `.tflite` | `inference/` | BERT, MobileBERT |
| Chat / Q&A | `.litertlm` | `inference-lm/` | Gemma, Llama |
| Summarization | `.litertlm` | `inference-lm/` | Gemma, Phi |
| Code completion | `.litertlm` | `inference-lm/` | Code Gemma |
| Audio classification | `.tflite` | `inference/` | YAMNet, AudioSet |
| Custom model | `.tflite` | `inference/` | Any model you train |

---

## Official References

- [LiteRT model documentation](https://ai.google.dev/edge/litert/models)
- [LiteRT-LM models page](https://ai.google.dev/edge/litert/inference/litert-lm#models)
- [litert-community on HuggingFace](https://huggingface.co/litert-community)
- [TensorFlow Hub — TFLite models](https://tfhub.dev/s?deployment-format=lite)
- [Qualcomm AI Hub](https://aihub.qualcomm.com) — pre-optimized Snapdragon models
- [MediaPipe LLM Inference (for .task format)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
