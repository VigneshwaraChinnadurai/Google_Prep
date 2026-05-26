"""
Master script: Combines all parts and generates the expanded 70+ page DOCX.
"""
import os
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Execute parts in sequence
parts = [
    'generate_expanded_docx.py',  # Part 1: Front matter
    'gen_part2.py',               # Part 2: Exec Summary + Ch 1-3
    'gen_part3.py',               # Part 3: Ch 4
    'gen_part4.py',               # Part 4: Ch 5-9
    'gen_part5.py',               # Part 5: Ch 10 (Appendices)
]

# Read and combine all parts
combined_code = ""
for part in parts:
    path = os.path.join(BASE_DIR, part)
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    # Skip the module docstring and imports from parts 2-5 (they use variables from part 1)
    if part != parts[0]:
        # Remove the docstring at the top
        lines = content.split('\n')
        start = 0
        # Skip leading docstring
        if lines[0].startswith('"""'):
            for i in range(1, len(lines)):
                if '"""' in lines[i]:
                    start = i + 1
                    break
        combined_code += '\n' + '\n'.join(lines[start:])
    else:
        combined_code += content

# Write combined script
combined_path = os.path.join(BASE_DIR, '_combined_gen.py')
with open(combined_path, 'w', encoding='utf-8') as f:
    f.write(combined_code)

print(f"Combined script written to: {combined_path}")
print("Executing combined script...")

# Execute
exec(compile(open(combined_path, encoding='utf-8').read(), combined_path, 'exec'))
