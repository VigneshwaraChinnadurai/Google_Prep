import bisect

class Solution:
    def minSumSquareDiff(self, nums1: list[int], nums2: list[int], k1: int, k2: int) -> int:
        n = len(nums1)
        k = k1 + k2  # total budget (can use on either side)

        # Work with absolute differences
        diffs = sorted([abs(nums1[i] - nums2[i]) for i in range(n)], reverse=True)

        # Greedily reduce the largest differences first
        # Use binary search to determine how many steps needed to bring top values down
        for i in range(n):
            if k <= 0:
                break
            if i + 1 < n:
                # How many steps to level diffs[0..i] down to diffs[i+1]?
                steps = (diffs[i] - diffs[i+1]) * (i + 1)
            else:
                steps = diffs[i] * (i + 1)  # bring to 0

            if k >= steps:
                k -= steps
                diffs[i] = diffs[i+1] if i + 1 < n else 0
                # Actually set all top i+1 to diffs[i+1]
                for j in range(i + 1):
                    diffs[j] = diffs[i+1] if i + 1 < n else 0
            else:
                # Distribute remaining k among top i+1 diffs
                q, r = divmod(k, i + 1)
                for j in range(i + 1):
                    diffs[j] -= q
                for j in range(r):
                    diffs[j] -= 1
                k = 0
                break

        return sum(d * d for d in diffs)