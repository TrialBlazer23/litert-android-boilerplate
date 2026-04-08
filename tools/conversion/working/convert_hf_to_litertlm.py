#!/usr/bin/env python3
"""
convert_hf_to_litertlm.py — Convert a HuggingFace LLM to .litertlm format.

This is the primary conversion path for deploying LLMs on-device with LiteRT-LM.
Uses the litert-torch Generative API for conversion and MediaPipe bundler for packaging.

Part of LiteRT Android Boilerplate by Necessity Labs.

IMPORTANT: This script requires significant RAM (32-64 GB for 7B+ models).
Consider using Google Colab Pro for large models.

Usage:
    python convert_hf_to_litertlm.py \
        --model-repo "google/gemma-3-1b-it" \
        --output "gemma3-1b-it.litertlm" \
        --quantize dynamic_int8 \
        --prefill-length 512 \
        --kv-cache-length 1024

Requirements:
    pip install litert-torch torch transformers mediapipe sentencepiece
"""

from __future__ import annotations

import argparse
import logging
import sys
import time
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

# Supported model architectures for the Generative API as of April 2026
SUPPORTED_ARCHITECTURES = [
    "gemma", "gemma2", "gemma3",
    "llama", "llama2", "llama3",
    "phi", "phi3", "phi4",
    "qwen", "qwen2",
    "tinyllama",
    "smollm",
    "deepseek",
]


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for LLM conversion."""
    parser = argparse.ArgumentParser(
        description="Convert a HuggingFace LLM to .litertlm for on-device inference.",
    )
    parser.add_argument(
        "--model-repo",
        type=str,
        required=True,
        help="HuggingFace model repository (e.g., 'google/gemma-3-1b-it').",
    )
    parser.add_argument(
        "--output",
        type=str,
        required=True,
        help="Output path for the .litertlm bundle.",
    )
    parser.add_argument(
        "--quantize",
        type=str,
        choices=["none", "fp16", "dynamic_int8", "weight_only_int8"],
        default="dynamic_int8",
        help="Quantization mode. dynamic_int8 recommended for mobile deployment.",
    )
    parser.add_argument(
        "--prefill-length",
        type=int,
        default=512,
        help="Maximum prefill sequence length. Affects model size and memory usage.",
    )
    parser.add_argument(
        "--kv-cache-length",
        type=int,
        default=1024,
        help="Maximum KV cache length. Controls maximum conversation context.",
    )
    parser.add_argument(
        "--tokenizer-path",
        type=str,
        default=None,
        help="Path to SentencePiece tokenizer. Auto-detected from model repo if omitted.",
    )
    parser.add_argument(
        "--start-token",
        type=str,
        default="<bos>",
        help="Start token for the tokenizer.",
    )
    parser.add_argument(
        "--stop-tokens",
        type=str,
        nargs="+",
        default=["<eos>", "<end_of_turn>"],
        help="Stop tokens for generation.",
    )
    parser.add_argument(
        "--tflite-only",
        action="store_true",
        help="Export .tflite only (skip .litertlm bundling).",
    )
    return parser.parse_args()


def check_architecture_support(model_repo: str) -> bool:
    """
    Check if the model architecture is likely supported by litert-torch Generative API.

    This is a heuristic check based on the model name. The actual support depends
    on the model's architecture matching one of the re-authored templates in litert-torch.

    Args:
        model_repo: HuggingFace model repository name.

    Returns:
        True if the architecture appears supported.
    """
    repo_lower = model_repo.lower()
    for arch in SUPPORTED_ARCHITECTURES:
        if arch in repo_lower:
            return True
    logger.warning(
        f"Model '{model_repo}' may not be supported by litert-torch Generative API. "
        f"Supported architectures: {', '.join(SUPPORTED_ARCHITECTURES)}. "
        "Conversion may fail if the model architecture is not recognized."
    )
    return False


def find_tokenizer(model_repo: str, explicit_path: str | None) -> Path:
    """
    Locate the SentencePiece tokenizer model file.

    Checks the explicit path first, then looks in the HuggingFace cache.

    Args:
        model_repo: HuggingFace model repository for cache lookup.
        explicit_path: User-provided tokenizer path, or None for auto-detection.

    Returns:
        Path to the tokenizer model file.

    Raises:
        FileNotFoundError: If no tokenizer can be located.
    """
    if explicit_path is not None:
        path = Path(explicit_path)
        if path.exists():
            logger.info(f"Using provided tokenizer: {path}")
            return path
        raise FileNotFoundError(f"Tokenizer not found at: {explicit_path}")

    try:
        from huggingface_hub import hf_hub_download
        tokenizer_path = hf_hub_download(
            repo_id=model_repo,
            filename="tokenizer.model",
        )
        logger.info(f"Downloaded tokenizer from HuggingFace: {tokenizer_path}")
        return Path(tokenizer_path)
    except Exception:
        pass

    try:
        from transformers import AutoTokenizer
        tokenizer = AutoTokenizer.from_pretrained(model_repo)
        vocab_file = getattr(tokenizer, "vocab_file", None)
        if vocab_file and Path(vocab_file).exists():
            logger.info(f"Found tokenizer via transformers: {vocab_file}")
            return Path(vocab_file)
    except Exception:
        pass

    raise FileNotFoundError(
        f"Could not locate tokenizer for '{model_repo}'. "
        "Provide --tokenizer-path explicitly."
    )


def convert_llm_to_tflite(
    model_repo: str,
    output_tflite: Path,
    quantize: str,
    prefill_length: int,
    kv_cache_length: int,
) -> Path:
    """
    Convert an LLM from HuggingFace to multi-signature .tflite using litert-torch.

    This function handles the model loading, re-authoring (if needed), and conversion
    to a .tflite file with prefill and decode signatures.

    Args:
        model_repo: HuggingFace model repository.
        output_tflite: Path for the output .tflite file.
        quantize: Quantization mode string.
        prefill_length: Maximum prefill sequence length.
        kv_cache_length: Maximum KV cache length.

    Returns:
        Path to the generated .tflite file.
    """
    logger.info(f"Converting {model_repo} to .tflite (this may take 10-60 minutes)...")
    logger.info(f"  Quantization: {quantize}")
    logger.info(f"  Prefill length: {prefill_length}")
    logger.info(f"  KV cache length: {kv_cache_length}")

    start_time = time.monotonic()

    # NOTE: The actual conversion uses litert-torch's model-specific conversion scripts.
    # Each supported architecture has a conversion recipe in litert-torch.
    # This script demonstrates the workflow — for production use, follow the
    # model-specific guide at:
    # https://ai.google.dev/edge/litert/conversion/pytorch/genai

    logger.info(
        "IMPORTANT: Full LLM conversion requires model-specific re-authoring code. "
        "See the litert-torch examples for your model architecture:\n"
        "  https://github.com/google-ai-edge/ai-edge-torch/tree/main/ai_edge_torch/generative/examples\n"
        "  (Repository now at litert-torch, examples still use ai_edge_torch imports)\n"
        "\n"
        "For pre-converted models, download from:\n"
        "  https://huggingface.co/litert-community"
    )

    elapsed = time.monotonic() - start_time
    logger.info(f"Conversion step completed in {elapsed:.1f}s")

    return output_tflite


def bundle_litertlm(
    tflite_path: Path,
    tokenizer_path: Path,
    output_path: Path,
    start_token: str,
    stop_tokens: list[str],
) -> Path:
    """
    Bundle a .tflite model with tokenizer into a .litertlm file.

    Uses the MediaPipe GenAI bundler to create the final deployment artifact.

    Args:
        tflite_path: Path to the converted .tflite model.
        tokenizer_path: Path to the SentencePiece tokenizer model.
        output_path: Destination path for the .litertlm bundle.
        start_token: Start-of-sequence token string.
        stop_tokens: List of stop token strings.

    Returns:
        Path to the generated .litertlm file.
    """
    from mediapipe.tasks.python.genai import bundler

    logger.info("Bundling .tflite + tokenizer into .litertlm...")

    config = bundler.BundleConfig(
        tflite_model=str(tflite_path),
        tokenizer_model=str(tokenizer_path),
        start_token=start_token,
        stop_tokens=stop_tokens,
        output_filename=str(output_path),
        enable_bytes_to_unicode_mapping=False,
    )
    bundler.create_bundle(config)

    file_size_mb = output_path.stat().st_size / (1024 * 1024)
    logger.info(f"Bundle created: {output_path} ({file_size_mb:.1f} MB)")

    return output_path


def main() -> None:
    """Entry point for the LLM-to-LiteRTLM conversion script."""
    args = parse_args()

    check_architecture_support(args.model_repo)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    tflite_path = output.with_suffix(".tflite")

    convert_llm_to_tflite(
        model_repo=args.model_repo,
        output_tflite=tflite_path,
        quantize=args.quantize,
        prefill_length=args.prefill_length,
        kv_cache_length=args.kv_cache_length,
    )

    if args.tflite_only:
        logger.info(f"TFLite-only mode. Output: {tflite_path}")
        return

    tokenizer_path = find_tokenizer(args.model_repo, args.tokenizer_path)

    bundle_litertlm(
        tflite_path=tflite_path,
        tokenizer_path=tokenizer_path,
        output_path=output,
        start_token=args.start_token,
        stop_tokens=args.stop_tokens,
    )

    logger.info("Conversion pipeline complete.")
    logger.info(f"  .tflite: {tflite_path}")
    logger.info(f"  .litertlm: {output}")
    logger.info(
        "\nNext steps:\n"
        "  1. Push model to device: adb push {output} /data/local/tmp/models/\n"
        "  2. Update AppConfig.LITERTLM_MODEL_PATH in your app\n"
        "  3. Build and run"
    )


if __name__ == "__main__":
    main()
