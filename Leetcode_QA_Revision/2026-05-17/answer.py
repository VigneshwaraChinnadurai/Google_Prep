import collections

class Solution:
    def canReach(self, arr: list[int], start: int) -> bool:
        n = len(arr)
        
        queue = collections.deque([start])
        visited = {start}

        while queue:
            curr_idx = queue.popleft()

            if arr[curr_idx] == 0:
                return True

            jump = arr[curr_idx]
            
            # Explore neighbors: forward and backward jumps
            for next_idx in [curr_idx + jump, curr_idx - jump]:
                if 0 <= next_idx < n and next_idx not in visited:
                    visited.add(next_idx)
                    queue.append(next_idx)
        
        return False