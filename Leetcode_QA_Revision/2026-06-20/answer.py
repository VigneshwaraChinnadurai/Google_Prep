import math
from typing import List

class Solution:
    def maxBuilding(self, n: int, restrictions: List[List[int]]) -> int:
        # Combine building 1 and restrictions into a single list of points.
        # A point is [id, maxHeight].
        # Building 1 has id=1 and height=0.
        points = [[1, 0]] + restrictions
        points.sort()

        # If building n is not in the list, add it. Its height is unconstrained
        # by a specific restriction, but is constrained by its distance from other points.
        # We can give it a large initial height (e.g., n-1, the max possible);
        # the passes will correct it to the tightest possible value.
        if points[-1][0] != n:
            points.append([n, n - 1])

        # --- Propagate constraints to find the tightest possible max height at each point ---

        # Forward pass (left to right):
        # The height of a building is limited by the height of the previous
        # special building plus their distance.
        # h[i] <= h[i-1] + (id[i] - id[i-1])
        for i in range(1, len(points)):
            points[i][1] = min(points[i][1], points[i-1][1] + (points[i][0] - points[i-1][0]))

        # Backward pass (right to left):
        # The height of a building is also limited by the height of the next
        # special building plus their distance.
        # h[i] <= h[i+1] + (id[i+1] - id[i])
        for i in range(len(points) - 2, -1, -1):
            points[i][1] = min(points[i][1], points[i+1][1] + (points[i+1][0] - points[i][0]))

        # --- Calculate the maximum height over all buildings ---

        max_height = 0
        # The maximum height can occur at one of the special points or in between them.
        # For each segment between two consecutive special points, the height profile
        # forms a "tent" shape. We calculate the peak of this tent.
        for i in range(len(points) - 1):
            id1, h1 = points[i]
            id2, h2 = points[i+1]
            
            # The distance between the two points
            dist = id2 - id1
            
            # The peak height in the segment [id1, id2] is achieved where the
            # upward slope from h1 meets the upward slope (going backward) from h2.
            # Let the peak height be H. The number of steps to reach H from h1 is (H - h1)
            # and from h2 is (H - h2). The total steps must be at most the distance.
            # (H - h1) + (H - h2) <= dist  =>  2*H <= dist + h1 + h2
            # H <= (dist + h1 + h2) / 2
            peak = (dist + h1 + h2) // 2
            max_height = max(max_height, peak)
            
        return max_height