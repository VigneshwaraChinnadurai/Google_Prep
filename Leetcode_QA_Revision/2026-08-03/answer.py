import math

class Solution:
    def stoneGameIII(self, stoneValue: list[int]) -> str:
        n = len(stoneValue)
        
        # We use dynamic programming. Let dp[i] be the maximum score difference
        # the current player can achieve over the other player from the subarray
        # stoneValue[i:].
        
        # We can solve this with a bottom-up approach. Notice that dp[i] only depends on
        # dp[i+1], dp[i+2], and dp[i+3]. This allows for a space optimization to O(1).
        
        # dp1, dp2, dp3 will store the values for dp[i+1], dp[i+2], and dp[i+3] respectively.
        # Base cases: beyond the end of the array, the score difference is 0.
        dp1, dp2, dp3 = 0, 0, 0
        
        # Iterate backwards from the end of the array to compute dp values.
        for i in range(n - 1, -1, -1):
            # Calculate dp[i] based on the player's three choices.
            
            # Option 1: Take 1 stone
            # Score diff = stoneValue[i] - dp[i+1]
            take1 = stoneValue[i] - dp1
            
            # Option 2: Take 2 stones
            take2 = -math.inf
            if i + 1 < n:
                take2 = stoneValue[i] + stoneValue[i+1] - dp2
            
            # Option 3: Take 3 stones
            take3 = -math.inf
            if i + 2 < n:
                take3 = stoneValue[i] + stoneValue[i+1] + stoneValue[i+2] - dp3
            
            # dp[i] is the maximum of the possible outcomes
            current_dp = max(take1, take2, take3)
            
            # Update the sliding window of dp values for the next iteration (i-1)
            dp3, dp2, dp1 = dp2, dp1, current_dp
            
        # After the loop, dp1 holds the value for dp[0].
        # This is the score difference Alice (the first player) can achieve.
        score_diff = dp1
        
        if score_diff > 0:
            return "Alice"
        elif score_diff < 0:
            return "Bob"
        else:
            return "Tie"