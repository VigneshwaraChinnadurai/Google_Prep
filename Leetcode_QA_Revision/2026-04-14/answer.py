import math
from typing import List

class Solution:
  def minimumTotalDistance(self, robot: List[int], factory: List[List[int]]) -> int:
    robot.sort()
    factory.sort()

    n = len(robot)
    m = len(factory)

    # dp[i] represents the minimum cost to repair the first i robots.
    # The state is updated iteratively for each factory.
    dp = [math.inf] * (n + 1)
    dp[0] = 0

    # Iterate through each factory
    for j in range(m):
        pos, limit = factory[j]
        
        # next_dp[i] will store the minimum cost to repair the first i robots
        # considering factories from 0 to j.
        next_dp = [math.inf] * (n + 1)
        
        # To compute next_dp[i], we consider assigning k of the first i robots
        # to the current factory j.
        for i in range(n + 1):
            
            current_cost_for_factory_j = 0
            # k is the number of robots assigned to the current factory j.
            # These must be the last k robots among the first i due to the non-crossing property.
            for k in range(min(i, limit) + 1):
                # The cost of assigning k robots (from robot[i-k] to robot[i-1]) to factory j.
                if k > 0:
                    current_cost_for_factory_j += abs(robot[i - k] - pos)
                
                # The remaining i-k robots must be repaired by factories 0 to j-1.
                # The minimum cost for that is stored in dp[i-k].
                if dp[i - k] != math.inf:
                    # The total cost is the sum of the subproblem cost and the cost for the current assignment.
                    next_dp[i] = min(next_dp[i], dp[i - k] + current_cost_for_factory_j)
        
        # Update dp state for the next factory iteration.
        dp = next_dp
        
    return dp[n]