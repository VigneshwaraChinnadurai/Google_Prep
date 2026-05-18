import collections
from typing import List

class Solution:
    def minJumps(self, arr: List[int]) -> int:
        n = len(arr)
        if n <= 1:
            return 0

        # Pre-process to find all indices with the same value
        value_to_indices = collections.defaultdict(list)
        for i, value in enumerate(arr):
            value_to_indices[value].append(i)

        # BFS state: (index, steps)
        queue = collections.deque([(0, 0)])
        # Visited set to avoid cycles and redundant work
        visited = {0}

        while queue:
            index, steps = queue.popleft()

            # Check if we reached the end
            if index == n - 1:
                return steps

            # Explore neighbors
            
            # 1. Jump to j where arr[i] == arr[j]
            current_value = arr[index]
            if current_value in value_to_indices:
                for same_value_index in value_to_indices[current_value]:
                    if same_value_index not in visited:
                        # The check `i != j` is implicitly handled because the current
                        # index is already in the visited set.
                        visited.add(same_value_index)
                        queue.append((same_value_index, steps + 1))
                
                # Crucial optimization: once we've added all nodes of a certain value
                # to the queue, we don't need to iterate through this list again.
                # This prevents Time Limit Exceeded on cases with many repeated numbers.
                del value_to_indices[current_value]

            # 2. Jump to i + 1
            next_index = index + 1
            if next_index < n and next_index not in visited:
                visited.add(next_index)
                queue.append((next_index, steps + 1))

            # 3. Jump to i - 1
            prev_index = index - 1
            if prev_index >= 0 and prev_index not in visited:
                visited.add(prev_index)
                queue.append((prev_index, steps + 1))
        
        return -1 # Should not be reached based on problem constraints