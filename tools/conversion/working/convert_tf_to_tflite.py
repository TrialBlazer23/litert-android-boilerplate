#!/usr/bin/env python3
"""
convert_tf_to_tflite.py — Convert TensorFlow SavedModel or Keras model to .tflite.

The most mature and stable conversion path. Unchanged by the LiteRT rebranding.

Part of LiteRT Android Boilerplate by Necessity Labs.

Usage:
    # From SavedModel directory
    python convert_tf_to_tflite.py \
        --saved-model saved_model_dir/ \
        --output model.tflite \
        --quantize dynamic_int8

    # From Keras .h5 file
    python convert_tf_to_tflite.py \
        --keras-model model.h5 \
        --output model.tflite

Requirements:
    pip install tensorflow>=2.17.0
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
    """Parse command-line arguments for TF-to-TFLite conversion."""
    parser = argparse.ArgumentParser(
        description="Convert TensorFlow SavedModel or Keras model to .tflite format.",
    )
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument(
        "--saved-model",
        type=str,
        help="Path to TensorFlow SavedModel directory.",
    )
    source_group.add_argument(
        "--keras-model",
        type=str,
        help="Path to Keras .h5 or SavedModel file.",
    )
    parser.add_argument(
        "--output",
        type=str,
        required=True,
        help="Output path for the .tflite file.",
    )
    parser.add_argument(
        "--quantize",
        type=str,
        choices=["none", "dynamic_int8", "float16", "full_int8"],
        default="none",
        help="Quantization method to apply.",
    )
    parser.add_argument(
        "--representative-dataset-size",
        type=int,
        default=100,
        help="Number of samples for full INT8 calibration (only used with full_int8).",
    )
    return parser.parse_args()


def apply_quantization(
    converter: "tf.lite.TFLiteConverter",
    quantize: str,
    representative_dataset_size: int,
) -> None:
    """
    Configure quantization settings on the TFLite converter.

    Args:
        converter: The TFLiteConverter instance to configure.
        quantize: Quantization mode string.
        representative_dataset_size: Number of calibration samples for full INT8.
    """
    import tensorflow as tf
    import numpy as np

    if quantize == "none":
        logger.info("No quantization applied (FP32 output).")
        return

    if quantize == "dynamic_int8":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        logger.info("Dynamic range INT8 quantization enabled.")

    elif quantize == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
        logger.info("Float16 quantization enabled.")

    elif quantize == "full_int8":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]

        def representative_dataset():
            """Generate random calibration data. Replace with real data for production."""
            for _ in range(representative_dataset_size):
                data = np.random.rand(1, 224, 224, 3).astype(np.float32)
                yield [data]

        converter.representative_dataset = representative_dataset
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
        ]
        converter.inference_input_type = tf.uint8
        converter.inference_output_type = tf.uint8
        logger.info(
            f"Full INT8 quantization enabled with {representative_dataset_size} "
            "calibration samples. Replace representative_dataset with real data "
            "for production quality."
        )


def convert_saved_model(
    saved_model_dir: str,
    output_path: str,
    quantize: str,
    representative_dataset_size: int,
) -> Path:
    """
    Convert a TensorFlow SavedModel to .tflite.

    Args:
        saved_model_dir: Path to the SavedModel directory.
        output_path: Destination path for the .tflite file.
        quantize: Quantization mode.
        representative_dataset_size: Calibration samples for full INT8.

    Returns:
        Path to the generated .tflite file.
    """
    import tensorflow as tf

    logger.info(f"Loading SavedModel from: {saved_model_dir}")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    apply_quantization(converter, quantize, representative_dataset_size)

    logger.info("Converting...")
    start_time = time.monotonic()
    tflite_model = converter.convert()
    elapsed = time.monotonic() - start_time

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(tflite_model)

    file_size_mb = output.stat().st_size / (1024 * 1024)
    logger.info(f"Conversion complete in {elapsed:.1f}s")
    logger.info(f"Output: {output} ({file_size_mb:.1f} MB)")

    return output


def convert_keras_model(
    keras_model_path: str,
    output_path: str,
    quantize: str,
    representative_dataset_size: int,
) -> Path:
    """
    Convert a Keras model to .tflite.

    Args:
        keras_model_path: Path to the .h5 or SavedModel Keras file.
        output_path: Destination path for the .tflite file.
        quantize: Quantization mode.
        representative_dataset_size: Calibration samples for full INT8.

    Returns:
        Path to the generated .tflite file.
    """
    import tensorflow as tf

    logger.info(f"Loading Keras model from: {keras_model_path}")
    model = tf.keras.models.load_model(keras_model_path)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    apply_quantization(converter, quantize, representative_dataset_size)

    logger.info("Converting...")
    start_time = time.monotonic()
    tflite_model = converter.convert()
    elapsed = time.monotonic() - start_time

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(tflite_model)

    file_size_mb = output.stat().st_size / (1024 * 1024)
    logger.info(f"Conversion complete in {elapsed:.1f}s")
    logger.info(f"Output: {output} ({file_size_mb:.1f} MB)")

    return output


def main() -> None:
    """Entry point for the TF-to-TFLite conversion script."""
    args = parse_args()

    try:
        if args.saved_model:
            convert_saved_model(
                saved_model_dir=args.saved_model,
                output_path=args.output,
                quantize=args.quantize,
                representative_dataset_size=args.representative_dataset_size,
            )
        elif args.keras_model:
            convert_keras_model(
                keras_model_path=args.keras_model,
                output_path=args.output,
                quantize=args.quantize,
                representative_dataset_size=args.representative_dataset_size,
            )
    except Exception as e:
        logger.error(f"Conversion failed: {e}", exc_info=True)
        sys.exit(1)

    logger.info("Done. Push to device: adb push <model>.tflite /data/local/tmp/models/")


if __name__ == "__main__":
    main()
