class Solution:
    def maxJumps(self, arr: list[int], d: int) -> int:
        n = len(arr)
        # dp[i] stores the maximum number of indices you can visit starting from index i.
        # We use a value of 0 to indicate that the result for an index has not been computed yet.
        # Since the minimum number of visited indices is 1 (the starting index itself),
        # a computed value will always be > 0.
        dp = [0] * n

        def solve(i: int) -> int:
            # If the result for index i is already computed, return it from the cache.
            if dp[i] != 0:
                return dp[i]

            # Initialize the result for the current path. A path always includes the starting index.
            res = 1

            # Explore jumps to the right from index i.
            # The loop goes from i+1 up to a distance of d, staying within array bounds.
            for j in range(i + 1, min(i + d + 1, n)):
                # Jump condition: arr[i] > arr[k] for all k between i and j.
                # This is equivalent to saying there's no "wall" (arr[j] >= arr[i])
                # between the start and the destination. If we find such a wall,
                # we cannot jump to it or past it, so we break the loop.
                if arr[j] >= arr[i]:
                    break
                
                # If arr[j] < arr[i], the jump is valid.
                # The total path length is 1 (for index i) plus the length of the path starting from j.
                # We recursively call solve(j) and update our result.
                res = max(res, 1 + solve(j))

            # Explore jumps to the left from index i.
            # The logic is symmetric to jumping to the right.
            for j in range(i - 1, max(i - d - 1, -1), -1):
                if arr[j] >= arr[i]:
                    break
                res = max(res, 1 + solve(j))

            # Cache the computed result for index i before returning.
            dp[i] = res
            return res

        # We can start at any index. We need to compute the longest path starting
        # from each possible index and then find the overall maximum.
        max_visits = 0
        for i in range(n):
            max_visits = max(max_visits, solve(i))

        return max_visits