from typing import List
import bisect

class Solution:
    def maximumWeight(self, intervals: List[List[int]]) -> List[int]:
        n = len(intervals)
        # Sort by right endpoint, break ties by left endpoint, then original index
        order = sorted(range(n), key=lambda i: (intervals[i][1], intervals[i][0], i))

        # Sorted right endpoints for binary search
        sorted_r = [intervals[order[i]][1] for i in range(n)]

        # dp[k][i] = (max_weight, lex_smallest_indices_tuple) using exactly k intervals
        # among the first i+1 in sorted order
        # We use 1-indexed dp: dp[k][i] for i in 0..n
        # dp[k][i] = best answer using k non-overlapping intervals from order[0..i-1]

        # State: (weight, indices_tuple) — compare lexicographically
        NEG = (-1, tuple())  # represents impossible / -infinity

        # dp[k][i]: best using k intervals from first i (0-indexed order[0..i-1])
        # Transition: for interval order[i-1] (the i-th in sorted order):
        #   Option 1: skip it → dp[k][i] = dp[k][i-1]
        #   Option 2: take it → find last j where r[j] < l[order[i-1]]
        #             dp[k][i] = combine(dp[k-1][j+1], this interval)
        # We want k from 1 to 4.

        K = 4
        # dp[k][i]: best (weight, indices) picking exactly k from first i intervals
        # Use list of lists, size (K+1) x (n+1)
        # Initialize all as NEG except dp[0][i] = (0, ()) for all i

        # To save memory and simplify, use 2D arrays
        # dp[k] = array of size n+1
        INF_NEG = (0, ())  # dp[0][i] = (0, ()) always valid (pick 0 intervals)

        # dp[k][i] = best state picking exactly k intervals from first i
        dp = [[(0, ())] * (n + 1) for _ in range(K + 1)]
        # dp[k][0] = (0,()) for k==0, impossible for k>0
        for k in range(1, K + 1):
            dp[k][0] = (-1, ())  # impossible

        for i in range(1, n + 1):
            idx = order[i - 1]
            l_i, r_i, w_i = intervals[idx]

            # Find last position j (in sorted order, 0-indexed) with r[j] < l_i
            # i.e., last j in [0, i-2] with sorted_r[j] < l_i
            # bisect_left on sorted_r[0..i-1] for l_i
            pos = bisect.bisect_left(sorted_r, l_i, 0, i - 1)
            # sorted_r[pos-1] < l_i (if pos > 0), sorted_r[pos] >= l_i
            # We want the last index with r < l_i → that's pos-1 (0-indexed in sorted)
            # In dp terms (1-indexed): dp[k-1][pos] covers first pos intervals
            prev_count = pos  # use dp[k-1][pos]

            for k in range(K, 0, -1):
                # Option 1: skip interval i
                dp[k][i] = dp[k][i - 1]

                # Option 2: take interval i (requires k-1 intervals before it)
                if dp[k - 1][prev_count][0] >= 0 or k == 1:
                    prev_w, prev_indices = dp[k - 1][prev_count]
                    if k == 1:
                        # Just this interval alone
                        new_w = w_i
                        new_indices = (idx,)
                    else:
                        if prev_w < 0:
                            # Can't form k-1 intervals before → skip
                            continue
                        new_w = prev_w + w_i
                        new_indices = prev_indices + (idx,)

                    # Sort indices for lexicographic comparison
                    new_indices_sorted = tuple(sorted(new_indices))
                    candidate = (new_w, new_indices_sorted)

                    # Compare: prefer higher weight, then lex smaller indices
                    cur_w, cur_idx = dp[k][i]
                    if (candidate[0] > cur_w or
                            (candidate[0] == cur_w and candidate[1] < cur_idx)):
                        dp[k][i] = candidate

        # Find best answer: try all k from 1 to K, pick best
        best_w = -1
        best_idx = None

        for k in range(1, K + 1):
            w, idx_tuple = dp[k][n]
            if w < 0:
                continue
            if (w > best_w or
                    (w == best_w and list(idx_tuple) < list(best_idx))):
                best_w = w
                best_idx = list(idx_tuple)

        return sorted(best_idx) if best_idx else []