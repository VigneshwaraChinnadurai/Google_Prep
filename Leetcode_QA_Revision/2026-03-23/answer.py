from typing import List

class Solution:
    def maxProductPath(self, grid: List[List[int]]) -> int:
        m = len(grid)
        n = len(grid[0])
        MOD = 10**9 + 7

        # dp_max[i][j] will store the maximum product to reach cell (i, j)
        # dp_min[i][j] will store the minimum product to reach cell (i, j)
        # We need to track both because a large negative product can become a 
        # large positive product when multiplied by another negative number.
        
        dp_max = [[0] * n for _ in range(m)]
        dp_min = [[0] * n for _ in range(m)]

        # Base case: starting cell (0, 0)
        dp_max[0][0] = grid[0][0]
        dp_min[0][0] = grid[0][0]

        # Fill the first row (only one path from the left)
        for j in range(1, n):
            product = dp_max[0][j-1] * grid[0][j]
            dp_max[0][j] = product
            dp_min[0][j] = product

        # Fill the first column (only one path from above)
        for i in range(1, m):
            product = dp_max[i-1][0] * grid[i][0]
            dp_max[i][0] = product
            dp_min[i][0] = product

        # Fill the rest of the DP tables
        for i in range(1, m):
            for j in range(1, n):
                val = grid[i][j]
                
                # Candidates for the new max/min product come from multiplying `val`
                # with the max/min products of the cells above and to the left.
                
                if val >= 0:
                    # If current value is non-negative:
                    # new max = max of previous maxes * val
                    # new min = min of previous mins * val
                    max_prev = max(dp_max[i-1][j], dp_max[i][j-1])
                    min_prev = min(dp_min[i-1][j], dp_min[i][j-1])
                    dp_max[i][j] = max_prev * val
                    dp_min[i][j] = min_prev * val
                else: # val < 0
                    # If current value is negative, the roles flip:
                    # new max = min of previous mins * val (e.g., -100 * -2 = 200)
                    # new min = max of previous maxes * val (e.g., 100 * -2 = -200)
                    max_prev = max(dp_max[i-1][j], dp_max[i][j-1])
                    min_prev = min(dp_min[i-1][j], dp_min[i][j-1])
                    dp_max[i][j] = min_prev * val
                    dp_min[i][j] = max_prev * val

        # The result is the maximum product at the bottom-right corner
        max_product = dp_max[m-1][n-1]

        # If the maximum possible product is negative, no non-negative path exists.
        if max_product < 0:
            return -1
        else:
            return max_product % MOD