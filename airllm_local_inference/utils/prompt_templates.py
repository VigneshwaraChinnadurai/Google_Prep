"""
Prompt Templates
================
Chat-style prompt templates for various model families.
Ensures correct formatting for instruction-tuned models.
"""

from dataclasses import dataclass
from typing import List, Dict


@dataclass
class PromptTemplate:
    """Defines how to format prompts for a specific model family."""
    name: str
    system_prefix: str
    system_suffix: str
    user_prefix: str
    user_suffix: str
    assistant_prefix: str
    assistant_suffix: str
    default_system_prompt: str

    def format_single(self, user_message: str, system_prompt: str = None) -> str:
        """Format a single-turn prompt."""
        sys_prompt = system_prompt or self.default_system_prompt
        parts = []
        if sys_prompt:
            parts.append(f"{self.system_prefix}{sys_prompt}{self.system_suffix}")
        parts.append(f"{self.user_prefix}{user_message}{self.user_suffix}")
        parts.append(self.assistant_prefix)
        return "".join(parts)

    def format_chat(self, messages: List[Dict[str, str]]) -> str:
        """Format a multi-turn conversation.
        
        messages: List of dicts with 'role' and 'content' keys.
                  role can be 'system', 'user', or 'assistant'
        """
        parts = []
        for msg in messages:
            role = msg["role"]
            content = msg["content"]
            if role == "system":
                parts.append(f"{self.system_prefix}{content}{self.system_suffix}")
            elif role == "user":
                parts.append(f"{self.user_prefix}{content}{self.user_suffix}")
            elif role == "assistant":
                parts.append(f"{self.assistant_prefix}{content}{self.assistant_suffix}")
        # Add assistant prefix for the model to continue from
        parts.append(self.assistant_prefix)
        return "".join(parts)


# ============================================================
# Pre-defined templates for popular model families
# ============================================================

LLAMA2_TEMPLATE = PromptTemplate(
    name="llama2-chat",
    system_prefix="<<SYS>>\n",
    system_suffix="\n<</SYS>>\n\n",
    user_prefix="[INST] ",
    user_suffix=" [/INST]",
    assistant_prefix="",
    assistant_suffix=" ",
    default_system_prompt="You are a helpful, respectful and honest assistant. Always answer as helpfully as possible.",
)

LLAMA3_TEMPLATE = PromptTemplate(
    name="llama3-instruct",
    system_prefix="<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n",
    system_suffix="<|eot_id|>",
    user_prefix="<|start_header_id|>user<|end_header_id|>\n\n",
    user_suffix="<|eot_id|>",
    assistant_prefix="<|start_header_id|>assistant<|end_header_id|>\n\n",
    assistant_suffix="<|eot_id|>",
    default_system_prompt="You are a helpful AI assistant.",
)

MISTRAL_TEMPLATE = PromptTemplate(
    name="mistral-instruct",
    system_prefix="",
    system_suffix="",
    user_prefix="[INST] ",
    user_suffix=" [/INST]",
    assistant_prefix="",
    assistant_suffix="</s>",
    default_system_prompt="",
)

QWEN_TEMPLATE = PromptTemplate(
    name="qwen-chat",
    system_prefix="<|im_start|>system\n",
    system_suffix="<|im_end|>\n",
    user_prefix="<|im_start|>user\n",
    user_suffix="<|im_end|>\n",
    assistant_prefix="<|im_start|>assistant\n",
    assistant_suffix="<|im_end|>\n",
    default_system_prompt="You are a helpful assistant.",
)

GENERIC_TEMPLATE = PromptTemplate(
    name="generic",
    system_prefix="### System:\n",
    system_suffix="\n\n",
    user_prefix="### User:\n",
    user_suffix="\n\n",
    assistant_prefix="### Assistant:\n",
    assistant_suffix="\n\n",
    default_system_prompt="You are a helpful AI assistant.",
)

TEMPLATES = {
    "llama2": LLAMA2_TEMPLATE,
    "llama3": LLAMA3_TEMPLATE,
    "mistral": MISTRAL_TEMPLATE,
    "qwen": QWEN_TEMPLATE,
    "generic": GENERIC_TEMPLATE,
}


def get_template(model_id: str) -> PromptTemplate:
    """Auto-detect the appropriate template from a model ID."""
    model_lower = model_id.lower()
    if "llama-3" in model_lower or "llama3" in model_lower:
        return LLAMA3_TEMPLATE
    elif "llama-2" in model_lower or "llama2" in model_lower:
        return LLAMA2_TEMPLATE
    elif "mistral" in model_lower or "mixtral" in model_lower:
        return MISTRAL_TEMPLATE
    elif "qwen" in model_lower:
        return QWEN_TEMPLATE
    else:
        return GENERIC_TEMPLATE
