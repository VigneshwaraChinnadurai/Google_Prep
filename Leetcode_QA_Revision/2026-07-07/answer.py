class Solution:
    def sumAndMultiply(self, n: int) -> int:
        # Handle the edge case where n is 0.
        # If n = 0, there are no non-zero digits.
        # Per the problem, x = 0, sum = 0, so the result is 0 * 0 = 0.
        if n == 0:
            return 0

        # Convert the integer to a string for easy iteration over its digits.
        s_n = str(n)
        
        # Initialize a string to build the concatenated non-zero digits (for x).
        x_str = ""
        # Initialize a variable to store the sum of these digits.
        sum_val = 0
        
        # Iterate through each character (digit) of the string.
        for digit_char in s_n:
            if digit_char != '0':
                # Append the non-zero digit to the string for x.
                x_str += digit_char
                # Add the integer value of the digit to the sum.
                sum_val += int(digit_char)
                
        # Convert the concatenated string of digits back to an integer to get x.
        # Since n > 0, there must be at least one non-zero digit, so x_str
        # will not be empty and this conversion is safe.
        x = int(x_str)
        
        # Return the product of x and the sum of its digits.
        return x * sum_val