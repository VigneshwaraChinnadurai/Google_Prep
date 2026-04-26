import collections

class Solution:
    def containsCycle(self, grid: list[list[str]]) -> bool:
        m, n = len(grid), len(grid[0])
        visited = [[False for _ in range(n)] for _ in range(m)]

        for r in range(m):
            for c in range(n):
                if not visited[r][c]:
                    char_to_find = grid[r][c]
                    
                    # Start BFS for this component
                    q = collections.deque([(r, c, -1, -1)])  # (row, col, parent_row, parent_col)
                    visited[r][c] = True

                    while q:
                        curr_r, curr_c, prev_r, prev_c = q.popleft()

                        # Explore neighbors
                        for dr, dc in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
                            next_r, next_c = curr_r + dr, curr_c + dc

                            # Check if the neighbor is within bounds
                            if not (0 <= next_r < m and 0 <= next_c < n):
                                continue
                            
                            # Check if the neighbor has the same character
                            if grid[next_r][next_c] != char_to_find:
                                continue

                            # We cannot immediately go back to the cell we came from
                            if next_r == prev_r and next_c == prev_c:
                                continue

                            # If we encounter a cell that has already been visited in this
                            # traversal, we have found a cycle.
                            if visited[next_r][next_c]:
                                return True
                            
                            visited[next_r][next_c] = True
                            q.append((next_r, next_c, curr_r, curr_c))
        
        return False