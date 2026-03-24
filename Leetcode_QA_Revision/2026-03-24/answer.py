class Solution:
    def constructProductMatrix(self, grid: list[list[int]]) -> list[list[int]]:
        MOD = 12345
        n = len(grid)
        m = len(grid[0])

        # Initialize the product matrix p with 1s.
        # This matrix will first store prefix products, then be updated with suffix products.
        p = [[1] * m for _ in range(n)]

        # --- Forward Pass: Calculate Prefix Products ---
        # We iterate through the grid as if it were a flattened 1D array.
        # `prefix_prod` holds the product of all elements encountered so far.
        # For each cell (i, j), p[i][j] is set to the product of all elements
        # *before* grid[i][j]. Then, the running product is updated.
        prefix_prod = 1
        for i in range(n):
            for j in range(m):
                p[i][j] = prefix_prod
                prefix_prod = (prefix_prod * grid[i][j]) % MOD

        # --- Backward Pass: Calculate Suffix Products and Final Result ---
        # We iterate backward through the grid.
        # `suffix_prod` holds the product of all elements from the end.
        # For each cell (i, j), we multiply its current value in p (the prefix product)
        # by the `suffix_prod` (product of all elements *after* grid[i][j]).
        # This gives the final desired product. The running suffix product is then updated.
        suffix_prod = 1
        for i in range(n - 1, -1, -1):
            for j in range(m - 1, -1, -1):
                p[i][j] = (p[i][j] * suffix_prod) % MOD
                suffix_prod = (suffix_prod * grid[i][j]) % MOD
        
        return p