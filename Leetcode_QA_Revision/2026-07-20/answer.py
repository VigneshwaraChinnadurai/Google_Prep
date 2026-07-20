class Solution:
    def shiftGrid(self, grid: list[list[int]], k: int) -> list[list[int]]:
        m = len(grid)
        n = len(grid[0])
        total_elements = m * n

        # The shift pattern repeats every 'total_elements' shifts.
        # We only need to consider the remainder of k divided by the total number of elements.
        effective_k = k % total_elements

        # 1. Flatten the 2D grid into a 1D list.
        # This is done by reading elements row by row.
        flat_list = [item for row in grid for item in row]

        # 2. Perform the circular shift on the 1D list.
        # A right shift of 'effective_k' positions means the last 'effective_k'
        # elements move to the front of the list.
        shifted_flat_list = flat_list[-effective_k:] + flat_list[:-effective_k]

        # 3. Reconstruct the 2D grid from the shifted 1D list.
        # We partition the 1D list into 'm' chunks of size 'n'.
        result_grid = [shifted_flat_list[i*n : (i+1)*n] for i in range(m)]
            
        return result_grid