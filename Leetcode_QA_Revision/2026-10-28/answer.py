class Solution:
    def minCost(self, grid: list[list[int]]) -> int:
        m, n = len(grid), len(grid[0])
        # dp[j] = bitmask of reachable XOR values at current row, column j
        dp = [0] * n
        for i in range(m):
            for j in range(n):
                v = grid[i][j]
                if i == 0 and j == 0:
                    cur = 1 << v
                else:
                    src = 0
                    if i > 0:
                        src |= dp[j]
                    if j > 0:
                        src |= dp[j - 1]
                    cur = 0
                    # shift every reachable XOR x to x ^ v
                    while src:
                        low = src & -src
                        x = low.bit_length() - 1
                        cur |= 1 << (x ^ v)
                        src ^= low
                dp[j] = cur
        mask = dp[n - 1]
        return (mask & -mask).bit_length() - 1