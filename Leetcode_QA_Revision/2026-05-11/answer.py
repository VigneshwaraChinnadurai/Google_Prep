from typing import List

class Solution:
    def separateDigits(self, nums: List[int]) -> List[int]:
        """
        Separates the digits of each integer in an array and returns them in a single flat list.
        """
        # This can be solved concisely using a nested list comprehension.
        # The outer loop iterates through each number in the input list `nums`.
        # The inner loop iterates through each character of the string representation of the number.
        # Each character is converted back to an integer and added to the resulting list.
        return [int(digit) for num in nums for digit in str(num)]