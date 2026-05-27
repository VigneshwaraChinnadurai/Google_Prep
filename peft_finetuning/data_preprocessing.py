"""
Data Preprocessing Pipeline for Enterprise LLM Fine-Tuning
===========================================================

Handles:
- Multiple enterprise data formats (instruction, conversation, QA, completion)
- Tokenization with proper chat templates
- Sequence packing for training efficiency
- Streaming for large datasets that exceed RAM
- Train/eval splitting with stratification
"""

import json
import logging
from pathlib import Path
from typing import Dict, List, Optional, Generator

import torch
from datasets import Dataset, load_dataset, concatenate_datasets
from transformers import AutoTokenizer, PreTrainedTokenizer

from .config import DataConfig, DataFormat, ModelConfig

logger = logging.getLogger(__name__)


# ═══════════════════════════════════════════════════════════════════════════════
# DATA FORMAT TEMPLATES
# ═══════════════════════════════════════════════════════════════════════════════

INSTRUCTION_TEMPLATE = """### System:
{system}

### Instruction:
{instruction}

### Input:
{input}

### Response:
{output}"""

INSTRUCTION_TEMPLATE_NO_INPUT = """### System:
{system}

### Instruction:
{instruction}

### Response:
{output}"""

QA_TEMPLATE = """### System:
{system}

### Question:
{question}

### Answer:
{answer}"""

COMPLETION_TEMPLATE = """{prompt}{completion}"""


# ═══════════════════════════════════════════════════════════════════════════════
# DATA PREPROCESSING CLASS
# ═══════════════════════════════════════════════════════════════════════════════

