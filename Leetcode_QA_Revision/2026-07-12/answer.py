class Solution:
    def arrayRankTransform(self, arr: list[int]) -> list[int]:
        # Step 1: Get unique elements and sort them to establish rank order.
        # set(arr) removes duplicates. sorted() puts them in ascending order.
        sorted_unique_arr = sorted(set(arr))
        
        # Step 2: Create a map from each value to its rank.
        # The rank is the 1-based index in the sorted unique list.
        # A dictionary comprehension is a concise way to build this map.
        rank_map = {value: i + 1 for i, value in enumerate(sorted_unique_arr)}
        
        # Step 3: Transform the original array by replacing each element
        # with its rank from the map.
        # A list comprehension iterates through the original array and builds the result.
        return [rank_map[num] for num in arr]