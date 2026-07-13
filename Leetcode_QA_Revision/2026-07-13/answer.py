class Solution:
    def sequentialDigits(self, low: int, high: int) -> List[int]:
        # All sequential digit numbers are substrings of "123456789".
        # For example, 234, 5678, etc.
        sample = "123456789"
        n = 9
        result = []

        # The length of any number in the range [low, high] must be
        # between len(str(low)) and len(str(high)).
        len_low = len(str(low))
        len_high = len(str(high))

        # Iterate through all possible lengths of sequential numbers.
        for length in range(len_low, len_high + 1):
            # Iterate through all possible starting positions for a given length.
            # This creates a sliding window over the sample string.
            for i in range(n - length + 1):
                # Extract the substring and convert it to an integer.
                num = int(sample[i:i + length])

                # Optimization: The numbers are generated in increasing order.
                # If we exceed 'high', no further numbers will be in range,
                # so we can stop and return the result.
                if num > high:
                    return result
                
                # If the number is within the [low, high] range, add it.
                # We already know num <= high from the check above.
                if num >= low:
                    result.append(num)
        
        return result