class Solution:
    def maxPalindromes(self, s: str, k: int) -> int:
        n = len(s)

        # Precompute is_palindrome[i][j] using DP
        is_pal = [[False] * n for _ in range(n)]
        for i in range(n):
            is_pal[i][i] = True
        for i in range(n - 1):
            is_pal[i][i + 1] = (s[i] == s[i + 1])
        for length in range(3, n + 1):
            for i in range(n - length + 1):
                j = i + length - 1
                is_pal[i][j] = (s[i] == s[j] and is_pal[i + 1][j - 1])

        # Greedy DP: dp[i] = max palindromes using s[0..i-1]
        # dp[i] = max(dp[i-1],  # skip s[i-1]
        #             max over j<=i-k of (dp[j] + 1) if s[j..i-1] is palindrome of len>=k)
        dp = [0] * (n + 1)
        for i in range(1, n + 1):
            dp[i] = dp[i - 1]  # skip current position
            # Try all palindromes ending at i-1 with length >= k
            for j in range(i - k, -1, -1):
                # substring s[j..i-1], length = i-j >= k
                if is_pal[j][i - 1]:
                    dp[i] = max(dp[i], dp[j] + 1)
                    break  # Greedy: take the shortest valid palindrome ending here

        return dp[n]