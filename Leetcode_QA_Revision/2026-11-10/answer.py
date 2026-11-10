class Solution:
    def climbStairs(self, n: int, costs: List[int]) -> int:
        INF = float('inf')
        dp = [INF] * (n + 1)
        dp[0] = 0
        for j in range(1, n + 1):
            c = costs[j - 1]
            best = INF
            for d in (1, 2, 3):
                i = j - d
                if i >= 0 and dp[i] + d * d < best:
                    best = dp[i] + d * d
            dp[j] = best + c
        return dp[n]