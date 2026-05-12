import collections
from typing import List

class Solution:
    def minJumps(self, nums: List[int]) -> int:
        n = len(nums)
        if n <= 1:
            return 0

        # 1. Pre-computation for prime factorization using a sieve
        # The maximum value of nums[i] is 10^6.
        MAX_VAL = 10**6
        spf = list(range(MAX_VAL + 1))
        # Sieve to find the Smallest Prime Factor (SPF) for numbers up to MAX_VAL
        for i in range(2, int(MAX_VAL**0.5) + 1):
            if spf[i] == i:  # i is a prime number
                for j in range(i * i, MAX_VAL + 1, i):
                    if spf[j] == j:  # If spf[j] is not set yet
                        spf[j] = i
        
        def get_prime_factors(num):
            factors = set()
            if num <= 1:
                return factors
            temp_num = num
            while temp_num > 1:
                p = spf[temp_num]
                factors.add(p)
                while temp_num % p == 0:
                    temp_num //= p
            return factors

        # 2. Build a map from prime factors to indices where numbers have that factor
        prime_factor_to_indices = collections.defaultdict(list)
        for i, num in enumerate(nums):
            factors = get_prime_factors(num)
            for p in factors:
                prime_factor_to_indices[p].append(i)

        # 3. BFS to find the shortest path
        queue = collections.deque([(0, 0)])  # (index, distance)
        visited_indices = {0}
        
        while queue:
            curr_idx, dist = queue.popleft()

            if curr_idx == n - 1:
                return dist

            # Option 1: Adjacent jumps
            for next_idx in [curr_idx - 1, curr_idx + 1]:
                if 0 <= next_idx < n and next_idx not in visited_indices:
                    visited_indices.add(next_idx)
                    queue.append((next_idx, dist + 1))

            # Option 2: Prime teleportation
            val = nums[curr_idx]
            # Check if val is a prime number using the pre-computed SPF array
            if val > 1 and spf[val] == val:
                p = val
                if p in prime_factor_to_indices:
                    # Jump to all indices where the number is a multiple of p
                    for next_idx in prime_factor_to_indices[p]:
                        if next_idx not in visited_indices:
                            visited_indices.add(next_idx)
                            queue.append((next_idx, dist + 1))
                    # Optimization: process each prime's connections only once
                    # This prevents re-adding the same set of indices to the queue
                    del prime_factor_to_indices[p]
        
        return -1 # Should not be reached based on problem constraints