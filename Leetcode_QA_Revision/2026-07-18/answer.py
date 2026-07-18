import math
from typing import List

class Solution:
    def findGCD(self, nums: List[int]) -> int:
        """
        Finds the greatest common divisor of the smallest and largest numbers in the array.
        """
        # Step 1: Find the smallest and largest numbers in the array.
        # The min() and max() functions are efficient for this, each taking O(N) time.
        smallest_num = min(nums)
        largest_num = max(nums)
        
        # Step 2: Calculate the greatest common divisor (GCD) of these two numbers.
        # Python's math.gcd() function uses the Euclidean algorithm, which is very efficient.
        return math.gcd(smallest_num, largest_num)