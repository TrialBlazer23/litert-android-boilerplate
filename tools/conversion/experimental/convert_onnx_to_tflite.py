#!/usr/bin/env python3
"""
convert_onnx_to_tflite.py — EXPERIMENTAL: Convert ONNX model to .tflite via onnx2tf.

STATUS: EXPERIMENTAL — Uses community-maintained onnx2tf tool (not official Google).

LIMITATIONS:
- Supports 192 ONNX ops (not all)
- Not suitable for LLM-scale autoregressive transformers
- No quantization during conversion (apply post-conversion)
- Dynamic shapes may not convert cleanly
- The onnx2tf author recommends litert-torch for new projects

WHEN TO USE: Legacy ONNX models (vision classifiers, embedders) where you don't
have the original PyTorch/TensorFlow source code.

Part of LiteRT Android Boilerplate by Necessity Labs.

Usage:
    python convert_onnx_to_tflite.py \
        --onnx-path model.onnx \
        --output model.tflite

Requirements:
    pip install onnx2tf==2.4.0 onnx==1.16.0 tensorflow>=2.17.0
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


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for ONNX conversion."""
    parser = argparse.ArgumentParser(
        description="EXPERIMENTAL: Convert ONNX model to .tflite via onnx2tf.",
    )
    parser.add_argument(
        "--onnx-path",
        type=str,
        required=True,
        help="Path to the input ONNX model file.",
    )
    parser.add_argument(
        "--output",
        type=str,
        required=True,
        help="Output path for the .tflite file.",
    )
    parser.add_argument(
        "--output-signaturedefs",
        action="store_true",
        help="Generate signature definitions in the output TFLite model.",
    )
    return parser.parse_args()


def verify_onnx_model(onnx_path: Path) -> bool:
    """
    Verify the ONNX model is valid and inspect its structure.

    Args:
        onnx_path: Path to the ONNX model file.

    Returns:
        True if the model passes basic validation.
    """
    try:
        import onnx
        model = onnx.load(str(onnx_path))
        onnx.checker.check_model(model)

        graph = model.graph
        logger.info(f"ONNX model: {onnx_path.name}")
        logger.info(f"  IR version: {model.ir_version}")
        logger.info(f"  Opset version: {model.opset_import[0].version}")
        logger.info(f"  Inputs: {len(graph.input)}")
        logger.info(f"  Outputs: {len(graph.output)}")
        logger.info(f"  Nodes: {len(graph.node)}")

        op_types = set(node.op_type for node in graph.node)
        logger.info(f"  Unique op types: {len(op_types)}")

        return True
    except Exception as e:
        logger.error(f"ONNX validation failed: {e}")
        return False


def convert_onnx_to_tflite(
    onnx_path: Path,
    output_path: Path,
    output_signaturedefs: bool,
) -> Path:
    """
    Convert an ONNX model to .tflite using onnx2tf.

    Args:
        onnx_path: Path to the input ONNX model.
        output_path: Destination path for the .tflite file.
        output_signaturedefs: Whether to include signature definitions.

    Returns:
        Path to the generated .tflite file.

    Raises:
        RuntimeError: If conversion fails.
    """
    import onnx2tf

    logger.info("Starting ONNX → TFLite conversion via onnx2tf...")
    logger.warning(
        "This is a community tool. For production models, consider converting "
        "from PyTorch via litert-torch instead."
    )

    start_time = time.monotonic()

    output_dir = output_path.parent / f"{output_path.stem}_onnx2tf"
    output_dir.mkdir(parents=True, exist_ok=True)

    onnx2tf.convert(
        input_onnx_file_path=str(onnx_path),
        output_folder_path=str(output_dir),
        output_signaturedefs=output_signaturedefs,
        copy_onnx_input_output_names_to_tflite=True,
        non_verbose=False,
    )

    tflite_files = list(output_dir.glob("*.tflite"))
    if not tflite_files:
        raise RuntimeError(
            f"onnx2tf did not produce a .tflite file in {output_dir}. "
            "The ONNX model may contain unsupported operations."
        )

    source_tflite = tflite_files[0]
    output_path.parent.mkdir(parents=True, exist_ok=True)

    import shutil
    shutil.copy2(source_tflite, output_path)

    elapsed = time.monotonic() - start_time
    file_size_mb = output_path.stat().st_size / (1024 * 1024)

    logger.info(f"Conversion complete in {elapsed:.1f}s")
    logger.info(f"Output: {output_path} ({file_size_mb:.1f} MB)")

    return output_path


def main() -> None:
    """Entry point for the ONNX-to-TFLite conversion script."""
    args = parse_args()
    onnx_path = Path(args.onnx_path)
    output_path = Path(args.output)

    if not onnx_path.exists():
        logger.error(f"ONNX file not found: {onnx_path}")
        sys.exit(1)

    if not verify_onnx_model(onnx_path):
        logger.error("ONNX model validation failed. Aborting.")
        sys.exit(1)

    try:
        convert_onnx_to_tflite(
            onnx_path=onnx_path,
            output_path=output_path,
            output_signaturedefs=args.output_signaturedefs,
        )
    except Exception as e:
        logger.error(f"Conversion failed: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Done. Push to device: adb push <model>.tflite /data/local/tmp/models/")


if __name__ == "__main__":
    main()
