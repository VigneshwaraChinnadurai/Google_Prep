from typing import List

class Solution:
    def findRotation(self, mat: List[List[int]], target: List[List[int]]) -> bool:
        n = len(mat)
        
        # We check up to 4 rotations (0, 90, 180, 270 degrees).
        for _ in range(4):
            # Check if the current state of mat matches target.
            if mat == target:
                return True
            
            # If not, rotate mat 90 degrees clockwise in-place for the next check.
            # This is done in two steps:
            # 1. Transpose the matrix.
            # 2. Reverse each row.
            
            # Step 1: Transpose
            for i in range(n):
                for j in range(i + 1, n):
                    mat[i][j], mat[j][i] = mat[j][i], mat[i][j]
            
            # Step 2: Reverse each row
            for i in range(n):
                mat[i].reverse()
                
        # If no rotation matches after 4 attempts, it's impossible.
        return False