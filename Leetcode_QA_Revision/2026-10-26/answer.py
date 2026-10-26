class Solution:
    def countIslands(self, grid: List[List[int]], k: int) -> int:
        m, n = len(grid), len(grid[0])
        g = [row[:] for row in grid]
        count = 0
        for i in range(m):
            for j in range(n):
                if g[i][j] > 0:
                    total = 0
                    stack = [(i, j)]
                    total += g[i][j]
                    g[i][j] = 0
                    while stack:
                        r, c = stack.pop()
                        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                            nr, nc = r + dr, c + dc
                            if 0 <= nr < m and 0 <= nc < n and g[nr][nc] > 0:
                                total += g[nr][nc]
                                g[nr][nc] = 0
                                stack.append((nr, nc))
                    if total % k == 0:
                        count += 1
        return count