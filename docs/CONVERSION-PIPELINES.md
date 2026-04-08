# Model Conversion Pipelines

> Last verified: April 2026
> Sources: [ai.google.dev/edge/litert](https://ai.google.dev/edge/litert), [PyPI litert-torch](https://pypi.org/project/litert-torch/), [google-ai-edge/LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)

This document covers every realistic path for converting models into LiteRT-compatible formats (.tflite and .litertlm) for on-device Android inference.

---

## Quick Reference

| Source Format | Target | Status | Pipeline |
|---|---|---|---|
| HuggingFace safetensors (LLM) | .litertlm | **Working** | litert-torch Generative API |
| HuggingFace safetensors (vision/audio) | .tflite | **Working** | litert-torch |
| TF SavedModel / Keras | .tflite | **Working** | tf.lite.TFLiteConverter |
| PyTorch (.pt / .pth) | .tflite | **Working** | litert-torch |
| JAX | .tflite | **Working** | jax2tf → TFLiteConverter |
| ONNX | .tflite | **Experimental** | onnx2tf (community) |
| GGUF | .litertlm | **Experimental** | Dequantize → safetensors → litert-torch |
| Pre-built .litertlm | Ready to use | **Working** | Download from HuggingFace litert-community |

---

## Working Pipelines

### 1. HuggingFace → LiteRT (Primary Path)

This is the officially supported, recommended path for most models.

**Tool:** `litert-torch` (formerly `ai-edge-torch`, renamed January 2026)
**PyPI:** `pip install litert-torch` (v0.8.0+)
**Docs:** [ai.google.dev/edge/litert/conversion/pytorch](https://ai.google.dev/edge/litert/conversion/pytorch)

#### For Classical Models (vision, audio, embeddings)

```bash
pip install litert-torch==0.8.0
```

```python
import torch
import ai_edge_torch  # litert-torch still uses this import name

# Load your PyTorch model
model = YourModel().eval()
sample_input = (torch.randn(1, 3, 224, 224),)

# Convert
edge_model = ai_edge_torch.convert(model, sample_input)

# Test inference in Python
output = edge_model(*sample_input)

# Export to .tflite
edge_model.export("model.tflite")
```

#### For LLMs (Generative Models)

Uses the Torch Generative API — a specialized path for transformer-based models.

**Docs:** [ai.google.dev/edge/litert/conversion/pytorch/genai](https://ai.google.dev/edge/litert/conversion/pytorch/genai)

Supported architectures (verified April 2026):
- Gemma (1B, 2B, 4B, 7B variants)
- Gemma 3, Gemma 3n, Gemma 4
- LLaMA 2, LLaMA 3, LLaMA 3.1
- Phi-4, Phi-4-mini
- Qwen 2.5
- SmolLM, TinyLlama
- DeepSeek-R1 distill variants

```python
import ai_edge_torch.generative.utilities.loader as loading_utils
from ai_edge_torch.generative.layers import model_config as cfg
# ... (model-specific re-authoring code)

# Multi-signature export (prefill + decode)
edge_model = (
    ai_edge_torch.signature("prefill", model, (prefill_tokens, prefill_input_pos))
    .signature("decode", model, (decode_token, decode_input_pos))
    .convert(quant_config=quant_config)
)
edge_model.export("model.tflite")
```

Then bundle into .litertlm:
```python
from mediapipe.tasks.python.genai import bundler

config = bundler.BundleConfig(
    tflite_model="model.tflite",
    tokenizer_model="tokenizer.model",
    start_token="<s>",
    stop_tokens=["</s>"],
    output_filename="model.litertlm",
    enable_bytes_to_unicode_mapping=False,
)
bundler.create_bundle(config)
```

**Quantization options during conversion:**
- `none` — full precision (FP32)
- `fp16` — half precision
- `dynamic_int8` — dynamic range INT8
- `weight_only_int8` — weights quantized, activations FP32

For INT4 quantization, use `ai-edge-quantizer` as a post-conversion step:
```bash
pip install ai-edge-quantizer
```

**System requirements:** Linux, Python 3.10+, 64 GB RAM for 7B+ models, 32 GB for smaller.

#### Pre-built Models (Skip Conversion)

The fastest path. Download ready-to-use .litertlm files:

- **HuggingFace litert-community:** [huggingface.co/litert-community](https://huggingface.co/litert-community)
  - 85+ models as of April 2026
  - Gemma 4 E2B/E4B, Gemma 3 1B/4B, Phi-4-mini, Qwen 2.5, DeepSeek-R1 distill, SmolVLM
  - All in .litertlm format, ready for LiteRT-LM

- **Kaggle:** [kaggle.com/models](https://www.kaggle.com/models?tfhub-redirect=true)
  - 64 model families, 599 variations in .tflite format
  - Dominated by Gemma variants and classical vision models

- **Google AI Edge Gallery app:** Test models on-device before custom deployment

### 2. TensorFlow SavedModel / Keras → LiteRT

The original, most mature conversion path. Unchanged by the LiteRT rebranding.

**Docs:** [ai.google.dev/edge/litert/conversion/tensorflow/overview](https://ai.google.dev/edge/litert/conversion/tensorflow/overview)

```python
import tensorflow as tf

# From SavedModel
converter = tf.lite.TFLiteConverter.from_saved_model("saved_model_dir")

# Or from Keras
converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)

# Optional: quantization
converter.optimizations = [tf.lite.Optimize.DEFAULT]  # Dynamic range INT8

# Convert
tflite_model = converter.convert()

with open("model.tflite", "wb") as f:
    f.write(tflite_model)
```

**Quantization options:**
| Method | Config | Use Case |
|---|---|---|
| Dynamic range | `optimizations = [DEFAULT]` | General purpose, easy |
| Full integer (INT8) | `optimizations + representative_dataset` | Max performance, needs calibration data |
| Float16 | `target_spec.supported_types = [tf.float16]` | Good GPU performance |
| INT16 activations | `target_spec.supported_ops = [INT16_WITH_INT8_WEIGHTS]` | Experimental, higher accuracy than INT8 |

### 3. JAX → LiteRT

Via the jax2tf bridge, then standard TFLiteConverter.

```python
import jax
import jax.numpy as jnp
from jax.experimental import jax2tf
import tensorflow as tf

# Convert JAX function to TF
tf_fn = jax2tf.convert(jax_fn)
tf_concrete = tf.function(tf_fn, input_signature=[...]).get_concrete_function()

converter = tf.lite.TFLiteConverter.from_concrete_functions([tf_concrete])
tflite_model = converter.convert()
```

---

## Experimental Pipelines

### 4. GGUF → LiteRT (Lossy, Community Hack)

**Status: No direct path exists. Workaround is lossy and limited.**

GGUF files use GGML quantization formats (Q4_K_M, Q5_K_S, IQ2_XXS, etc.) that are fundamentally incompatible with LiteRT's FlatBuffers schema. There is no official tool that accepts GGUF input.

#### The Workaround

1. Dequantize GGUF back to FP16/FP32 safetensors
2. Load as HuggingFace model
3. Convert via litert-torch Generative API
4. Re-quantize during LiteRT conversion

```bash
# Step 1: Convert GGUF to safetensors (community tool)
pip install gguf transformers safetensors

python -c "
from gguf import GGUFReader
# ... extraction and conversion logic
# This only works cleanly for F16 and F32 GGUF variants
"

# Step 2-4: Standard litert-torch pipeline from there
```

#### Limitations

- **Only F16/F32 GGUF variants convert cleanly.** Sub-8-bit quantized formats (Q2_K, Q3_K, Q4_K_M, Q5_K_S, IQ1_M, etc.) lose precision irreversibly during dequantization. The round-trip degrades quality.
- **Architecture must be supported** by litert-torch Generative API. If the model arch is not in the supported list, this path dead-ends.
- **No official support.** Google does not document or endorse GGUF conversion. Community scripts may break between versions.
- **Recommended alternative:** Find the same model in HuggingFace safetensors format and convert directly. Most popular models are available in both GGUF and safetensors.

### 5. ONNX → LiteRT (Community Tool)

**Tool:** `onnx2tf` v2.4.0 by PINTO0309
**PyPI:** `pip install onnx2tf`

```bash
onnx2tf -i model.onnx -o output_dir
# Produces model_float32.tflite in output_dir
```

**Limitations:**
- Supports 192 ONNX ops, but not all
- LLM-scale ONNX models (full autoregressive transformers) are not well supported
- The onnx2tf author explicitly recommends litert-torch for new projects
- No quantization during conversion (apply post-conversion via TFLite tools)
- Not suitable for models with dynamic shapes or complex control flow

**When to use:** Legacy ONNX models (vision classifiers, embedders) where you don't have the original PyTorch/TF source. For anything else, prefer litert-torch.

---

## .task File Packaging

Two distinct flavors of .task files exist:

### Classical .task (Vision/Audio/Text Classifiers)

Produced by MediaPipe Model Maker for predefined task types (image classifier, object detector, text classifier, audio classifier, gesture recognizer, etc.).

Contains: .tflite model + metadata (label maps, normalization params, input specs).

**Limitation:** CPU only through MediaPipe Tasks API. No QNN/NPU acceleration via this path.

### LLM .task / .litertlm (Generative AI)

Produced by `mediapipe.tasks.python.genai.bundler`.

Contains: .tflite model(s) + SentencePiece tokenizer + sampling config.

**Note:** `.task` and `.litertlm` are the same format for GenAI models. LiteRT-LM reads both extensions.

**For NPU acceleration:** Use .litertlm with LiteRT-LM Backend.NPU(). The MediaPipe LLM Inference API (.task path) supports CPU and GPU only — not QNN NPU.

---

## Conversion Decision Tree

```
Have a model you want to run on-device?
│
├─ Is it available pre-built on HuggingFace litert-community?
│  └─ YES → Download .litertlm and use directly ✅
│
├─ Is it a HuggingFace model (safetensors)?
│  ├─ LLM / Generative → litert-torch Generative API → .litertlm ✅
│  └─ Vision / Audio → litert-torch → .tflite ✅
│
├─ Is it a TensorFlow SavedModel or Keras?
│  └─ tf.lite.TFLiteConverter → .tflite ✅
│
├─ Is it a PyTorch model (.pt)?
│  └─ litert-torch → .tflite ✅
│
├─ Is it ONNX?
│  └─ onnx2tf → .tflite (experimental, limited) ⚠️
│
├─ Is it GGUF?
│  ├─ F16/F32 variant → dequantize → safetensors → litert-torch ⚠️
│  └─ Quantized (Q4_K_M, etc.) → Find safetensors version instead 🔴
│
└─ Is it something else?
   └─ Convert to PyTorch or TF first, then use paths above
```

---

## Tools Setup

### Install litert-torch (conversion toolkit)

```bash
# Recommended: use a dedicated environment
python -m venv litert-env
source litert-env/bin/activate

pip install litert-torch==0.8.0
pip install torch==2.5.1  # Check litert-torch requirements for compatible version
pip install mediapipe      # For .litertlm bundling
pip install ai-edge-quantizer  # For INT4 quantization
```

### Install onnx2tf (ONNX conversion)

```bash
pip install onnx2tf==2.4.0
pip install onnx==1.16.0
pip install tensorflow==2.17.0  # Check onnx2tf compatibility
```

### Google Colab Option

For users without 64 GB RAM locally, Google Colab Pro/Pro+ provides the memory needed for large model conversion. See `tools/conversion/working/colab_hf_to_litertlm.py` for a ready-to-use notebook script.

---

## What You Still Need to Provide

| Item | Why | Where to Get It |
|---|---|---|
| Model weights | Models are too large for the repo | HuggingFace, Kaggle, or your training pipeline |
| QNN .so files | Qualcomm license, device-specific | QAIRT SDK via QPM or Qualcomm AI Hub |
| Tokenizer file | Model-specific | Included in HuggingFace model repos |
| Calibration data | Required for INT8 quantization | Your domain-specific dataset |
