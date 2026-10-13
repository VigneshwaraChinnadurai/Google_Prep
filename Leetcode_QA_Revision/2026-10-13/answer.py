from typing import List

class Solution:
    def maxAlternatingSum(self, nums: List[int]) -> int:
        # Since we can rearrange freely, we work with absolute values (squaring removes sign).
        # Sort by |x| descending. Pair up: +largest² - second_largest² + ...
        # Odd positions (0,2,4,...) add, even positions (1,3,5,...) subtract.
        # Greedy: sort by |x| desc. Pair consecutive: (a,b) contributes a²-b² = (a-b)(a+b) >= 0.
        # Unpaired last element (if odd count) just adds its square.

        vals = sorted((abs(x) for x in nums), reverse=True)
        result = 0
        for i in range(0, len(vals), 2):
            result += vals[i] * vals[i]
            if i + 1 < len(vals):
                result -= vals[i+1] * vals[i+1]
        return result