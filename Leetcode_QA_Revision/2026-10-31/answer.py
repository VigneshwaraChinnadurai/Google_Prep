class Solution:
    def uniquePaths(self, grid: List[List[int]]) -> int:
        MOD = 10**9 + 7
        m, n = len(grid), len(grid[0])
        # A[i][j]: ways of entering cell (i,j) while moving right
        # B[i][j]: ways of entering cell (i,j) while moving down
        A = [[0] * (n + 1) for _ in range(m + 1)]
        B = [[0] * (n + 1) for _ in range(m + 1)]
        A[0][0] = 1
        for i in range(m):
            for j in range(n):
                a = A[i][j]
                b = B[i][j]
                if grid[i][j] == 1:
                    # entered moving right -> turned down, lands in (i+1, j)
                    B[i + 1][j] = (B[i + 1][j] + a) % MOD
                    # entered moving down -> turned right, lands in (i, j+1)
                    A[i][j + 1] = (A[i][j + 1] + b) % MOD
                else:
                    t = (a + b) % MOD
                    A[i][j + 1] = (A[i][j + 1] + t) % MOD
                    B[i + 1][j] = (B[i + 1][j] + t) % MOD
        return (A[m - 1][n - 1] + B[m - 1][n - 1]) % MOD