class Solution:
    def numberOfSubmatrices(self, grid: List[List[str]]) -> int:
        rows = len(grid)
        if rows == 0:
            return 0
        cols = len(grid[0])
        if cols == 0:
            return 0

        count = 0
        # ps_x[c] stores the total count of 'X's in the submatrix from (0,0) to (r-1, c).
        # As we process row r, we update it to store counts for the submatrix from (0,0) to (r, c).
        ps_x = [0] * cols
        ps_y = [0] * cols

        for r in range(rows):
            # These store the prefix sum of 'X' and 'Y' for the current row `r`
            row_x_sum = 0
            row_y_sum = 0
            for c in range(cols):
                if grid[r][c] == 'X':
                    row_x_sum += 1
                elif grid[r][c] == 'Y':
                    row_y_sum += 1
                
                # Total sum for submatrix (0,0) to (r,c) is:
                # sum for (0,0) to (r-1, c) + sum for row r from 0 to c
                total_x = ps_x[c] + row_x_sum
                total_y = ps_y[c] + row_y_sum
                
                # Update the prefix sum arrays for the next row's calculation
                ps_x[c] = total_x
                ps_y[c] = total_y
                
                # Check conditions: equal frequency and at least one 'X'
                if total_x > 0 and total_x == total_y:
                    count += 1
        
        return count