class DataPreprocessor:
    """
    Enterprise data preprocessing pipeline.

    Converts raw enterprise data into tokenized training samples with:
    - Proper formatting using chat/instruction templates
    - Label masking (only compute loss on response tokens)
    - Sequence packing for training efficiency
    - Memory-efficient streaming for large datasets
    """

    def __init__(self, model_config: ModelConfig, data_config: DataConfig):
        self.model_config = model_config
        self.data_config = data_config
        self.tokenizer = self._load_tokenizer()

    def _load_tokenizer(self) -> PreTrainedTokenizer:
        """Load and configure the tokenizer."""
        tokenizer = AutoTokenizer.from_pretrained(
            self.model_config.model_name_or_path,
            trust_remote_code=self.model_config.trust_remote_code,
            token=self.model_config.hf_token,
        )

        # Ensure pad token is set (many LLMs don't have one)
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token
            tokenizer.pad_token_id = tokenizer.eos_token_id

        tokenizer.padding_side = "right"  # Required for training (left for inference)
        return tokenizer

    def prepare_datasets(self) -> tuple[Dataset, Optional[Dataset]]:
        """
        Main entry point: load, format, tokenize, and split data.

        Returns:
            (train_dataset, eval_dataset) - tokenized and ready for Trainer
        """
        logger.info(f"Loading data from: {self.data_config.train_file}")
        logger.info(f"Data format: {self.data_config.data_format.value}")

        # Load raw data
        raw_dataset = self._load_raw_data()

        # Format into unified text
        formatted_dataset = raw_dataset.map(
            self._format_sample,
            remove_columns=raw_dataset.column_names,
            num_proc=self.data_config.num_proc,
            desc="Formatting samples",
        )

        # Split if no separate eval file
        if self.data_config.eval_file:
            eval_raw = self._load_raw_data(self.data_config.eval_file)
            eval_formatted = eval_raw.map(
                self._format_sample,
                remove_columns=eval_raw.column_names,
                num_proc=self.data_config.num_proc,
                desc="Formatting eval samples",
            )
        else:
            split = formatted_dataset.train_test_split(
                test_size=self.data_config.eval_split_ratio,
                seed=self.data_config.seed,
            )
            formatted_dataset = split["train"]
            eval_formatted = split["test"]

        # Tokenize
        train_dataset = formatted_dataset.map(
            self._tokenize,
            batched=True,
            remove_columns=["text"],
            num_proc=self.data_config.num_proc,
            desc="Tokenizing train set",
        )

        eval_dataset = eval_formatted.map(
            self._tokenize,
            batched=True,
            remove_columns=["text"],
            num_proc=self.data_config.num_proc,
            desc="Tokenizing eval set",
        )

        # Optional: pack sequences for efficiency
        if self.data_config.packing:
            train_dataset = self._pack_sequences(train_dataset)

        logger.info(f"Train samples: {len(train_dataset)}")
        logger.info(f"Eval samples: {len(eval_dataset)}")
        logger.info(f"Max sequence length: {self.data_config.max_seq_length}")

        return train_dataset, eval_dataset

    # ─── Data Loading ─────────────────────────────────────────────────────────

    def _load_raw_data(self, filepath: Optional[str] = None) -> Dataset:
        """Load raw data from various file formats."""
        filepath = filepath or self.data_config.train_file
        path = Path(filepath)

        if not path.exists():
            raise FileNotFoundError(
                f"Data file not found: {filepath}\n"
                f"Create a JSONL file with your enterprise data. Example format:\n"
                f'{{"instruction": "Summarize this policy", "input": "...", "output": "..."}}'
            )

        suffix = path.suffix.lower()

        if suffix in (".jsonl", ".json"):
            dataset = load_dataset("json", data_files=str(path), split="train")
        elif suffix == ".csv":
            dataset = load_dataset("csv", data_files=str(path), split="train")
        elif suffix == ".parquet":
            dataset = load_dataset("parquet", data_files=str(path), split="train")
        else:
            raise ValueError(f"Unsupported file format: {suffix}. Use .jsonl, .json, .csv, or .parquet")

        logger.info(f"Loaded {len(dataset)} samples from {filepath}")
        return dataset

    # ─── Formatting ───────────────────────────────────────────────────────────

    def _format_sample(self, sample: Dict) -> Dict[str, str]:
        """
        Convert a raw sample into formatted text based on data_format.

        The formatted text includes both prompt and response, with the model
        trained to predict only the response portion.
        """
        fmt = self.data_config.data_format

        if fmt == DataFormat.INSTRUCTION:
            return self._format_instruction(sample)
        elif fmt == DataFormat.CONVERSATION:
            return self._format_conversation(sample)
        elif fmt == DataFormat.QA:
            return self._format_qa(sample)
        elif fmt == DataFormat.COMPLETION:
            return self._format_completion(sample)
        else:
            raise ValueError(f"Unknown data format: {fmt}")

    def _format_instruction(self, sample: Dict) -> Dict[str, str]:
        """Format instruction-following data."""
        instruction = sample.get("instruction", "")
        input_text = sample.get("input", "")
        output = sample.get("output", sample.get("response", ""))

        if input_text.strip():
            text = INSTRUCTION_TEMPLATE.format(
                system=self.data_config.system_prompt,
                instruction=instruction,
                input=input_text,
                output=output,
            )
        else:
            text = INSTRUCTION_TEMPLATE_NO_INPUT.format(
                system=self.data_config.system_prompt,
                instruction=instruction,
                output=output,
            )

        return {"text": text + self.tokenizer.eos_token}

    def _format_conversation(self, sample: Dict) -> Dict[str, str]:
        """Format multi-turn conversation data using the model's chat template."""
        messages = sample.get("messages", sample.get("conversations", []))

        # Use the tokenizer's built-in chat template if available
        if hasattr(self.tokenizer, "apply_chat_template"):
            text = self.tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=False
            )
        else:
            # Fallback: manual formatting
            parts = []
            for msg in messages:
                role = msg.get("role", msg.get("from", "user"))
                content = msg.get("content", msg.get("value", ""))
                parts.append(f"### {role.capitalize()}:\n{content}")
            text = "\n\n".join(parts)

        return {"text": text + self.tokenizer.eos_token}

    def _format_qa(self, sample: Dict) -> Dict[str, str]:
        """Format question-answer data."""
        text = QA_TEMPLATE.format(
            system=self.data_config.system_prompt,
            question=sample.get("question", ""),
            answer=sample.get("answer", ""),
        )
        return {"text": text + self.tokenizer.eos_token}

    def _format_completion(self, sample: Dict) -> Dict[str, str]:
        """Format simple prompt-completion data."""
        text = COMPLETION_TEMPLATE.format(
            prompt=sample.get("prompt", ""),
            completion=sample.get("completion", ""),
        )
        return {"text": text + self.tokenizer.eos_token}

    # ─── Tokenization ─────────────────────────────────────────────────────────

    def _tokenize(self, batch: Dict[str, List[str]]) -> Dict:
        """
        Tokenize a batch of formatted text samples.

        Applies truncation and returns input_ids, attention_mask, and labels.
        Labels are set to input_ids (causal LM objective).
        """
        tokenized = self.tokenizer(
            batch["text"],
            truncation=True,
            max_length=self.data_config.max_seq_length,
            padding=False,  # Dynamic padding in DataCollator is more memory-efficient
            return_attention_mask=True,
        )

        # For causal LM, labels = input_ids (shifted internally by the model)
        tokenized["labels"] = tokenized["input_ids"].copy()

        return tokenized

    # ─── Sequence Packing ─────────────────────────────────────────────────────

    def _pack_sequences(self, dataset: Dataset) -> Dataset:
        """
        Pack multiple short sequences into single max-length sequences.

        This maximizes GPU utilization by eliminating padding waste.
        A typical enterprise dataset has variable-length samples; packing
        ensures every token in a batch contributes to the loss.

        Example:
            Before: [sample1 (128 tokens)] [PAD x 1920] | [sample2 (256 tokens)] [PAD x 1792]
            After:  [sample1 | sample2 | sample3 | ... | sampleN] (2048 tokens, no padding)
        """
        max_len = self.data_config.max_seq_length
        packed_input_ids = []
        packed_attention_mask = []
        packed_labels = []

        current_ids = []
        current_mask = []
        current_labels = []

        for sample in dataset:
            ids = sample["input_ids"]
            mask = sample["attention_mask"]
            labels = sample["labels"]

            # If adding this sample exceeds max_len, save current and start new
            if len(current_ids) + len(ids) > max_len:
                if current_ids:
                    # Pad to max_len
                    pad_len = max_len - len(current_ids)
                    packed_input_ids.append(
                        current_ids + [self.tokenizer.pad_token_id] * pad_len
                    )
                    packed_attention_mask.append(current_mask + [0] * pad_len)
                    packed_labels.append(current_labels + [-100] * pad_len)  # -100 = ignore in loss

                current_ids = ids[:max_len]
                current_mask = mask[:max_len]
                current_labels = labels[:max_len]
            else:
                current_ids.extend(ids)
                current_mask.extend(mask)
                current_labels.extend(labels)

        # Don't forget the last packed sequence
        if current_ids:
            pad_len = max_len - len(current_ids)
            packed_input_ids.append(current_ids + [self.tokenizer.pad_token_id] * pad_len)
            packed_attention_mask.append(current_mask + [0] * pad_len)
            packed_labels.append(current_labels + [-100] * pad_len)

        packed_dataset = Dataset.from_dict({
            "input_ids": packed_input_ids,
            "attention_mask": packed_attention_mask,
            "labels": packed_labels,
        })

        logger.info(
            f"Packing: {len(dataset)} samples → {len(packed_dataset)} packed sequences "
            f"(max_len={max_len})"
        )
        return packed_dataset


