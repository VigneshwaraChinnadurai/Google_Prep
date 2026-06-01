```python
from typing import List

class Solution:
    def minimumCost(self, cost: List[int]) -> int:
        # The core idea is a greedy approach. To minimize the total cost, we should
        # try to get the most expensive candies for free.
        # The discount rule: for every two candies bought, a third one is free,
        # provided its cost is less than or equal to the minimum of the two
        # purchased candies.

        # Let's sort the candy costs in descending order.
        # Consider a group of three candies with costs c1, c2, c3 such that c1 >= c2 >= c3.
        # If we buy the two most expensive ones (c1 and c2), the minimum cost of the
        # purchased pair is c2. We can get a candy with cost up to c2 for free.
        # Since c3 <= c2, we can choose c3 to be the free candy. This is the best
        # possible move for this group as we are getting the most expensive
        # possible candy (c3) for free.

        # This greedy strategy of grouping the sorted costs into threes and getting
        # the cheapest of the three for free is optimal. We always maximize the
        # value of the free candy at each step.

        cost.sort(reverse=True)
        
        total_cost = 0
        n = len(cost)
        
        # We iterate through the sorted costs. We pay for the first two items in
        # every group of three, and the third item is free.
        for i in range(n):
            # The indices of items to be paid for are 0, 1, 3, 4, 6, 7, ...
            # The indices of free items are 2, 5, 8, ...
            # An item at index `i` is free if `(i + 1)` is a multiple of 3.
            if (i + 1) % 3 != 0:
                total_cost += cost[i]
                
        return total_cost

```