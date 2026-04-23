import collections
import bisect
from typing import List

class Solution:
    def solveQueries(self, nums: List[int], queries: List[int]) -> List[int]:
        n = len(nums)
        
        # Pre-process to map each value to a sorted list of its indices.
        value_to_indices = collections.defaultdict(list)
        for i, num in enumerate(nums):
            value_to_indices[num].append(i)
            
        answer = []
        
        for q_idx in queries:
            val = nums[q_idx]
            indices = value_to_indices[val]
            
            # If the value appears only once, no other equal element exists.
            if len(indices) == 1:
                answer.append(-1)
                continue
            
            # Find the position of q_idx in the sorted list of indices using binary search.
            p = bisect.bisect_left(indices, q_idx)
            
            # Identify the predecessor and successor indices, handling circularity.
            # Python's `indices[p-1]` handles the wrap-around for p=0 (predecessor of first is last).
            prev_idx = indices[p - 1]
            # Modulo operator handles the wrap-around for the last element (successor of last is first).
            next_idx = indices[(p + 1) % len(indices)]
            
            # Calculate the linear distances to the two neighbors.
            dist_to_prev = abs(q_idx - prev_idx)
            dist_to_next = abs(q_idx - next_idx)
            
            # The minimum circular distance is the minimum of the four possible paths:
            # 1. Direct path to predecessor
            # 2. Wrapped path to predecessor
            # 3. Direct path to successor
            # 4. Wrapped path to successor
            min_dist = min(dist_to_prev, n - dist_to_prev, dist_to_next, n - dist_to_next)
            
            answer.append(min_dist)
            
        return answer