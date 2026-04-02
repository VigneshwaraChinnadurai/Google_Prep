import math

class Solution:
    def maximumAmount(self, coins: list[list[int]]) -> int:
        m, n = len(coins), len(coins[0])
        
        # dp[i][j][k] represents the maximum amount of money the robot can have
        # at cell (i, j) having used exactly k neutralizations.
        # k can be 0, 1, or 2.
        dp = [[[-math.inf] * 3 for _ in range(n)] for _ in range(m)]

        # Base case: Starting at (0, 0)
        val = coins[0][0]
        if val >= 0:
            # If the starting cell has non-negative coins, we gain them.
            # 0 neutralizations used.
            dp[0][0][0] = val
        else:
            # If the starting cell has a robber, we have two choices.
            # Choice 1: Don't neutralize. Lose coins. 0 neutralizations used.
            dp[0][0][0] = val
            # Choice 2: Neutralize. Lose 0 coins. 1 neutralization used.
            dp[0][0][1] = 0

        # Fill the DP table by iterating through each cell
        for i in range(m):
            for j in range(n):
                if i == 0 and j == 0:
                    continue

                # For each cell (i, j), the robot can arrive from the top (i-1, j)
                # or from the left (i, j-1). We take the path that yields the
                # maximum profit for each number of neutralizations.
                prev_max = [-math.inf] * 3
                if i > 0:
                    for k in range(3):
                        prev_max[k] = max(prev_max[k], dp[i-1][j][k])
                if j > 0:
                    for k in range(3):
                        prev_max[k] = max(prev_max[k], dp[i][j-1][k])

                current_coin = coins[i][j]
                
                if current_coin >= 0:
                    # If the cell has non-negative coins, we simply add them.
                    # The number of neutralizations used does not change.
                    for k in range(3):
                        if prev_max[k] != -math.inf:
                            dp[i][j][k] = prev_max[k] + current_coin
                else:  # Robber at (i, j)
                    # If the cell has a robber, we have two choices for each state k.
                    for k in range(3):
                        # Choice 1: Don't neutralize the robber at (i, j).
                        # This means we arrived at a predecessor with k neutralizations.
                        profit_no_neutralize = -math.inf
                        if prev_max[k] != -math.inf:
                            profit_no_neutralize = prev_max[k] + current_coin

                        # Choice 2: Neutralize the robber at (i, j).
                        # This uses one neutralization, so we must have arrived
                        # at a predecessor with k-1 neutralizations.
                        profit_neutralize = -math.inf
                        if k > 0 and prev_max[k-1] != -math.inf:
                            profit_neutralize = prev_max[k-1]  # Robbery is avoided, so add 0

                        dp[i][j][k] = max(profit_no_neutralize, profit_neutralize)

        # The final answer is the maximum profit at the destination (m-1, n-1)
        # across all possible numbers of neutralizations used (0, 1, or 2).
        result = max(dp[m-1][n-1])
        
        # The problem constraints guarantee a path exists, so result won't be -inf.
        return int(result)