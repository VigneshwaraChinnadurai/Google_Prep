class Solution:
    def uniquePaths(self, grid: List[List[int]]) -> int:
        MOD = 10**9 + 7
        m, n = len(grid), len(grid[0])
        dp = [[0] * n for _ in range(m)]
        colP = [[0] * n for _ in range(m)]
        rowP = [[0] * n for _ in range(m)]
        up = [[0] * n for _ in range(m)]
        for i in range(m):
            left = 0
            for j in range(n):
                if grid[i][j] == 1:
                    up[i][j] = (up[i - 1][j] + 1) if i > 0 else 1
                    left += 1
                    # dp stays 0
                else:
                    L = up[i - 1][j] if i > 0 else 0
                    Lp = left
                    left = 0
                    up[i][j] = 0
                    if i == 0 and j == 0:
                        val = 1
                    else:
                        val = 0
                        if j > 0:
                            val += dp[i][j - 1]
                        if i > 0:
                            val += dp[i - 1][j]
                        if i > 0 and j > 0 and L > 0:
                            s = colP[i - 1][j - 1]
                            if i - L - 1 >= 0:
                                s -= colP[i - L - 1][j - 1]
                            val += s
                        if i > 0 and j > 0 and Lp > 0:
                            s = rowP[i - 1][j - 1]
                            if j - Lp - 1 >= 0:
                                s -= rowP[i - 1][j - Lp - 1]
                            val += s
                        val %= MOD
                    dp[i][j] = val
                colP[i][j] = ((colP[i - 1][j] if i > 0 else 0) + dp[i][j]) % MOD
                rowP[i][j] = ((rowP[i][j - 1] if j > 0 else 0) + dp[i][j]) % MOD
        return dp[m - 1][n - 1] % MOD