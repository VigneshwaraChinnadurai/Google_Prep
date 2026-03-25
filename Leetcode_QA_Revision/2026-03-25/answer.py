class Solution:
    def canPartitionGrid(self, grid: list[list[int]]) -> bool:
        m = len(grid)
        n = len(grid[0])

        # Calculate row sums and then the total sum from row sums.
        row_sums = [sum(row) for row in grid]
        total_sum = sum(row_sums)

        # If the total sum is odd, it's impossible to partition it into two equal integer sums.
        if total_sum % 2 != 0:
            return False
        
        target_sum = total_sum // 2

        # Check for a valid horizontal cut.
        # We iterate through the m-1 possible horizontal cut locations (after each row).
        # A running prefix sum of row_sums gives the sum of the top partition.
        current_sum = 0
        for i in range(m - 1):
            current_sum += row_sums[i]
            if current_sum == target_sum:
                return True

        # Check for a valid vertical cut.
        # First, calculate column sums. zip(*grid) is a Pythonic way to transpose the grid.
        col_sums = [sum(col) for col in zip(*grid)]
        
        # We iterate through the n-1 possible vertical cut locations (after each column).
        # A running prefix sum of col_sums gives the sum of the left partition.
        current_sum = 0
        for j in range(n - 1):
            current_sum += col_sums[j]
            if current_sum == target_sum:
                return True
        
        # If no valid horizontal or vertical cut is found.
        return False