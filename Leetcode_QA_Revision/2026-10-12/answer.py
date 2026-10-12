from typing import List
from collections import Counter

class Solution:
    def maxSum(self, nums: List[int], k: int) -> int:
        MOD = 10**9 + 7

        # Key insight: AND+OR operation preserves the multiset of bits at each position.
        # For each bit position, the count of 1s across all numbers is invariant.
        # To maximise sum of squares of k numbers, greedily assign bits to fewer numbers
        # (concentrate bits into as few elements as possible = greedy packing).
        # For each bit, assign its count of 1s to the top k elements (leftmost available).

        # Count bits at each position across all nums
        bit_count = [0] * 30
        for x in nums:
            for b in range(30):
                if x >> b & 1:
                    bit_count[b] += 1

        # Build k largest elements by assigning bits greedily
        # For each bit (high to low), assign its 1s to the first `cnt` of the k slots
        result_bits = [0] * k  # result_bits[i] = value of i-th chosen element

        for b in range(29, -1, -1):
            cnt = bit_count[b]
            # Assign this bit to the first min(cnt, k) elements
            for i in range(min(cnt, k)):
                result_bits[i] |= (1 << b)

        return sum(x * x % MOD for x in result_bits) % MOD