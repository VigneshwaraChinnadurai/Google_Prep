class Solution:
    def rotateTheBox(self, boxGrid: List[List[str]]) -> List[List[str]]:
        m = len(boxGrid)
        n = len(boxGrid[0])

        # Step 1: Apply gravity to each row.
        # In the initial side-view, gravity pulls stones to the right.
        # We process each row independently to settle the stones.
        for r in range(m):
            # `write_pos` tracks the rightmost available position for a stone to settle.
            write_pos = n - 1
            # Iterate from right to left through the row.
            for c in range(n - 1, -1, -1):
                if boxGrid[r][c] == '*':
                    # An obstacle acts as a new "floor". Reset the settling position
                    # to be just to the left of the obstacle.
                    write_pos = c - 1
                elif boxGrid[r][c] == '#':
                    # A stone is found. Move it to the rightmost available spot.
                    # A swap works because `write_pos` will always point to an empty spot
                    # or the current stone's position itself. This moves the stone to
                    # `write_pos` and effectively leaves an empty spot at `c`.
                    boxGrid[r][c], boxGrid[r][write_pos] = boxGrid[r][write_pos], boxGrid[r][c]
                    # The next stone will settle to the left of the current one.
                    write_pos -= 1

        # Step 2: Rotate the grid 90 degrees clockwise.
        # The grid dimensions change from m x n to n x m.
        # An element at (r, c) in the original grid moves to (c, m - 1 - r) in the new grid.
        res = [['' for _ in range(m)] for _ in range(n)]
        for r in range(m):
            for c in range(n):
                res[c][m - 1 - r] = boxGrid[r][c]
        
        return res