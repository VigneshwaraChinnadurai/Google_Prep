from typing import List
from collections import deque

class Solution:
    def minMoves(self, classroom: List[str], energy: int) -> int:
        m, n = len(classroom), len(classroom[0])

        # Identify key cells
        litter_cells = []
        start = None
        for r in range(m):
            for c in range(n):
                ch = classroom[r][c]
                if ch == 'S':
                    start = (r, c)
                elif ch == 'L':
                    litter_cells.append((r, c))

        num_litter = len(litter_cells)
        full_mask = (1 << num_litter) - 1
        litter_index = {cell: i for i, cell in enumerate(litter_cells)}

        if num_litter == 0:
            return 0

        # BFS: state = (row, col, litter_mask, energy_remaining)
        # visited[r][c][mask][e] to avoid revisiting
        visited = [[[[False] * (energy + 1) for _ in range(1 << num_litter)]
                    for _ in range(n)] for _ in range(m)]

        sr, sc = start
        init_mask = 0
        if (sr, sc) in litter_index:
            init_mask = 1 << litter_index[(sr, sc)]

        visited[sr][sc][init_mask][energy] = True
        # deque entries: (moves, row, col, mask, energy_left)
        q = deque()
        q.append((0, sr, sc, init_mask, energy))

        dirs = [(-1,0),(1,0),(0,-1),(0,1)]

        while q:
            moves, r, c, mask, e = q.popleft()

            for dr, dc in dirs:
                nr, nc = r + dr, c + dc
                if not (0 <= nr < m and 0 <= nc < n):
                    continue
                if classroom[nr][nc] == 'X':
                    continue
                if e <= 0:
                    continue

                ne = e - 1
                cell_type = classroom[nr][nc]

                # Reset energy if on R
                if cell_type == 'R':
                    ne = energy

                # Collect litter if on L
                nmask = mask
                if cell_type == 'L' and (nr, nc) in litter_index:
                    nmask = mask | (1 << litter_index[(nr, nc)])

                if nmask == full_mask:
                    return moves + 1

                if not visited[nr][nc][nmask][ne]:
                    visited[nr][nc][nmask][ne] = True
                    q.append((moves + 1, nr, nc, nmask, ne))

        return -1