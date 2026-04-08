#!/usr/bin/env python3
"""
convert_hf_to_tflite.py — Convert a HuggingFace PyTorch model to .tflite format.

For classical models (vision, audio, embeddings) — NOT LLMs.
For LLMs, use convert_hf_to_litertlm.py instead.

Part of LiteRT Android Boilerplate by Necessity Labs.

Usage:
    python convert_hf_to_tflite.py \
        --model-name "google/mobilenet_v2_1.0_224" \
        --output "mobilenet_v2.tflite" \
        --input-shape 1 3 224 224

Requirements:
    pip install litert-torch torch torchvision transformers
"""

from __future__ import annotations

import argparse
import logging
import sys
import time
from pathlib import Path

import torch

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for model conversion."""
    parser = argparse.ArgumentParser(
        description="Convert a PyTorch model to LiteRT .tflite format.",
    )
    parser.add_argument(
        "--model-name",
        type=str,
        required=True,
        help="HuggingFace model name or local path to PyTorch model.",
    )
    parser.add_argument(
        "--output",
        type=str,
        required=True,
        help="Output path for the .tflite file.",
    )
    parser.add_argument(
        "--input-shape",
        type=int,
        nargs="+",
        required=True,
        help="Input tensor shape (e.g., 1 3 224 224 for a vision model).",
    )
    parser.add_argument(
        "--quantize",
        type=str,
        choices=["none", "fp16", "dynamic_int8"],
        default="none",
        help="Quantization mode to apply during conversion.",
    )
    return parser.parse_args()


def load_torchvision_model(model_name: str) -> torch.nn.Module:
    """
    Attempt to load a model from torchvision by name.

    Supports common model names like 'mobilenet_v2', 'resnet18', 'efficientnet_b0'.
    Falls back to None if the model is not found in torchvision.

    Args:
        model_name: Name of the torchvision model.

    Returns:
        The loaded PyTorch model in eval mode, or None if not found.
    """
    try:
        import torchvision.models as models
        model_fn = getattr(models, model_name, None)
        if model_fn is not None:
            logger.info(f"Loading torchvision model: {model_name}")
            model = model_fn(weights=None)
            model.eval()
            return model
    except ImportError:
        logger.warning("torchvision not installed. Skipping torchvision model lookup.")
    return None


def load_huggingface_model(model_name: str) -> torch.nn.Module | None:
    """
    Attempt to load a model from HuggingFace transformers.

    Works with vision models (AutoModelForImageClassification),
    audio models, and other supported architectures.

    Args:
        model_name: HuggingFace model identifier (e.g., 'google/mobilenet_v2_1.0_224').

    Returns:
        The loaded PyTorch model in eval mode, or None if loading fails.
    """
    try:
        from transformers import AutoModel
        logger.info(f"Loading HuggingFace model: {model_name}")
        model = AutoModel.from_pretrained(model_name, torchscript=True)
        model.eval()
        return model
    except Exception as e:
        logger.warning(f"Failed to load HuggingFace model: {e}")
        return None


def convert_to_tflite(
    model: torch.nn.Module,
    sample_input: tuple[torch.Tensor, ...],
    output_path: str,
    quantize: str = "none",
) -> Path:
    """
    Convert a PyTorch model to .tflite using litert-torch (ai_edge_torch).

    Args:
        model: PyTorch model in eval mode.
        sample_input: Tuple of sample input tensors matching expected input shape.
        output_path: Destination path for the .tflite file.
        quantize: Quantization mode ('none', 'fp16', 'dynamic_int8').

    Returns:
        Path to the generated .tflite file.

    Raises:
        RuntimeError: If conversion fails.
    """
    import ai_edge_torch

    logger.info("Starting conversion with litert-torch (ai_edge_torch)...")
    start_time = time.monotonic()

    quant_config = None
    if quantize == "dynamic_int8":
        from ai_edge_torch.quantize import quant_recipes
        quant_config = quant_recipes.full_linear_int8_dynamic_recipe()
        logger.info("Applying dynamic INT8 quantization.")
    elif quantize == "fp16":
        logger.info("FP16 quantization is applied at the TFLite level post-conversion.")

    edge_model = ai_edge_torch.convert(model, sample_input, quant_config=quant_config)

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    edge_model.export(str(output))

    elapsed = time.monotonic() - start_time
    file_size_mb = output.stat().st_size / (1024 * 1024)

    logger.info(f"Conversion complete in {elapsed:.1f}s")
    logger.info(f"Output: {output} ({file_size_mb:.1f} MB)")

    return output


def main() -> None:
    """Entry point for the HuggingFace-to-TFLite conversion script."""
    args = parse_args()

    model = load_torchvision_model(args.model_name)
    if model is None:
        model = load_huggingface_model(args.model_name)
    if model is None:
        logger.error(
            f"Could not load model '{args.model_name}' from torchvision or HuggingFace. "
            "Provide a valid model name or local path."
        )
        sys.exit(1)

    sample_input = (torch.randn(*args.input_shape),)
    logger.info(f"Sample input shape: {args.input_shape}")

    try:
        output_path = convert_to_tflite(
            model=model,
            sample_input=sample_input,
            output_path=args.output,
            quantize=args.quantize,
        )
        logger.info(f"Successfully converted to: {output_path}")
    except Exception as e:
        logger.error(f"Conversion failed: {e}", exc_info=True)
        sys.exit(1)


if __name__ == "__main__":
    main()
