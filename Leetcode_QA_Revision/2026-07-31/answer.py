import collections

class Solution:
    def minimumPushes(self, word: str) -> int:
        """
        Calculates the minimum number of pushes to type a word on a remappable 8-key keypad.

        The strategy is based on a greedy approach. To minimize the total number of pushes,
        the most frequent characters should be assigned to the key positions that require the
        fewest pushes.

        The available key positions and their costs are determined by the 8 available keys (2-9):
        - 8 positions with a cost of 1 push (the first character on each of the 8 keys).
        - 8 positions with a cost of 2 pushes (the second character on each key).
        - 8 positions with a cost of 3 pushes (the third character on each key).
        - and so on.

        The algorithm is as follows:
        1. Count the frequency of each character in the input `word`.
        2. Sort these frequencies in descending order.
        3. Iterate through the sorted frequencies and assign them to the cheapest available
           key positions based on their frequency rank.
        4. The total number of pushes is the sum of (frequency * push_cost) for each unique character.
        """
        
        # 1. Count character frequencies.
        # collections.Counter is efficient for this task.
        counts = collections.Counter(word)
        
        # 2. Get the frequencies and sort them in descending order.
        # The greedy strategy requires us to assign the cheapest push costs
        # to the most frequent characters.
        frequencies = sorted(counts.values(), reverse=True)
        
        total_pushes = 0
        
        # 3. & 4. Iterate through sorted frequencies and calculate the total cost.
        for i, freq in enumerate(frequencies):
            # Determine the push cost based on the character's rank in frequency.
            # The index `i` corresponds to the rank (0-indexed).
            # - Ranks 0-7 (first 8) are assigned to 1-push slots.
            # - Ranks 8-15 (next 8) are assigned to 2-push slots.
            # - Ranks 16-23 (next 8) are assigned to 3-push slots.
            # - Ranks 24-25 (last 2) are assigned to 4-push slots.
            # This pattern can be calculated using integer division: (i // 8) + 1.
            push_cost = (i // 8) + 1
            
            total_pushes += freq * push_cost
            
        return total_pushes