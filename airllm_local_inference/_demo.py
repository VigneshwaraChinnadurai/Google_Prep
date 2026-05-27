"""End-to-end AirLLM inference demo"""
from airllm import AutoModel
import torch, time

print('='*60)
print('AirLLM Local Inference - End-to-End Demo')
print('='*60)
print()

print('[1/3] Loading model: Qwen/Qwen2.5-1.5B-Instruct')
start = time.time()
model = AutoModel.from_pretrained('Qwen/Qwen2.5-1.5B-Instruct', device='cpu')
print(f'      Model loaded in {time.time()-start:.1f}s')
print()

prompt = 'What is the capital of France? Answer in one sentence.'
print(f'[2/3] Prompt: "{prompt}"')
print()

input_tokens = model.tokenizer([prompt], return_tensors='pt',
                               return_attention_mask=False,
                               truncation=True, max_length=128, padding=False)
print(f'[3/3] Generating (max_new_tokens=30)...')
start = time.time()
output = model.generate(input_tokens['input_ids'].cpu(),
                       max_new_tokens=30,
                       use_cache=True,
                       return_dict_in_generate=True)
elapsed = time.time() - start
decoded = model.tokenizer.decode(output.sequences[0], skip_special_tokens=True)
print(f'      Generation time: {elapsed:.1f}s')
print()
print('='*60)
print(f'OUTPUT: {decoded}')
print('='*60)
