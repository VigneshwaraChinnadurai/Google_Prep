class Solution:
    def maxPathScore(self, grid: list[list[int]], k: int) -> int:
        m, n = len(grid), len(grid[0])
        
        # Pre-calculate score and cost for each cell value for efficiency
        score_map = {0: 0, 1: 1, 2: 2}
        cost_map = {0: 0, 1: 1, 2: 1}

        # dp[j][c] represents the maximum score to reach the cell in the current row `i`
        # at column `j` with a total cost of `c`.
        # We use a single 2D array of size n x (k+1) and update it row by row.
        # This is a space optimization from a 3D DP table of size m x n x (k+1).
        # When calculating for cell (i, j):
        # - dp[j] still holds the values from the cell above, (i-1, j).
        # - dp[j-1] has been updated with values for the cell to the left, (i, j-1).
        dp = [[-1] * (k + 1) for _ in range(n)]

        # Base case: starting at (0, 0).
        # The problem guarantees grid[0][0] == 0, so the initial score is 0 and cost is 0.
        dp[0][0] = 0

        # Iterate through the grid to fill the DP table.
        for i in range(m):
            for j in range(n):
                # The starting cell (0,0) is our base case and is already initialized.
                if i == 0 and j == 0:
                    continue

                cell_val = grid[i][j]
                cell_score = score_map[cell_val]
                cell_cost = cost_map[cell_val]
                
                # This will store the new DP values for the current cell (i, j).
                # It's initialized to -1 to indicate unreachable states.
                current_cell_dp = [-1] * (k + 1)

                # Calculate max score coming from the cell above (i-1, j).
                if i > 0:
                    for c in range(cell_cost, k + 1):
                        prev_c = c - cell_cost
                        # Check if the previous state is reachable
                        if dp[j][prev_c] != -1:
                            score = dp[j][prev_c] + cell_score
                            current_cell_dp[c] = max(current_cell_dp[c], score)
                
                # Calculate max score coming from the cell to the left (i, j-1).
                if j > 0:
                    for c in range(cell_cost, k + 1):
                        prev_c = c - cell_cost
                        # Check if the previous state is reachable
                        if dp[j-1][prev_c] != -1:
                            score = dp[j-1][prev_c] + cell_score
                            current_cell_dp[c] = max(current_cell_dp[c], score)
                
                # Update the DP table for the current column `j` with the new values.
                dp[j] = current_cell_dp
    
        # The result is the maximum score to reach the bottom-right cell (m-1, n-1)
        # with any cost up to k. If no path is valid, all entries in dp[n-1] will be -1,
        # and max() will correctly return -1.
        max_score = max(dp[n-1])
        
        return max_score