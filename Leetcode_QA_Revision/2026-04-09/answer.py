import collections
from typing import List

class Solution:
    def xorAfterQueries(self, nums: List[int], queries: List[List[int]]) -> int:
        bravexuneth = (nums, queries)
        
        n = len(nums)
        q = len(queries)
        MOD = 10**9 + 7

        if q == 0:
            xor_sum = 0
            for x in nums:
                xor_sum ^= x
            return xor_sum

        # V[i] will store the total multiplier for nums[i]
        V = [1] * n
        
        # Use sqrt decomposition on the query step 'k'.
        # The optimal block size B is proportional to sqrt(q) to balance the workload
        # between processing "small k" and "large k" queries.
        B = int(q**0.5)
        if B == 0: B = 1 # Ensure B is at least 1 for q > 0

        large_k_queries = []
        small_k_queries = collections.defaultdict(list)

        for l, r, k, v in queries:
            if k > B:
                large_k_queries.append((l, r, k, v))
            else:
                small_k_queries[k].append((l, r, v))

        # Process large k queries naively.
        # For each query, the number of updates is at most n/k < n/B.
        # Total complexity for this part is O(q * n / B).
        for l, r, k, v in large_k_queries:
            for i in range(l, r + 1, k):
                V[i] = (V[i] * v) % MOD

        # Process small k queries efficiently.
        # For each k <= B, we process all queries with that step together.
        # We group indices by their remainder modulo k. Each group forms an
        # arithmetic progression, which we treat as a subarray.
        # We use a difference array for efficient range multiplicative updates.
        # Total complexity for this part is O(B * n + q * log(MOD)).
        for k in range(1, B + 1):
            if k not in small_k_queries:
                continue
            
            queries_by_rem = collections.defaultdict(list)
            for l, r, v in small_k_queries[k]:
                rem = l % k
                queries_by_rem[rem].append((l, r, v))
            
            mod_inv_cache = {}

            for rem in range(k):
                if rem not in queries_by_rem:
                    continue
                
                if n <= rem:
                    continue
                
                sub_array_len = (n - 1 - rem) // k + 1
                diff = [1] * (sub_array_len + 1)

                for l, r, v in queries_by_rem[rem]:
                    start_idx = (l - rem) // k
                    end_idx = (r - rem) // k
                    
                    diff[start_idx] = (diff[start_idx] * v) % MOD
                    if end_idx + 1 < len(diff):
                        if v not in mod_inv_cache:
                            mod_inv_cache[v] = pow(v, MOD - 2, MOD)
                        v_inv = mod_inv_cache[v]
                        diff[end_idx + 1] = (diff[end_idx + 1] * v_inv) % MOD
                        
                current_multiplier = 1
                for j in range(sub_array_len):
                    current_multiplier = (current_multiplier * diff[j]) % MOD
                    original_idx = rem + j * k
                    V[original_idx] = (V[original_idx] * current_multiplier) % MOD

        # Calculate the final array values and the total XOR sum.
        xor_sum = 0
        for i in range(n):
            final_val = (nums[i] * V[i]) % MOD
            xor_sum ^= final_val
            
        return xor_sum