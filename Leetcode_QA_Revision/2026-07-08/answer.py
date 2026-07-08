import bisect
from typing import List

class Solution:
    def sumAndMultiply(self, s: str, queries: List[List[int]]) -> List[int]:
        MOD = 10**9 + 7
        
        # Step 1: Preprocessing
        # Extract non-zero digits and their original indices
        nz_digits = []
        nz_indices = []
        for i, char in enumerate(s):
            if char != '0':
                nz_digits.append(int(char))
                nz_indices.append(i)

        N = len(nz_digits)
        if N == 0:
            # If there are no non-zero digits in s, all answers are 0
            return [0] * len(queries)

        # Precompute prefix sums for the sum of non-zero digits
        # prefix_sum_nz[i] stores the sum of nz_digits[0...i-1]
        prefix_sum_nz = [0] * (N + 1)
        for i in range(N):
            prefix_sum_nz[i+1] = prefix_sum_nz[i] + nz_digits[i]

        # Precompute prefix values for the concatenated number (modulo MOD)
        # prefix_val_nz[i] stores the value of concatenating nz_digits[0...i-1]
        prefix_val_nz = [0] * (N + 1)
        for i in range(N):
            prefix_val_nz[i+1] = (prefix_val_nz[i] * 10 + nz_digits[i]) % MOD

        # Precompute powers of 10 (modulo MOD)
        powers_of_10 = [1] * (N + 1)
        for i in range(N):
            powers_of_10[i+1] = (powers_of_10[i] * 10) % MOD

        # Step 2: Process each query
        answer = []
        for l, r in queries:
            # Find the range [start_idx, end_idx) in nz_digits corresponding
            # to the original index range [l, r] using binary search.
            start_idx = bisect.bisect_left(nz_indices, l)
            end_idx = bisect.bisect_right(nz_indices, r)

            if start_idx >= end_idx:
                # No non-zero digits in the query range
                answer.append(0)
                continue

            # Calculate sum of digits for the range
            current_sum = prefix_sum_nz[end_idx] - prefix_sum_nz[start_idx]

            # Calculate the concatenated value 'x' for the range
            k = end_idx - start_idx
            val_full = prefix_val_nz[end_idx]
            val_prefix = prefix_val_nz[start_idx]
            
            term_to_subtract = (val_prefix * powers_of_10[k]) % MOD
            x = (val_full - term_to_subtract + MOD) % MOD

            # Calculate the final result for the query
            res = (x * current_sum) % MOD
            answer.append(res)
            
        return answer