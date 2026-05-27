"""
Evaluation Module for Fine-Tuned Models
=========================================

Provides comprehensive evaluation:
- Perplexity (language modeling quality)
- ROUGE scores (summarization/generation quality)
- Custom generation evaluation with reference comparison
- Side-by-side comparison: base model vs fine-tuned
"""

import logging
import math
from typing import Dict, List, Optional

import torch
import numpy as np
from datasets import Dataset
from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline
from peft import PeftModel

from .config import PipelineConfig, EvalConfig

logger = logging.getLogger(__name__)


class ModelEvaluator:
    """
    Comprehensive evaluation of fine-tuned models.

    Metrics computed:
    1. Perplexity: How well the model predicts held-out text (lower = better)
    2. ROUGE-1/2/L: Overlap between generated and reference text
    3. Generation quality: Manual inspection of sample outputs
    4. Base vs Fine-tuned comparison: Quantify improvement
    """

    def __init__(self, config: PipelineConfig):
        self.config = config
        self.eval_config = config.evaluation
        self.model = None
        self.tokenizer = None

    def load_finetuned_model(self, adapter_path: Optional[str] = None):
        """Load the fine-tuned model (base + LoRA adapter)."""
        adapter_path = adapter_path or self.config.training.output_dir

        logger.info(f"Loading base model: {self.config.model.model_name_or_path}")
        logger.info(f"Loading adapter from: {adapter_path}")

        # Load tokenizer
        self.tokenizer = AutoTokenizer.from_pretrained(
            adapter_path,
            trust_remote_code=self.config.model.trust_remote_code,
        )
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        # Load base model (in 4-bit for memory efficiency during eval too)
        from transformers import BitsAndBytesConfig

        quantization_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_quant_type="nf4",
        )

        base_model = AutoModelForCausalLM.from_pretrained(
            self.config.model.model_name_or_path,
            quantization_config=quantization_config,
            device_map="auto",
            trust_remote_code=self.config.model.trust_remote_code,
            token=self.config.model.hf_token,
        )

        # Load LoRA adapter
        self.model = PeftModel.from_pretrained(base_model, adapter_path)
        self.model.eval()
        logger.info("Fine-tuned model loaded for evaluation.")

    def evaluate_all(self, eval_dataset: Dataset) -> Dict[str, float]:
        """
        Run all evaluation metrics.

        Returns:
            Dictionary of metric_name → score
        """
        results = {}

        if self.eval_config.compute_perplexity:
            ppl = self.compute_perplexity(eval_dataset)
            results["perplexity"] = ppl
            logger.info(f"Perplexity: {ppl:.2f}")

        if self.eval_config.compute_rouge:
            rouge = self.compute_rouge(eval_dataset)
            results.update(rouge)
            logger.info(f"ROUGE scores: {rouge}")

        # Generate sample outputs for qualitative review
        samples = self.generate_samples(eval_dataset)
        results["sample_outputs"] = samples

        return results

    def compute_perplexity(self, eval_dataset: Dataset) -> float:
        """
        Compute perplexity on the evaluation set.

        Perplexity = exp(average negative log-likelihood)

        Lower perplexity = model better predicts the held-out text.
        Typical values:
        - Base Llama-2 7B on general text: ~5-8
        - Fine-tuned on domain data: ~3-6 (should decrease after fine-tuning)
        """
        self.model.eval()
        total_loss = 0.0
        total_tokens = 0

        # Process in batches to manage memory
        batch_size = self.config.training.per_device_eval_batch_size
        num_samples = min(len(eval_dataset), self.eval_config.eval_samples)

        with torch.no_grad():
            for i in range(0, num_samples, batch_size):
                batch_end = min(i + batch_size, num_samples)
                batch = eval_dataset[i:batch_end]

                input_ids = torch.tensor(batch["input_ids"]).to(self.model.device)
                attention_mask = torch.tensor(batch["attention_mask"]).to(self.model.device)
                labels = torch.tensor(batch["labels"]).to(self.model.device)

                outputs = self.model(
                    input_ids=input_ids,
                    attention_mask=attention_mask,
                    labels=labels,
                )

                # Sum loss (already averaged over sequence length by model)
                loss = outputs.loss
                seq_length = (labels != -100).sum().item()

                total_loss += loss.item() * seq_length
                total_tokens += seq_length

        avg_loss = total_loss / total_tokens if total_tokens > 0 else float("inf")
        perplexity = math.exp(avg_loss)

        return round(perplexity, 4)

    def compute_rouge(self, eval_dataset: Dataset) -> Dict[str, float]:
        """
        Compute ROUGE scores by generating responses and comparing to references.

        ROUGE-1: Unigram overlap (recall-oriented)
        ROUGE-2: Bigram overlap (captures phrase-level similarity)
        ROUGE-L: Longest common subsequence (captures sentence structure)
        """
        try:
            from rouge_score import rouge_scorer
        except ImportError:
            logger.warning("rouge-score not installed. Skipping ROUGE evaluation.")
            return {}

        scorer = rouge_scorer.RougeScorer(["rouge1", "rouge2", "rougeL"], use_stemmer=True)

        predictions = []
        references = []
        num_samples = min(len(eval_dataset), self.eval_config.eval_samples)

        for i in range(num_samples):
            sample = eval_dataset[i]

            # Extract prompt (everything before the response)
            input_ids = sample["input_ids"]
            labels = sample["labels"]

            # Find where labels start (first non -100 token is start of response)
            response_start = next(
                (j for j, l in enumerate(labels) if l != -100), len(labels)
            )

            # Generate from the prompt portion
            prompt_ids = torch.tensor([input_ids[:response_start]]).to(self.model.device)

            with torch.no_grad():
                generated = self.model.generate(
                    prompt_ids,
                    max_new_tokens=self.eval_config.max_new_tokens,
                    temperature=self.eval_config.temperature,
                    do_sample=self.eval_config.temperature > 0,
                    pad_token_id=self.tokenizer.pad_token_id,
                )

            # Decode
            pred_text = self.tokenizer.decode(
                generated[0][response_start:], skip_special_tokens=True
            )
            ref_text = self.tokenizer.decode(
                [t for t in labels[response_start:] if t != -100],
                skip_special_tokens=True,
            )

            predictions.append(pred_text)
            references.append(ref_text)

        # Compute aggregate ROUGE scores
        rouge_results = {"rouge1": [], "rouge2": [], "rougeL": []}
        for pred, ref in zip(predictions, references):
            if pred.strip() and ref.strip():
                scores = scorer.score(ref, pred)
                for key in rouge_results:
                    rouge_results[key].append(scores[key].fmeasure)

        return {
            k: round(np.mean(v), 4) if v else 0.0
            for k, v in rouge_results.items()
        }

    def generate_samples(
        self, eval_dataset: Dataset, num_samples: int = 5
    ) -> List[Dict[str, str]]:
        """
        Generate sample outputs for qualitative evaluation.

        Returns list of {"prompt": ..., "generated": ..., "reference": ...}
        """
        samples = []
        num_samples = min(num_samples, len(eval_dataset))

        for i in range(num_samples):
            sample = eval_dataset[i]
            input_ids = sample["input_ids"]
            labels = sample["labels"]

            # Find response boundary
            response_start = next(
                (j for j, l in enumerate(labels) if l != -100), len(labels) // 2
            )

            prompt_ids = torch.tensor([input_ids[:response_start]]).to(self.model.device)

            with torch.no_grad():
                generated = self.model.generate(
                    prompt_ids,
                    max_new_tokens=self.eval_config.max_new_tokens,
                    temperature=self.eval_config.temperature,
                    do_sample=self.eval_config.temperature > 0,
                    pad_token_id=self.tokenizer.pad_token_id,
                )

            prompt_text = self.tokenizer.decode(input_ids[:response_start], skip_special_tokens=True)
            generated_text = self.tokenizer.decode(generated[0][response_start:], skip_special_tokens=True)
            reference_text = self.tokenizer.decode(
                [t for t in labels[response_start:] if t != -100], skip_special_tokens=True
            )

            samples.append({
                "prompt": prompt_text.strip(),
                "generated": generated_text.strip(),
                "reference": reference_text.strip(),
            })

        return samples

    def compare_base_vs_finetuned(
        self, eval_dataset: Dataset, num_samples: int = 5
    ) -> List[Dict[str, str]]:
        """
        Side-by-side comparison of base model vs fine-tuned model outputs.

        Useful for demonstrating the value of fine-tuning to stakeholders.
        """
        # Generate with fine-tuned model (adapter enabled)
        self.model.eval()
        finetuned_outputs = self.generate_samples(eval_dataset, num_samples)

        # Disable adapter to get base model outputs
        self.model.disable_adapter_layers()
        base_outputs = self.generate_samples(eval_dataset, num_samples)
        self.model.enable_adapter_layers()

        comparisons = []
        for ft, base in zip(finetuned_outputs, base_outputs):
            comparisons.append({
                "prompt": ft["prompt"],
                "base_model_output": base["generated"],
                "finetuned_output": ft["generated"],
                "reference": ft["reference"],
            })

        return comparisons


