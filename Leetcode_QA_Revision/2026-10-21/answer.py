class Solution:
    def minOperations(self, s: str) -> int:
        target = ''.join(sorted(s))
        if s == target:
            return 0

        n = len(s)
        if n == 2:
            return -1

        # Find leftmost and rightmost mismatch positions
        L = 0
        while L < n and s[L] == target[L]:
            L += 1
        R = n - 1
        while R >= 0 and s[R] == target[R]:
            R -= 1

        # If mismatch doesn't touch both ends → 1 op suffices
        if L > 0 or R < n - 1:
            return 1

        # Mismatch spans entire string [0, n-1]
        # 3 ops needed iff max is unique at position 0 AND min is unique at position n-1
        max_c = target[-1]   # largest char (last in sorted)
        min_c = target[0]    # smallest char (first in sorted)

        if (s[0] == max_c and s[-1] == min_c and
                s.count(max_c) == 1 and s.count(min_c) == 1):
            return 3

        return 2