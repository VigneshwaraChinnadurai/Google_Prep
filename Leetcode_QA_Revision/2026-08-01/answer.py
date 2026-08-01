class Solution:
    def predictTheWinner(self, nums: list[int]) -> bool:
        """
        This problem can be solved using dynamic programming, based on game theory principles (minimax).
        We define dp[i][j] as the maximum score difference the current player can achieve over the
        other player on the subarray nums[i...j].

        The recurrence relation is as follows:
        When it's a player's turn for the subarray nums[i...j], they have two choices:
        1. Take nums[i]: Their score increases by nums[i]. The game continues on nums[i+1...j],
           and it's the other player's turn. The other player will play optimally and achieve a
           score difference of dp[i+1][j] over the current player. So, the net score difference
           for the current player from this move is nums[i] - dp[i+1][j].
        2. Take nums[j]: Similarly, the net score difference is nums[j] - dp[i][j-1].

        The player will choose the move that maximizes their score difference.
        So, dp[i][j] = max(nums[i] - dp[i+1][j], nums[j] - dp[i][j-1]).

        The base case is when i == j, dp[i][i] = nums[i].

        We can solve this using a 2D DP table. However, space can be optimized to O(n)
        because to compute a row `i` of the DP table, we only need the values from row `i+1`.
        By iterating `i` backwards from n-1 to 0, we can use a 1D array.
        """
        n = len(nums)
        if n == 1:
            return True

        # dp[j] will store the max score difference for a subarray starting at `i` and ending at `j`.
        dp = [0] * n

        # i is the start of the subarray, iterating backwards
        for i in range(n - 1, -1, -1):
            # Base case for a subarray of length 1 (i.e., nums[i])
            dp[i] = nums[i]
            # j is the end of the subarray, iterating forwards
            for j in range(i + 1, n):
                # In the 1D array:
                # dp[j] on the right side is from the previous i-iteration (representing dp[i+1][j])
                # dp[j-1] on the right side is from the current i-iteration (representing dp[i][j-1])
                take_i = nums[i] - dp[j]
                take_j = nums[j] - dp[j - 1]
                dp[j] = max(take_i, take_j)

        # After the loops, dp[n-1] holds the result for the entire array nums[0...n-1].
        # This is the score difference for Player 1. Player 1 wins if it's non-negative.
        return dp[n - 1] >= 0