def print_evaluation_report(results: Dict, comparisons: Optional[List[Dict]] = None):
    """Pretty-print evaluation results."""
    print("\n" + "=" * 70)
    print("EVALUATION REPORT")
    print("=" * 70)

    if "perplexity" in results:
        print(f"\n📊 Perplexity: {results['perplexity']:.2f}")
        print(f"   (Lower is better. Base Llama-2-7B ≈ 5-8 on general text)")

    if "rouge1" in results:
        print(f"\n📊 ROUGE Scores:")
        print(f"   ROUGE-1 (unigram):  {results.get('rouge1', 0):.4f}")
        print(f"   ROUGE-2 (bigram):   {results.get('rouge2', 0):.4f}")
        print(f"   ROUGE-L (longest):  {results.get('rougeL', 0):.4f}")

    if "sample_outputs" in results:
        print(f"\n📝 Sample Generations ({len(results['sample_outputs'])} samples):")
        for i, sample in enumerate(results["sample_outputs"][:3], 1):
            print(f"\n   ─── Sample {i} ───")
            print(f"   Prompt:    {sample['prompt'][:100]}...")
            print(f"   Generated: {sample['generated'][:200]}...")
            print(f"   Reference: {sample['reference'][:200]}...")

    if comparisons:
        print(f"\n🔄 Base vs Fine-tuned Comparison:")
        for i, comp in enumerate(comparisons[:3], 1):
            print(f"\n   ─── Comparison {i} ───")
            print(f"   Prompt:         {comp['prompt'][:80]}...")
            print(f"   Base Model:     {comp['base_model_output'][:150]}...")
            print(f"   Fine-tuned:     {comp['finetuned_output'][:150]}...")
            print(f"   Reference:      {comp['reference'][:150]}...")

    print("\n" + "=" * 70)
