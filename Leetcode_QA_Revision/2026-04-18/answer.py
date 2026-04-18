```python
class Solution:
    def mirrorDistance(self, n: int) -> int:
        """
        Calculates the mirror distance of an integer n.
        The mirror distance is defined as abs(n - reverse(n)).
        """
        
        # Convert the integer to a string to easily reverse its digits.
        s_n = str(n)
        
        # Reverse the string using slice notation.
        reversed_s_n = s_n[::-1]
        
        # Convert the reversed string back to an integer.
        # The int() function correctly handles leading zeros, e.g., int("01") becomes 1.
        reversed_n = int(reversed_s_n)
        
        # Calculate the absolute difference between the original number and its reverse.
        return abs(n - reversed_n)

```