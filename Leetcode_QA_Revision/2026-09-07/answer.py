class Solution:
    def distinctSubseqII(self, s: str) -> int:
        MOD = 10**9 + 7
        # dp[c] = number of distinct subsequences ending with character c
        dp = [0] * 26

        for ch in s:
            c = ord(ch) - ord('a')
            # Appending ch to every existing subsequence (including empty)
            # creates sum(dp)+1 new subsequences ending in ch.
            # This replaces any previous count for dp[c] to avoid duplicates.
            dp[c] = (sum(dp) + 1) % MOD

        return sum(dp) % MOD