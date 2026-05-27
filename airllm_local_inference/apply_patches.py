"""
AirLLM Compatibility Patch for transformers 4.57+
===================================================
Run this script after installing/updating AirLLM to apply required patches
for modern transformers compatibility.

Usage:
    python apply_patches.py
"""

import sys
import os
from pathlib import Path


def get_airllm_path():
    """Find the AirLLM package directory."""
    import airllm
    return Path(airllm.__file__).parent


def patch_file(filepath, old_text, new_text, description):
    """Apply a single patch to a file."""
    content = filepath.read_text(encoding="utf-8")
    if new_text in content:
        print(f"  [SKIP] {description} (already applied)")
        return True
    if old_text not in content:
        print(f"  [WARN] {description} (target text not found - may need manual fix)")
        return False
    content = content.replace(old_text, new_text, 1)
    filepath.write_text(content, encoding="utf-8")
    print(f"  [OK]   {description}")
    return True


def apply_patches():
    """Apply all compatibility patches."""
    airllm_dir = get_airllm_path()
    print(f"AirLLM directory: {airllm_dir}")
    print(f"Applying patches for transformers 4.57+ compatibility...\n")

    base_file = airllm_dir / "airllm_base.py"
    utils_file = airllm_dir / "utils.py"
    qwen2_file = airllm_dir / "airllm_qwen2.py"

    success = True

    # --- Patch 1: BetterTransformer import ---
    print("[1/7] BetterTransformer import (airllm_base.py)")
    success &= patch_file(
        base_file,
        "from optimum.bettertransformer import BetterTransformer",
        """try:
    from optimum.bettertransformer import BetterTransformer
except (ImportError, ModuleNotFoundError):
    BetterTransformer = None""",
        "Wrap BetterTransformer import in try/except",
    )

    # --- Patch 2: Add _is_stateful class attribute ---
    print("[2/7] Add _is_stateful attribute (airllm_base.py)")
    success &= patch_file(
        base_file,
        "class AirLLMBaseModel(GenerationMixin):",
        """class AirLLMBaseModel(GenerationMixin):
    _is_stateful = False
    main_input_name = "input_ids"
""",
        "Add _is_stateful and main_input_name class attributes",
    )

    # --- Patch 3: DynamicCache handling in prepare_inputs_for_generation ---
    print("[3/7] DynamicCache handling (airllm_base.py)")
    success &= patch_file(
        base_file,
        """    def prepare_inputs_for_generation(
            self, input_ids, past_key_values=None, attention_mask=None, inputs_embeds=None, **kwargs
    ):
        if past_key_values is not None:
            past_length = self.get_past_key_values_cache_seq_len(past_key_values)""",
        """    def prepare_inputs_for_generation(
            self, input_ids, past_key_values=None, attention_mask=None, inputs_embeds=None, **kwargs
    ):
        # Handle newer transformers DynamicCache objects
        if past_key_values is not None:
            # Check if it's a Cache object with no actual content
            if hasattr(past_key_values, 'get_seq_length'):
                seq_len = past_key_values.get_seq_length()
                if seq_len == 0:
                    past_key_values = None
            elif isinstance(past_key_values, (list, tuple)):
                if len(past_key_values) == 0 or past_key_values[0] is None:
                    past_key_values = None

        if past_key_values is not None:
            past_length = self.get_past_key_values_cache_seq_len(past_key_values)""",
        "Handle DynamicCache with no content",
    )

    # --- Patch 4: Layer output tuple vs tensor ---
    print("[4/7] Layer output handling (airllm_base.py)")
    success &= patch_file(
        base_file,
        "                                new_seq = layer(seq, **kwargs)[0]",
        """                                layer_out = layer(seq, **kwargs)
                                new_seq = layer_out[0] if isinstance(layer_out, tuple) else layer_out""",
        "Handle layer returning tensor instead of tuple",
    )

    # --- Patch 5: BetterTransformer None check in init_model ---
    print("[5/7] BetterTransformer None check (airllm_base.py)")
    success &= patch_file(
        base_file,
        "if self.get_use_better_transformer():",
        "if self.get_use_better_transformer() and BetterTransformer is not None:",
        "Check BetterTransformer is not None before using",
    )

    # --- Patch 6: lm_head Path fix ---
    print("[6/7] lm_head Path type fix (airllm_base.py)")
    success &= patch_file(
        base_file,
        """        from .persist.model_persister import ModelPersister
        if layer_name == self.layer_names_dict.get('lm_head', 'lm_head'):
            if not ModelPersister.get_model_persister().model_persist_exist(layer_name + '.', self.checkpoint_path):""",
        """        from .persist.model_persister import ModelPersister
        from pathlib import Path
        if layer_name == self.layer_names_dict.get('lm_head', 'lm_head'):
            checkpoint = Path(self.checkpoint_path) if isinstance(self.checkpoint_path, str) else self.checkpoint_path
            if not ModelPersister.get_model_persister().model_persist_exist(layer_name + '.', checkpoint):""",
        "Convert checkpoint_path to Path for lm_head check",
    )

    # --- Patch 7: Qwen2 rotary embeddings ---
    print("[7/7] Qwen2 rotary embeddings (airllm_qwen2.py)")
    qwen2_content = qwen2_file.read_text(encoding="utf-8")
    if "get_pos_emb_args" in qwen2_content:
        print("  [SKIP] Qwen2 rotary embeddings (already applied)")
    else:
        new_qwen2 = '''import torch
from transformers import GenerationConfig, AutoConfig

from .airllm_base import AirLLMBaseModel


class AirLLMQWen2(AirLLMBaseModel):


    def __init__(self, *args, **kwargs):
        super(AirLLMQWen2, self).__init__(*args, **kwargs)
        self._rotary_emb = None

    def _get_rotary_emb(self):
        if self._rotary_emb is None:
            from transformers.models.qwen2.modeling_qwen2 import Qwen2RotaryEmbedding
            config = AutoConfig.from_pretrained(self.model_local_path)
            self._rotary_emb = Qwen2RotaryEmbedding(config=config)
        return self._rotary_emb

    def get_pos_emb_args(self, len_p, len_s):
        """Compute rotary position embeddings for Qwen2 layers."""
        rotary_emb = self._get_rotary_emb()
        position_ids = torch.arange(len_p, len_p + len_s, dtype=torch.long, device=self.running_device).unsqueeze(0)
        # Use float16 dummy to get cos/sin in float16 (matching model weight dtype)
        dummy = torch.zeros(1, len_s, 1, dtype=torch.float16, device=self.running_device)
        cos, sin = rotary_emb(dummy, position_ids)
        return {\'position_embeddings\': (cos, sin)}

    def get_use_better_transformer(self):
        return False
'''
        qwen2_file.write_text(new_qwen2, encoding="utf-8")
        print("  [OK]   Override get_pos_emb_args with rotary embedding computation")

    print(f"\n{'='*50}")
    if success:
        print("All patches applied successfully!")
    else:
        print("Some patches may need manual attention (see warnings above).")
    print(f"{'='*50}")


if __name__ == "__main__":
    apply_patches()
