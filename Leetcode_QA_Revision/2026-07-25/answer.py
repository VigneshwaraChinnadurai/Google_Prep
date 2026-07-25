class Solution:
    def maxProduct(self, n: int) -> int:
        # Convert the integer to a string to iterate over its digits.
        s = str(n)
        
        # Convert the string digits to a list of integers.
        digits = [int(digit) for digit in s]
        
        # Sort the list to easily find the two largest digits.
        digits.sort()
        
        # The constraint n >= 10 ensures the list has at least two elements.
        # The maximum product is the product of the two largest digits,
        # which are the last two elements in the sorted list.
        return digits[-1] * digits[-2]