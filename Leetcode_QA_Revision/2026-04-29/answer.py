import math

class Solution:
    def maximumScore(self, grid: list[list[int]]) -> int:
        n = len(grid)
        
        # Precompute column prefix sums
        # col_prefix_sum[c][r] = sum of grid[i][c] for i from 0 to r-1
        col_prefix_sum = [[0] * (n + 1) for _ in range(n)]
        for c in range(n):
            for r in range(n):
                col_prefix_sum[c][r + 1] = col_prefix_sum[c][r] + grid[r][c]
        
        # DP state: dp_prev[k] is the maximum score considering columns 0..j-1,
        # where column j-1 is colored black down to row k-1.
        # k=0 means colored to row -1 (all white). k=r+1 means colored to row r.
        dp_prev = [0] * (n + 1)
        
        # Iterate through columns from left to right, starting from the second column
        for j in range(1, n):
            dp_curr = [0] * (n + 1)
            
            # Optimization helper arrays for O(1) transition
            # Case p < k: Maximize dp_prev[p] - col_prefix_sum[j-1][p]
            prefix_max_term = [-math.inf] * (n + 1)
            current_max = -math.inf
            for p in range(n + 1):
                val = dp_prev[p] - col_prefix_sum[j-1][p]
                current_max = max(current_max, val)
                prefix_max_term[p] = current_max

            # Case p > k: Maximize dp_prev[p] + col_prefix_sum[j][p]
            suffix_max_term = [-math.inf] * (n + 2) # Size n+2 for easier indexing [k+1]
            for p in range(n, -1, -1):
                val = dp_prev[p] + col_prefix_sum[j][p]
                suffix_max_term[p] = max(suffix_max_term[p+1], val)

            # Compute dp_curr[k] for each possible choice k for the current column j
            for k in range(n + 1):
                # Contribution from p < k
                val1 = -math.inf
                if k > 0:
                    val1 = col_prefix_sum[j-1][k] + prefix_max_term[k-1]
                
                # Contribution from p == k (score is 0)
                val2 = dp_prev[k]
                
                # Contribution from p > k
                val3 = -col_prefix_sum[j][k] + suffix_max_term[k+1]
                
                dp_curr[k] = max(val1, val2, val3)
            
            dp_prev = dp_curr
            
        return max(dp_prev)