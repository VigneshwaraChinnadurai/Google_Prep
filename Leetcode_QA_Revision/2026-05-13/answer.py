from typing import List

class Solution:
    def minMoves(self, nums: List[int], limit: int) -> int:
        n = len(nums)
        # The diff array will store the change in the number of moves required
        # as the target sum T increases.
        # diff[T] = moves(T) - moves(T-1).
        # The maximum possible sum is 2 * limit. We need an index for 2*limit+1.
        # So the size is 2 * limit + 2.
        diff = [0] * (2 * limit + 2)

        # We iterate through the n/2 pairs of numbers.
        for i in range(n // 2):
            a = nums[i]
            b = nums[n - 1 - i]

            # For each pair (a, b), we analyze the number of moves required
            # to make their sum equal to a target T.
            # Let min_val = min(a, b) and max_val = max(a, b).
            #
            # - 0 moves: If T = a + b.
            # - 1 move: If we change one number. The new sum can be in the range
            #   [min_val + 1, max_val + limit].
            # - 2 moves: Otherwise.
            #
            # Let's analyze the cost function for a single pair as T varies:
            # - T in [2, min_val]: 2 moves.
            # - T in [min_val + 1, a + b - 1]: 1 move.
            # - T = a + b: 0 moves.
            # - T in [a + b + 1, max_val + limit]: 1 move.
            # - T in [max_val + limit + 1, 2 * limit]: 2 moves.
            #
            # We can model the total cost by summing up these individual cost functions.
            # A sweep-line approach using a difference array is efficient for this.
            # We record the changes in the cost function at the event points.

            min_val = min(a, b)
            max_val = max(a, b)
            s = a + b

            # Initially, for any T, the cost for this pair is 2.
            # This contributes +2 to the cost starting from T=2.
            diff[2] += 2

            # At T = min_val + 1, the cost drops from 2 to 1 (a change of -1).
            diff[min_val + 1] -= 1

            # At T = s, the cost drops from 1 to 0 (a change of -1).
            diff[s] -= 1

            # At T = s + 1, the cost increases from 0 to 1 (a change of +1).
            diff[s + 1] += 1

            # At T = max_val + limit + 1, the cost increases from 1 to 2 (a change of +1).
            diff[max_val + limit + 1] += 1

        # Now, we compute the prefix sum of the diff array to get the actual
        # number of moves for each target sum T, and find the minimum.
        min_moves = float('inf')
        current_moves = 0
        
        # The possible target sums T range from 2 to 2 * limit.
        for t in range(2, 2 * limit + 1):
            current_moves += diff[t]
            min_moves = min(min_moves, current_moves)
            
        return min_moves