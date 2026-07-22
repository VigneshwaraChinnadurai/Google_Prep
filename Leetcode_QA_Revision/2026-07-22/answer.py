import math
from bisect import bisect_left, bisect_right

class Solution:
    def maxActiveSectionsAfterTrade(self, s: str, queries: list[list[int]]) -> list[int]:
        n = len(s)

        # Run-length encoding
        runs = []
        i = 0
        while i < n:
            j = i
            while j < n and s[j] == s[i]: j += 1
            runs.append((int(s[i]), i, j-1, j-i))  # (type, start, end, len)
            i = j
        m = len(runs)

        # Prefix sum of 1s
        prefix = [0] * (n + 1)
        for i in range(n):
            prefix[i+1] = prefix[i] + int(s[i])

        # Find run containing position pos
        run_starts = [r[1] for r in runs]
        def find_run(pos):
            return bisect_right(run_starts, pos) - 1

        # Extract valid vruns: 1-runs with 0-neighbors on both sides
        vruns = []  # (lzs, ls, re, rze, L, R, val)
        for i in range(1, m-1):
            if runs[i][0] == 1 and runs[i-1][0] == 0 and runs[i+1][0] == 0:
                lzs, ls  = runs[i-1][1], runs[i][1]
                re, rze  = runs[i][2],   runs[i+1][2]
                L, R     = runs[i-1][3], runs[i+1][3]
                vruns.append((lzs, ls, re, rze, L, R, L + R))

        V = len(vruns)
        vrun_lzs = [v[0] for v in vruns]
        vrun_rze = [v[3] for v in vruns]

        # Sparse table for range max on val
        if V > 0:
            LOG = int(math.log2(V)) + 1
            sparse = [[v[6] for v in vruns]]
            for k in range(1, LOG):
                prev, half = sparse[k-1], 1 << (k-1)
                sparse.append([max(prev[i], prev[i+half]) for i in range(V - (1<<k) + 1)])

            def range_max(lo, hi):
                if lo > hi: return 0
                k = int(math.log2(hi - lo + 1))
                return max(sparse[k][lo], sparse[k][hi - (1<<k) + 1])
        else:
            def range_max(lo, hi): return 0

        results = []
        for l, r in queries:
            base_outside = prefix[l] + (prefix[n] - prefix[r+1])
            base_in      = prefix[r+1] - prefix[l]
            max_gain     = 0

            rl = find_run(l)
            rr = find_run(r)

            # Partial-left: l inside a 0-run → try the 1-run to its right
            if (runs[rl][0] == 0 and rl+1 < m and runs[rl+1][0] == 1
                                   and rl+2 < m and runs[rl+2][0] == 0):
                re_i = runs[rl+1][2]
                if re_i < r:
                    left_g  = runs[rl+1][1] - l
                    right_g = min(runs[rl+2][3], r - re_i)
                    max_gain = max(max_gain, left_g + right_g)

            # Partial-right: r inside a 0-run → try the 1-run to its left
            if (runs[rr][0] == 0 and rr-1 >= 0 and runs[rr-1][0] == 1
                                   and rr-2 >= 0 and runs[rr-2][0] == 0):
                ls_j = runs[rr-1][1]
                if ls_j > l:
                    right_g = r - runs[rr-1][2]
                    left_g  = min(runs[rr-2][3], ls_j - l)
                    max_gain = max(max_gain, left_g + right_g)

            # Fully interior: vruns with lzs >= l and rze <= r → range-max
            v_low  = bisect_left(vrun_lzs, l)
            v_high = bisect_right(vrun_rze, r) - 1
            max_gain = max(max_gain, range_max(v_low, v_high))

            results.append(base_outside + base_in + max_gain)

        return results