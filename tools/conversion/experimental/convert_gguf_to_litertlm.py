#!/usr/bin/env python3
"""
convert_gguf_to_litertlm.py — EXPERIMENTAL: Convert GGUF model to .litertlm via dequantization.

STATUS: EXPERIMENTAL — This is a community workaround, NOT an officially supported pipeline.

WARNING: This path is LOSSY for sub-8-bit GGUF quantization formats. The round-trip
(GGUF → dequantize → safetensors → litert-torch → .litertlm) degrades model quality
for Q2_K, Q3_K, Q4_K_M, Q5_K_S, and similar GGML quant formats.

RECOMMENDED ALTERNATIVE: Find the same model in HuggingFace safetensors format and
convert directly using tools/conversion/working/convert_hf_to_litertlm.py instead.

Part of LiteRT Android Boilerplate by Necessity Labs.

Usage:
    python convert_gguf_to_litertlm.py \
        --gguf-path model-q4_k_m.gguf \
        --output-dir ./converted/ \
        --model-arch llama

Requirements:
    pip install gguf transformers safetensors torch
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

# GGUF quantization types and their dequantization quality
GGUF_QUANT_QUALITY = {
    "F32": "lossless",
    "F16": "lossless",
    "BF16": "lossless",
    "Q8_0": "near-lossless",
    "Q8_1": "near-lossless",
    "Q6_K": "minor-loss",
    "Q5_K_M": "moderate-loss",
    "Q5_K_S": "moderate-loss",
    "Q5_0": "moderate-loss",
    "Q5_1": "moderate-loss",
    "Q4_K_M": "significant-loss",
    "Q4_K_S": "significant-loss",
    "Q4_0": "significant-loss",
    "Q4_1": "significant-loss",
    "Q3_K_M": "severe-loss",
    "Q3_K_S": "severe-loss",
    "Q3_K_L": "severe-loss",
    "Q2_K": "severe-loss",
    "IQ4_XS": "significant-loss",
    "IQ3_XXS": "severe-loss",
    "IQ2_XXS": "severe-loss",
    "IQ1_M": "extreme-loss",
    "IQ1_S": "extreme-loss",
}


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for GGUF conversion."""
    parser = argparse.ArgumentParser(
        description="EXPERIMENTAL: Convert GGUF to LiteRT format via dequantization.",
    )
    parser.add_argument(
        "--gguf-path",
        type=str,
        required=True,
        help="Path to the input GGUF model file.",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        required=True,
        help="Directory for intermediate and final output files.",
    )
    parser.add_argument(
        "--model-arch",
        type=str,
        required=True,
        choices=["llama", "gemma", "phi", "qwen", "mistral"],
        help="Model architecture family for litert-torch conversion.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Proceed even with severe quality loss warning.",
    )
    return parser.parse_args()


def detect_gguf_quant_type(gguf_path: Path) -> str | None:
    """
    Detect the quantization type of a GGUF file from its filename or metadata.

    Most GGUF files include the quant type in the filename (e.g., model-q4_k_m.gguf).
    Falls back to reading file metadata if the filename is ambiguous.

    Args:
        gguf_path: Path to the GGUF file.

    Returns:
        Detected quantization type string, or None if undetectable.
    """
    filename = gguf_path.stem.upper()

    for quant_type in GGUF_QUANT_QUALITY:
        normalized = quant_type.replace("_", "").upper()
        if normalized in filename.replace("-", "").replace("_", ""):
            return quant_type

    try:
        from gguf import GGUFReader
        reader = GGUFReader(str(gguf_path))
        for tensor in reader.tensors:
            return str(tensor.tensor_type.name) if hasattr(tensor.tensor_type, "name") else None
    except Exception as e:
        logger.warning(f"Could not read GGUF metadata: {e}")

    return None


def assess_quality_risk(quant_type: str | None) -> str:
    """
    Assess the quality risk of dequantizing a GGUF file.

    Args:
        quant_type: The detected GGUF quantization type.

    Returns:
        Risk level string: 'lossless', 'near-lossless', 'minor-loss',
        'moderate-loss', 'significant-loss', 'severe-loss', 'extreme-loss', or 'unknown'.
    """
    if quant_type is None:
        return "unknown"
    return GGUF_QUANT_QUALITY.get(quant_type, "unknown")


def main() -> None:
    """Entry point for the experimental GGUF conversion pipeline."""
    args = parse_args()
    gguf_path = Path(args.gguf_path)
    output_dir = Path(args.output_dir)

    if not gguf_path.exists():
        logger.error(f"GGUF file not found: {gguf_path}")
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)

    logger.warning("=" * 70)
    logger.warning("EXPERIMENTAL PIPELINE — Quality may be degraded")
    logger.warning("Consider using the HuggingFace safetensors path instead:")
    logger.warning("  python tools/conversion/working/convert_hf_to_litertlm.py")
    logger.warning("=" * 70)

    quant_type = detect_gguf_quant_type(gguf_path)
    risk = assess_quality_risk(quant_type)

    logger.info(f"GGUF file: {gguf_path}")
    logger.info(f"Detected quantization: {quant_type or 'unknown'}")
    logger.info(f"Dequantization quality risk: {risk}")

    if risk in ("severe-loss", "extreme-loss") and not args.force:
        logger.error(
            f"Quantization type '{quant_type}' will produce severely degraded output "
            "after dequantization. The model quality will NOT match the original GGUF.\n"
            "\n"
            "Options:\n"
            "  1. Find the same model in safetensors format on HuggingFace (recommended)\n"
            "  2. Find a higher-precision GGUF variant (F16 or Q8_0)\n"
            "  3. Pass --force to proceed anyway (not recommended)\n"
        )
        sys.exit(1)

    if risk in ("significant-loss",) and not args.force:
        logger.warning(
            f"Quantization type '{quant_type}' will lose precision during dequantization. "
            "Output quality will be lower than the original GGUF. "
            "Pass --force to proceed, or find a safetensors source."
        )
        sys.exit(1)

    logger.info(
        "\nTo complete this pipeline manually:\n"
        "\n"
        "Step 1 — Dequantize GGUF to safetensors:\n"
        "  pip install gguf transformers safetensors\n"
        "  python -c \"\n"
        "  from gguf import GGUFReader\n"
        "  # Extract tensors and save as safetensors\n"
        "  # See: https://github.com/ggerganov/llama.cpp/discussions/2948\n"
        "  \"\n"
        "\n"
        "Step 2 — Load as HuggingFace model:\n"
        "  from transformers import AutoModelForCausalLM\n"
        f"  model = AutoModelForCausalLM.from_pretrained('{output_dir}/safetensors/')\n"
        "\n"
        "Step 3 — Convert via litert-torch Generative API:\n"
        "  (Follow the standard HuggingFace → .litertlm pipeline)\n"
        "\n"
        "Step 4 — Bundle as .litertlm:\n"
        "  (Use mediapipe.tasks.python.genai.bundler)\n"
        "\n"
        "Each step has potential failure points. This pipeline is inherently fragile.\n"
        "We strongly recommend using pre-built .litertlm models from:\n"
        "  https://huggingface.co/litert-community\n"
    )


if __name__ == "__main__":
    main()
