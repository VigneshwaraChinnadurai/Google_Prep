import collections
from typing import List

class Solution:
    def distance(self, nums: List[int]) -> List[int]:
        n = len(nums)
        arr = [0] * n
        
        # This problem can be solved efficiently using a two-pass approach combined
        # with prefix/suffix sums logic. For each index `i`, the total distance is
        # the sum of distances to identical elements on its left and on its right.
        
        # Pass 1: Left-to-right
        # For each index i, we calculate the sum of distances to all identical elements
        # on its left. The formula for this "left sum" is:
        # sum(|i - j|) for j < i where nums[j] == nums[i]
        # = sum(i - j) since i > j
        # = (count of such j's) * i - (sum of such j's)
        
        # `count` stores the number of occurrences of a value seen so far.
        # `index_sum` stores the sum of indices for a value seen so far.
        count = collections.defaultdict(int)
        index_sum = collections.defaultdict(int)
        
        for i, v in enumerate(nums):
            # Add the contribution from elements to the left
            arr[i] += count[v] * i - index_sum[v]
            
            # Update state for value v for future elements
            count[v] += 1
            index_sum[v] += i
            
        # Pass 2: Right-to-left
        # For each index i, we calculate the sum of distances to all identical elements
        # on its right. The formula for this "right sum" is:
        # sum(|i - j|) for j > i where nums[j] == nums[i]
        # = sum(j - i) since j > i
        # = (sum of such j's) - (count of such j's) * i
        
        # Reset maps for the second pass
        count.clear()
        index_sum.clear()
        
        for i in range(n - 1, -1, -1):
            v = nums[i]
            
            # Add the contribution from elements to the right
            arr[i] += index_sum[v] - count[v] * i
            
            # Update state for value v for future elements (in reverse)
            count[v] += 1
            index_sum[v] += i
            
        return arr