from typing import List

class Solution:
    def sumCounts(self, nums: List[int]) -> int:
        MOD = 10**9 + 7
        n = len(nums)

        # Two BITs for range-add / prefix-sum queries.
        # prefix_sum(p) = (p+1)*bit_d.query(p) - bit_kd.query(p)
        bit_d  = [0] * (n + 2)
        bit_kd = [0] * (n + 2)

        def update(bit, i, v):
            i += 1
            while i <= n + 1:
                bit[i] = (bit[i] + v) % MOD
                i += i & (-i)

        def query(bit, i):
            i += 1
            s = 0
            while i > 0:
                s = (s + bit[i]) % MOD
                i -= i & (-i)
            return s

        def range_add(l, r):          # add 1 to d[l..r]
            if l > r: return
            update(bit_d,  l,     1)
            update(bit_d,  r + 1, MOD - 1)
            update(bit_kd, l,     l % MOD)
            update(bit_kd, r + 1, MOD - (r + 1) % MOD)

        def prefix_sum(p):            # sum of d[0..p]
            if p < 0: return 0
            return ((p + 1) * query(bit_d, p) % MOD - query(bit_kd, p) + MOD) % MOD

        prev_occ = {}
        A = 0     # Sum_{i=0}^{j} d(i,j)^2  (updated each step)
        B = 0     # Sum_{i=0}^{j} d(i,j)
        total = 0

        for j in range(n):
            p = prev_occ.get(nums[j], -1)   # last occurrence before j

            # C = Sum_{i=p+1}^{j-1} d(i, j-1)  = B - prefix_sum(p)
            C     = (B - prefix_sum(p) + MOD) % MOD
            delta = (j - p) % MOD           # (j - p) new contributions

            # A_j = A_{j-1} + 2*C + delta   (expand (d+1)^2 = d^2 + 2d + 1)
            A = (A + 2 * C + delta) % MOD
            B = (B + delta) % MOD
            total = (total + A) % MOD

            # Update BIT: d[i] += 1 for i in [p+1, j]
            range_add(p + 1, j)
            prev_occ[nums[j]] = j

        return total