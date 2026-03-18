class Solution:
    def countSubmatrices(self, grid: List[List[int]], k: int) -> int:
        m, n = len(grid), len(grid[0])
        count = 0
        
        # We will use the grid itself to store the prefix sums to save space.
        # After the update, grid[r][c] will hold the sum of the submatrix
        # with top-left corner (0, 0) and bottom-right corner (r, c).
        
        for r in range(m):
            for c in range(n):
                # Calculate the prefix sum for the current cell (r, c)
                # using the standard recurrence relation. The values from
                # previous cells (grid[r-1][c], grid[r][c-1], grid[r-1][c-1])
                # have already been converted to their prefix sum equivalents.
                
                # Add sum from the submatrix ending at the cell above.
                if r > 0:
                    grid[r][c] += grid[r-1][c]
                
                # Add sum from the submatrix ending at the cell to the left.
                if c > 0:
                    grid[r][c] += grid[r][c-1]
                
                # Subtract the sum of the top-left diagonal submatrix,
                # as it was included twice (once from above, once from left).
                if r > 0 and c > 0:
                    grid[r][c] -= grid[r-1][c-1]
                
                # Now, grid[r][c] is the sum of the submatrix from (0,0) to (r,c).
                # Check if this sum is within the given limit k.
                if grid[r][c] <= k:
                    count += 1
                else:
                    # Optimization: Since all original grid values are non-negative,
                    # the prefix sums in any given row are non-decreasing.
                    # That is, prefix_sum(r, c) <= prefix_sum(r, c+1).
                    # If the sum at grid[r][c] exceeds k, all subsequent sums
                    # in the same row (grid[r][c+1], grid[r][c+2], ...) will also exceed k.
                    # Therefore, we can break the inner loop and move to the next row.
                    break
                    
        return count