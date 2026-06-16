```python
from typing import List

class Solution:
    def mapWordWeights(self, words: List[str], weights: List[int]) -> str:
        result_chars = []

        for word in words:
            word_weight = 0
            # Calculate the total weight for the current word.
            for char in word:
                # Determine the character's 0-based alphabetical index ('a' -> 0, 'b' -> 1, ...).
                index = ord(char) - ord('a')
                # Add the corresponding weight to the word's total weight.
                word_weight += weights[index]

            # Take the total weight modulo 26 to get a value between 0 and 25.
            mod_weight = word_weight % 26

            # Map the resulting value to a character in reverse alphabetical order.
            # The mapping is: 0 -> 'z', 1 -> 'y', ..., 25 -> 'a'.
            # This is achieved by subtracting the value from the ASCII code of 'z'.
            mapped_char = chr(ord('z') - mod_weight)
            
            result_chars.append(mapped_char)

        # Concatenate all the mapped characters to form the final result string.
        return "".join(result_chars)
```