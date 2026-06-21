class Solution:
    def maxIceCream(self, costs: list[int], coins: int) -> int:
        # The problem asks for a solution using counting sort.
        # The constraints are n <= 10^5 and 1 <= costs[i] <= 10^5, which makes
        # counting sort a suitable and efficient approach.

        # The optimal strategy is a greedy one: to maximize the number of ice cream
        # bars, we should always buy the cheapest ones first. Counting sort allows us
        # to process the costs in increasing order efficiently.

        # Step 1: Create a frequency array to count occurrences of each cost.
        # This is the core of the counting sort approach.
        # The maximum cost is 100,000, so the array size is 100,001 to include this index.
        freq = [0] * 100001
        for cost in costs:
            freq[cost] += 1
            
        ice_cream_count = 0
        
        # Step 2: Iterate through costs from the cheapest (1) to the most expensive.
        for cost in range(1, 100001):
            # If we don't have enough money for even the current cheapest bar,
            # we can't buy any more, so we can stop.
            if coins < cost:
                break
            
            # Check if there are any ice cream bars available at the current cost.
            if freq[cost] > 0:
                # Determine how many bars of this cost we can afford and buy.
                # We can afford `coins // cost` bars.
                # We have `freq[cost]` bars available.
                # So, we can buy the minimum of these two quantities.
                num_to_buy = min(freq[cost], coins // cost)
                
                # Update the remaining coins and the number of bars bought.
                coins -= num_to_buy * cost
                ice_cream_count += num_to_buy
                
        return ice_cream_count