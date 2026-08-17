from typing import List

class Solution:
    def stoneGameV(self, stoneValue: List[int]) -> int:
        n = len(stoneValue)
        if n == 1:
            return 0

        prefix = [0] * (n + 1)
        for i in range(n):
            prefix[i + 1] = prefix[i] + stoneValue[i]

        dp = [[0] * n for _ in range(n)]
        NEG_INF = float('-inf')
        # lp[l][k] = max(prefix[j+1] + dp[l][j]) for j in [l, k]
        lp = [[NEG_INF] * n for _ in range(n)]
        # rs[l][r] = max(dp[j][r] - prefix[j]) for j in [l+1, r]
        rs = [[NEG_INF] * n for _ in range(n)]

        for length in range(2, n + 1):
            for l in range(n - length, -1, -1):
                r = l + length - 1

                # Build lp[l][r-1]
                k = r - 1
                val = prefix[k + 1] + dp[l][k]
                lp[l][k] = val if k == l else max(lp[l][k - 1], val)

                # Build rs[l][r]
                val2 = dp[l + 1][r] - prefix[l + 1]
                rs[l][r] = val2 if (l + 1 == r) else max(val2, rs[l + 1][r])

                # Binary search for kstar: smallest k where left_sum >= right_sum
                # i.e. 2*prefix[k+1] >= prefix[l] + prefix[r+1]
                total = prefix[l] + prefix[r + 1]
                lo, hi = l, r - 1
                while lo < hi:
                    mid = (lo + hi) // 2
                    if 2 * prefix[mid + 1] < total:
                        lo = mid + 1
                    else:
                        hi = mid
                kstar = lo

                best = 0

                # Left region [l, kstar-1]: left_sum < right_sum
                # gain = left_sum + dp[l][k] = (prefix[k+1] - prefix[l]) + dp[l][k]
                #       = (prefix[k+1] + dp[l][k]) - prefix[l]
                if kstar > l:
                    best = lp[l][kstar - 1] - prefix[l]

                # Right region [kstar+1, r-1] or starting at kstar if ls > rs:
                # gain = right_sum + dp[k+1][r] = (prefix[r+1] - prefix[k+1]) + dp[k+1][r]
                #       = prefix[r+1] + (dp[k+1][r] - prefix[k+1])
                # j = k+1 ranges from kstar+1 to r, query rs[kstar][r]
                # But rs[l][r] = max(dp[j][r] - prefix[j]) for j in [l+1, r]
                # So right region query: rs[kstar][r] = max for j in [kstar+1, r]
                if kstar < r - 1:
                    best = max(best, prefix[r + 1] + rs[kstar][r])
                elif kstar == r - 1:
                    # only j = r contributes: dp[r][r] - prefix[r] = -prefix[r]
                    val_r = prefix[r + 1] + (dp[r][r] - prefix[r])
                    best = max(best, val_r)

                # Equal case: split at kstar where left_sum == right_sum
                ls_k = prefix[kstar + 1] - prefix[l]
                rs_k = prefix[r + 1] - prefix[kstar + 1]
                if ls_k == rs_k:
                    # Alice picks the better continuation
                    eq_val = ls_k + max(dp[l][kstar], dp[kstar + 1][r] if kstar + 1 <= r else 0)
                    best = max(best, eq_val)

                dp[l][r] = best

        return dp[0][n - 1]