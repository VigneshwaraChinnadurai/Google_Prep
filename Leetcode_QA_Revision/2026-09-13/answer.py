from typing import List
from collections import defaultdict

class Solution:
    def largestOverlap(self, img1: List[List[int]], img2: List[List[int]]) -> int:
        n = len(img1)
        # Extract positions of 1s in each image
        ones1 = [(r, c) for r in range(n) for c in range(n) if img1[r][c]]
        ones2 = [(r, c) for r in range(n) for c in range(n) if img2[r][c]]

        if not ones1 or not ones2:
            return 0

        # For each pair (p1 in ones1, p2 in ones2), the translation vector is (p2-p1).
        # Count how many pairs share the same translation vector.
        count = defaultdict(int)
        for r1, c1 in ones1:
            for r2, c2 in ones2:
                count[(r2 - r1, c2 - c1)] += 1

        return max(count.values())