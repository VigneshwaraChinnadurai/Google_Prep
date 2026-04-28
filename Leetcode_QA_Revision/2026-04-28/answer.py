import collections
from typing import List

class Solution:
    def hasValidPath(self, grid: List[List[int]]) -> bool:
        m, n = len(grid), len(grid[0])

        # Each street type maps to a set of possible moves (dr, dc).
        # Using sets for O(1) lookup.
        # Directions: (dr, dc)
        # 1: left-right, 2: up-down, 3: left-down, 4: right-down, 5: left-up, 6: right-up
        moves = {
            1: {(0, -1), (0, 1)},  # Left, Right
            2: {(-1, 0), (1, 0)},  # Up, Down
            3: {(0, -1), (1, 0)},  # Left, Down
            4: {(0, 1), (1, 0)},   # Right, Down
            5: {(0, -1), (-1, 0)}, # Left, Up
            6: {(0, 1), (-1, 0)}   # Right, Up
        }

        # BFS initialization
        q = collections.deque([(0, 0)])
        visited = {(0, 0)}

        while q:
            r, c = q.popleft()

            # Check if we reached the destination
            if r == m - 1 and c == n - 1:
                return True

            current_street_type = grid[r][c]
            
            # Explore neighbors based on the current street's connections
            for dr, dc in moves[current_street_type]:
                nr, nc = r + dr, c + dc

                # Check if the neighbor is within bounds and not visited
                if 0 <= nr < m and 0 <= nc < n and (nr, nc) not in visited:
                    neighbor_street_type = grid[nr][nc]
                    
                    # The crucial check: the neighbor street must have a port
                    # that connects back to the current cell.
                    # The direction from neighbor to current is (-dr, -dc).
                    if (-dr, -dc) in moves[neighbor_street_type]:
                        visited.add((nr, nc))
                        q.append((nr, nc))
        
        # If the queue becomes empty and we haven't reached the destination
        return False