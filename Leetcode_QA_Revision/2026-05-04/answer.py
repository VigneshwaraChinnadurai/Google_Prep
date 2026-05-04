class Solution:
    def rotate(self, matrix: list[list[int]]) -> None:
        """
        Do not return anything, modify matrix in-place instead.
        """
        n = len(matrix)

        # Step 1: Transpose the matrix in-place.
        # This swaps elements across the main diagonal (top-left to bottom-right).
        # An element at (i, j) moves to (j, i).
        for i in range(n):
            # We only need to iterate through the upper triangle of the matrix.
            # j starts from i + 1 to avoid swapping elements on the diagonal
            # with themselves and to avoid swapping other elements twice.
            for j in range(i + 1, n):
                matrix[i][j], matrix[j][i] = matrix[j][i], matrix[i][j]

        # Step 2: Reverse each row.
        # After transposing, reversing each row completes the 90-degree clockwise rotation.
        for i in range(n):
            matrix[i].reverse()