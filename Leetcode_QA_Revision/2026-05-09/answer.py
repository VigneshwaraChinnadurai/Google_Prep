class Solution:
    def rotateGrid(self, grid: list[list[int]], k: int) -> list[list[int]]:
        m, n = len(grid), len(grid[0])
        
        top, bottom = 0, m - 1
        left, right = 0, n - 1
        
        while top < bottom and left < right:
            # Calculate the number of elements in the current layer's perimeter
            perimeter = 2 * (bottom - top) + 2 * (right - left)
            
            # Calculate the effective number of rotations
            # Rotations are cyclic, so k rotations are equivalent to k % perimeter rotations.
            k_eff = k % perimeter
            
            if k_eff > 0:
                # 1. Extract the current layer into a 1D list (unroll)
                layer_elements = []
                # Top row (left to right)
                for c in range(left, right):
                    layer_elements.append(grid[top][c])
                # Right col (top to bottom)
                for r in range(top, bottom):
                    layer_elements.append(grid[r][right])
                # Bottom row (right to left)
                for c in range(right, left, -1):
                    layer_elements.append(grid[bottom][c])
                # Left col (bottom to top)
                for r in range(bottom, top, -1):
                    layer_elements.append(grid[r][left])

                # 2. Rotate the 1D list
                # A counter-clockwise rotation in the grid is a left shift in the unrolled list.
                rotated_layer = layer_elements[k_eff:] + layer_elements[:k_eff]
                
                # 3. Place the rotated elements back into the grid (roll back)
                idx = 0
                # Top row
                for c in range(left, right):
                    grid[top][c] = rotated_layer[idx]
                    idx += 1
                # Right col
                for r in range(top, bottom):
                    grid[r][right] = rotated_layer[idx]
                    idx += 1
                # Bottom row
                for c in range(right, left, -1):
                    grid[bottom][c] = rotated_layer[idx]
                    idx += 1
                # Left col
                for r in range(bottom, top, -1):
                    grid[r][left] = rotated_layer[idx]
                    idx += 1

            # Move to the next inner layer by shrinking the boundaries
            top += 1
            bottom -= 1
            left += 1
            right -= 1
            
        return grid