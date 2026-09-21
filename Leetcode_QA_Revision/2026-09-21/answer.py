from typing import List

class Solution:
    def resultArray(self, nums: List[int], k: int) -> List[int]:
        n = len(nums)
        result = [0] * k

        # A contiguous subarray nums[i..j] (0 <= i <= j <= n-1) is chosen.
        # Its product mod k = x contributes 1 to result[x].
        # Total subarrays = n*(n+1)/2, we count by remainder.
        #
        # Approach: for each right endpoint j, maintain a count array
        # cnt[r] = number of subarrays ending at j with product mod k == r.
        #
        # Transition: when we move from j-1 to j:
        #   new_cnt[r] = cnt[r * modinv... ] -- no, multiplication.
        #   For each subarray ending at j: it's either nums[j] alone, or
        #   extends a subarray ending at j-1.
        #   product(i..j) = product(i..j-1) * nums[j]
        #   So new_cnt[(r * nums[j]) % k] += cnt[r] for all r,
        #   plus new_cnt[nums[j] % k] += 1 (subarray of just nums[j]).
        #
        # This is O(n * k) time.

        cnt = [0] * k  # cnt[r] = # subarrays ending at current j with product ≡ r mod k

        for j in range(n):
            v = nums[j] % k
            new_cnt = [0] * k
            # Extend all previous subarrays
            for r in range(k):
                if cnt[r]:
                    new_cnt[(r * v) % k] += cnt[r]
            # New subarray of just nums[j]
            new_cnt[v] += 1
            cnt = new_cnt
            # Add to result
            for r in range(k):
                result[r] += cnt[r]

        return result