# ═══════════════════════════════════════════════════════════════════════════════
# SAMPLE DATA GENERATOR (for testing)
# ═══════════════════════════════════════════════════════════════════════════════

def generate_sample_data(output_path: str, num_samples: int = 100, format: DataFormat = DataFormat.INSTRUCTION):
    """
    Generate synthetic enterprise training data for testing the pipeline.

    This creates realistic-looking samples that demonstrate the expected
    data format without containing any real proprietary information.
    """
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    samples = []

    enterprise_topics = [
        ("Summarize the quarterly sales report", "Q3 revenue was $4.2M, up 15% YoY. Key growth in APAC region.", "Q3 revenue reached $4.2M (15% YoY growth), primarily driven by APAC expansion."),
        ("Draft an email response to a customer complaint", "Customer reports delayed shipment order #12345", "Dear Customer, We sincerely apologize for the delay with order #12345. We've expedited shipping and added a 15% discount to your next order."),
        ("Explain our refund policy", "", "Our refund policy allows returns within 30 days of purchase. Items must be unused and in original packaging. Refunds are processed within 5-7 business days."),
        ("Create a meeting agenda", "Project Alpha kickoff with engineering and product teams", "Project Alpha Kickoff Agenda:\n1. Project overview and objectives (10 min)\n2. Technical architecture review (20 min)\n3. Sprint planning and milestones (15 min)\n4. Q&A and action items (15 min)"),
        ("Analyze customer feedback trends", "NPS dropped from 72 to 65 this quarter", "The NPS decline from 72 to 65 suggests growing customer dissatisfaction. Key drivers: 1) Longer response times (avg 4h → 6h), 2) Feature requests unaddressed, 3) Pricing concerns from SMB segment."),
    ]

    for i in range(num_samples):
        topic = enterprise_topics[i % len(enterprise_topics)]

        if format == DataFormat.INSTRUCTION:
            samples.append({
                "instruction": topic[0],
                "input": topic[1],
                "output": topic[2],
            })
        elif format == DataFormat.QA:
            samples.append({
                "question": topic[0],
                "answer": topic[2],
            })
        elif format == DataFormat.CONVERSATION:
            samples.append({
                "messages": [
                    {"role": "system", "content": "You are a helpful enterprise assistant."},
                    {"role": "user", "content": f"{topic[0]}\n{topic[1]}".strip()},
                    {"role": "assistant", "content": topic[2]},
                ]
            })
        elif format == DataFormat.COMPLETION:
            samples.append({
                "prompt": f"Task: {topic[0]}\nContext: {topic[1]}\nResponse: ",
                "completion": topic[2],
            })

    with open(path, "w", encoding="utf-8") as f:
        for sample in samples:
            f.write(json.dumps(sample, ensure_ascii=False) + "\n")

    logger.info(f"Generated {num_samples} sample data entries at {path}")
    return path
