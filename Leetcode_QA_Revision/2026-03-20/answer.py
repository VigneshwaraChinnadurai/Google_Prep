import collections
from typing import List

class Solution:
    def minAbsDiff(self, grid: List[List[int]], k: int) -> List[List[int]]:
        m, n = len(grid), len(grid[0])
        ans_rows = m - k + 1
        ans_cols = n - k + 1
        ans = [[0] * ans_cols for _ in range(ans_rows)]

        def calculate_min_diff(counts: collections.Counter) -> int:
            # If there are fewer than two distinct numbers, the difference is 0.
            if len(counts) < 2:
                return 0
            
            # Get the unique elements and sort them. The minimum difference in a set
            # of numbers will always be between two consecutive numbers in its sorted version.
            sorted_keys = sorted(counts.keys())
            
            min_d = float('inf')
            for i in range(len(sorted_keys) - 1):
                diff = sorted_keys[i+1] - sorted_keys[i]
                min_d = min(min_d, diff)
            return min_d

        # We process the grid in horizontal strips of height k.
        # For each strip, we use a sliding window of width k.
        for r_start in range(ans_rows):
            # This counter will hold the frequency of numbers in the current k x k window.
            counts = collections.Counter()
            
            # 1. Initialize the counter for the first window in this strip (top-left at r_start, 0).
            for r in range(r_start, r_start + k):
                for c in range(k):
                    counts[grid[r][c]] += 1
            
            ans[r_start][0] = calculate_min_diff(counts)

            # 2. Slide the window horizontally from left to right.
            for c_start in range(1, ans_cols):
                # The new window has its top-left corner at (r_start, c_start).
                
                # Remove the leftmost column of the previous window.
                # This is the column at index c_start - 1.
                leaving_col_idx = c_start - 1
                for r in range(r_start, r_start + k):
                    val = grid[r][leaving_col_idx]
                    counts[val] -= 1
                    if counts[val] == 0:
                        del counts[val]
                
                # Add the rightmost column of the new window.
                # This is the column at index c_start + k - 1.
                entering_col_idx = c_start + k - 1
                for r in range(r_start, r_start + k):
                    val = grid[r][entering_col_idx]
                    counts[val] += 1
                
                ans[r_start][c_start] = calculate_min_diff(counts)
                
        return ans