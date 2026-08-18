import os
import subprocess
import sys

def check_dependencies():
    try:
        import optimum
        import onnxruntime
    except ImportError:
        print("Missing required packages for ONNX LLM Export.")
        print("Please run: pip install optimum[onnxruntime] onnxruntime-genai")
        sys.exit(1)

def export_phi3_to_onnx():
    """
    Exports Microsoft's Phi-3-Mini to an INT4 Quantized ONNX format.
    This replaces the simple cosine-similarity embedding logic with an actual Small Language Model (SLM)
    capable of reading transcripts and reasoning on-device whether it is a scam.
    """
    MODEL_ID = "microsoft/Phi-3-mini-4k-instruct"
    OUTPUT_DIR = "phi3-mini-int4-onnx"
    
    print(f"Starting ONNX INT4 Export for {MODEL_ID}...")
    print("This will download a 3.8B parameter model (~7GB) and quantize it to ~2.5GB.")
    print("Note: This requires a machine with at least 16GB of RAM.")
    
    # We use the optimum-cli via subprocess for the most stable export process
    # The --task text-generation-with-past ensures KV caching is enabled for fast mobile inference
    # --weight-format int4 applies 4-bit quantization
    command = [
        "optimum-cli", "export", "onnx",
        "-m", MODEL_ID,
        "--task", "text-generation-with-past",
        "--weight-format", "int4",
        "--device", "cpu", # Export on CPU to avoid MPS/CUDA memory limits during conversion
        OUTPUT_DIR
    ]
    
    try:
        subprocess.run(command, check=True)
        print(f"\nSuccess! Quantized Phi-3 model saved to: {OUTPUT_DIR}")
        print("You can now integrate this into the Android App using the onnxruntime-genai C++ or Java API.")
    except subprocess.CalledProcessError as e:
        print(f"\nExport failed: {e}")
        print("Ensure you have enough disk space and memory.")

if __name__ == "__main__":
    check_dependencies()
    export_phi3_to_onnx()
