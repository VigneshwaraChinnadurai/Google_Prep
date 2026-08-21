from typing import List
from math import gcd
from functools import reduce

class Solution:
    def findKthSmallest(self, coins: List[int], k: int) -> int:
        # Deduplicate and remove multiples: if coin a divides coin b, remove b.
        coins = sorted(set(coins))
        filtered = []
        for c in coins:
            if not any(c % f == 0 for f in filtered):
                filtered.append(c)
        coins = filtered
        n = len(coins)

        def lcm(a, b):
            return a * b // gcd(a, b)

        # Precompute LCM for every non-empty subset using bitmask
        # subset_lcm[mask] = LCM of coins[i] for i in mask
        subset_lcm = [0] * (1 << n)
        for mask in range(1, 1 << n):
            lsb = mask & (-mask)
            idx = lsb.bit_length() - 1
            rest = mask ^ lsb
            if rest == 0:
                subset_lcm[mask] = coins[idx]
            else:
                l = subset_lcm[rest]
                # Cap LCM to avoid huge numbers (anything > k * max_coin is useless)
                cap = k * max(coins) + 1
                g = gcd(l, coins[idx])
                new_lcm = l // g * coins[idx]
                subset_lcm[mask] = min(new_lcm, cap)

        # count(x) = number of valid amounts <= x (inclusion-exclusion)
        def count(x):
            total = 0
            for mask in range(1, 1 << n):
                l = subset_lcm[mask]
                if l == 0:
                    continue
                bits = bin(mask).count('1')
                c = x // l
                if bits % 2 == 1:
                    total += c
                else:
                    total -= c
            return total

        # Binary search for smallest x with count(x) >= k
        lo, hi = 1, k * min(coins)
        while lo < hi:
            mid = (lo + hi) // 2
            if count(mid) >= k:
                hi = mid
            else:
                lo = mid + 1

        return lo