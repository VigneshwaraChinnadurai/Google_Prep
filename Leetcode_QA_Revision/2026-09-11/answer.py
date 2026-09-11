from typing import List
from collections import Counter

class Solution:
    def totalNumbers(self, digits: List[int]) -> int:
        freq = Counter(digits)
        count = 0
        for num in range(100, 1000, 2):  # 3-digit even numbers
            d0 = num // 100
            d1 = (num // 10) % 10
            d2 = num % 10
            needed = Counter([d0, d1, d2])
            if all(freq[d] >= needed[d] for d in needed):
                count += 1
        return count