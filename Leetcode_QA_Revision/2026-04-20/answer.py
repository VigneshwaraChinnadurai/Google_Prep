class Solution:
    def maxDistance(self, colors: List[int]) -> int:
        n = len(colors)
        
        # The optimal pair of houses must include one of the endpoints (index 0 or n-1).
        # This is because for any pair of interior houses, a larger or equal distance
        # can be found by pairing one of them with an endpoint.
        # We calculate the maximum possible distance from each endpoint.

        # 1. Max distance from the start (index 0):
        # Find the rightmost house `j` with a color different from colors[0].
        # The distance is j - 0.
        j = n - 1
        while colors[j] == colors[0]:
            j -= 1
        dist_from_start = j

        # 2. Max distance from the end (index n-1):
        # Find the leftmost house `i` with a color different from colors[n-1].
        # The distance is (n - 1) - i.
        i = 0
        while colors[i] == colors[n - 1]:
            i += 1
        dist_from_end = (n - 1) - i

        # The answer is the maximum of these two possibilities.
        # This logic works for both cases:
        # - If colors[0] != colors[n-1], then i=0 and j=n-1, giving n-1.
        # - If colors[0] == colors[n-1], it finds the furthest different house from each end.
        return max(dist_from_start, dist_from_end)