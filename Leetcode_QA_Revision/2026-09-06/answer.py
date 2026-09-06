class Solution:
    def numDistinct(self, s: str, t: str) -> int:
        m, n = len(s), len(t)
        if n > m:
            return 0

        # dp[j] = number of ways to form t[:j] using s processed so far
        # Process right to left to avoid using same s[i] twice
        dp = [0] * (n + 1)
        dp[0] = 1  # empty t matched by empty prefix of s

        for i in range(m):
            # Traverse j from n down to 1 to avoid overwriting
            for j in range(n, 0, -1):
                if s[i] == t[j - 1]:
                    dp[j] += dp[j - 1]

        return dp[n]