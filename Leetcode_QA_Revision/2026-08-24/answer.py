from typing import List

class Solution:
    def stoneGameVIII(self, stones: List[int]) -> int:
        n = len(stones)
        # Build prefix sums: prefix[i] = stones[0] + ... + stones[i]
        prefix = [0] * n
        prefix[0] = stones[0]
        for i in range(1, n):
            prefix[i] = prefix[i - 1] + stones[i]

        # Key insight: when a player picks x stones (removing leftmost x and
        # placing their sum back), the new stone has value prefix[x-1].
        # The NEXT player's effective game starts from index x (1-indexed pick
        # means the new leftmost stone is prefix[x-1], next pick must choose
        # >= 2 stones including this new one, so next cut point j > x-1).
        #
        # Let dp[i] = best score difference (current player - opponent) when
        # the current player must pick a cut point >= i (i.e. take prefix[i]
        # and leave the rest for the opponent to play from index i+1 onward).
        #
        # If current player cuts at index i (takes prefix[i]):
        #   score diff = prefix[i] - dp[i+1]  (current gets prefix[i], then
        #                                       opponent plays with dp[i+1])
        # dp[i] = max over j in [i, n-2] of (prefix[j] - dp[j+1])
        #       = max(prefix[i] - dp[i+1], dp[i+1 mapped back]...)
        #
        # Recurrence (right to left):
        #   dp[n-1] = prefix[n-1]           (forced: take everything)
        #   dp[i]   = max(prefix[i] - dp[i+1], dp[i+1])
        #           = max(prefix[i], dp[i+1]) - ... no, let's be careful:
        #   dp[i] = max(prefix[i] - dp[i+1],   <- cut here
        #               dp[i+1] applied as if we inherited it)  <- cut later
        # Actually: dp[i] = max(prefix[i] - dp[i+1], dp[i+1 considering skip])
        # The "skip" means the current player doesn't cut at i but defers to i+1:
        #   dp[i] = max(prefix[i] - dp[i+1], dp[i+1])
        # Wait — if we skip i, dp[i] = dp[i+1] (same current player now must
        # cut at i+1 or later)? No — skipping means we move to i+1, still the
        # SAME player choosing. So:
        #   dp[i] = max(prefix[i] - dp[i+1], dp[i+1])
        # Hmm that's not right either since dp[i+1] already encodes the best
        # choice from i+1 onward for the CURRENT player.
        #
        # Correct recurrence:
        #   dp[i] = max(prefix[i] - dp[i+1], dp[i+1 as inherited])
        # Let's define dp[i] = best (current - other) when current must pick
        # cut point in [i, n-2].
        #   dp[n-2] = prefix[n-1] - 0 ... no.
        #
        # Cleaner: dp[i] = max score diff for CURRENT player starting from cut i.
        # Base: dp[n-1] = prefix[n-1] (only one choice, take all, opponent gets 0 more)
        # dp[i] = max(prefix[i] - dp[i+1],   cut at i: gain prefix[i], lose dp[i+1]
        #             dp[i+1] ... no, "skip i" means current player cuts at some j>i,
        #             which is exactly dp[i+1] for the same current player.
        # So dp[i] = max(prefix[i] - dp[i+1], dp[i+1])
        # But note prefix[i] - dp[i+1] vs dp[i+1]:
        # = max(prefix[i] - dp[i+1], dp[i+1])

        # Alice must cut at some index >= 1 (x > 1 means at least 2 stones removed,
        # so cut index is at least 1, i.e. prefix[1..n-1]).
        # dp[i] for i in [1, n-1].
        # Base: dp[n-1] = prefix[n-1].
        # Transition: dp[i] = max(prefix[i] - dp[i+1], dp[i+1])
        # Answer: dp[1].

        dp = prefix[n - 1]
        for i in range(n - 2, 0, -1):
            dp = max(prefix[i] - dp, dp)

        return dp