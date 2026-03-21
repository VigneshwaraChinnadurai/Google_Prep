class Solution:
    def reverseSubmatrix(self, grid: List[List[int]], x: int, y: int, k: int) -> List[List[int]]:
        # Define the top and bottom row indices of the submatrix.
        top = x
        bottom = x + k - 1
        
        # Use a two-pointer approach to swap rows, moving from the outside in.
        # The loop continues as long as the top pointer is above the bottom pointer.
        while top < bottom:
            # The columns of the submatrix range from y to y + k - 1.
            # We swap the corresponding segments of the top and bottom rows.
            # Python's slice assignment is a concise way to perform this swap.
            grid[top][y:y+k], grid[bottom][y:y+k] = grid[bottom][y:y+k], grid[top][y:y+k]
            
            # Move the pointers towards the center for the next pair of rows.
            top += 1
            bottom -= 1
            
        # The grid is modified in-place, so we return the updated grid.
        return grid