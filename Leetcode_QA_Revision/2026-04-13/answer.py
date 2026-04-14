class Solution:
    def getMinDistance(self, nums: list[int], target: int, start: int) -> int:
        """
        Finds the minimum distance from a starting index to a target element in an array.
        """
        min_distance = float('inf')
        
        for i, num in enumerate(nums):
            if num == target:
                distance = abs(i - start)
                if distance < min_distance:
                    min_distance = distance
                    
        return min_distance