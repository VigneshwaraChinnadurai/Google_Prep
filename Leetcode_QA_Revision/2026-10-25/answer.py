class Solution:
    def closedIsland(self, grid: list[list[int]]) -> int:
        m, n = len(grid), len(grid[0])
        g = [row[:] for row in grid]

        def flood(sr, sc):
            stack = [(sr, sc)]
            g[sr][sc] = 1
            touches_border = False
            while stack:
                r, c = stack.pop()
                if r == 0 or c == 0 or r == m - 1 or c == n - 1:
                    touches_border = True
                for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nr, nc = r + dr, c + dc
                    if 0 <= nr < m and 0 <= nc < n and g[nr][nc] == 0:
                        g[nr][nc] = 1
                        stack.append((nr, nc))
            return not touches_border

        count = 0
        for i in range(m):
            for j in range(n):
                if g[i][j] == 0:
                    if flood(i, j):
                        count += 1
        return count