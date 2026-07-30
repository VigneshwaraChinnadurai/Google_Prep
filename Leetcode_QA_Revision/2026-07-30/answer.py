class Solution:
    def minimumPushes(self, word: str) -> int:
        n = len(word)
        total_pushes = 0
        
        # The problem is to find the minimum number of pushes to type a word with
        # distinct letters by remapping them to 8 telephone keys.
        # The cost of typing a letter is its position on a key (1st: 1 push, 2nd: 2 pushes, etc.).

        # A greedy strategy is optimal here. To minimize the total pushes, we should
        # assign the letters to the cheapest available slots. The cheapest slots are
        # the first positions on each of the 8 keys, all costing 1 push. The next
        # cheapest are the second positions, costing 2 pushes, and so on.

        # This means:
        # - The first 8 letters we assign will each cost 1 push.
        # - The next 8 letters (from the 9th to the 16th) will each cost 2 pushes.
        # - The next 8 letters (17th to 24th) will each cost 3 pushes.
        # - The remaining letters will each cost 4 pushes.

        # We can calculate the total cost by iterating through the n letters and
        # determining the cost for each based on its order.
        for i in range(n):
            # The cost for the character at index `i` (0-indexed) is determined
            # by which group of 8 it falls into.
            # Indices 0-7:  i // 8 = 0. Cost = 1.
            # Indices 8-15: i // 8 = 1. Cost = 2.
            # Indices 16-23:i // 8 = 2. Cost = 3.
            # etc.
            cost = (i // 8) + 1
            total_pushes += cost
            
        return total_pushes