class Solution:
    def smallestUniqueSubarray(self, nums: List[int]) -> int:
        import random
        from collections import Counter
        n = len(nums)
        M = (1 << 61) - 1
        B = random.randrange(10**6, M - 1)
        H = [0] * (n + 1)
        P = [1] * (n + 1)
        for i, x in enumerate(nums):
            H[i + 1] = (H[i] * B + x) % M
            P[i + 1] = (P[i] * B) % M

        def has_unique(L):
            pl = P[L]
            cnt = Counter((H[i + L] - H[i] * pl) % M for i in range(n - L + 1))
            for c in cnt.values():
                if c == 1:
                    return True
            return False

        lo, hi = 1, n
        while lo < hi:
            mid = (lo + hi) // 2
            if has_unique(mid):
                hi = mid
            else:
                lo = mid + 1
        return lo