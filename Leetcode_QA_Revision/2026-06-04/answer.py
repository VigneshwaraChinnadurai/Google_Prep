class Solution:
    def totalWaviness(self, num1: int, num2: int) -> int:
        
        def get_waviness(n: int) -> int:
            """Calculates the waviness of a single number."""
            s = str(n)
            
            # Per problem definition, numbers with fewer than 3 digits have a waviness of 0.
            if len(s) < 3:
                return 0
            
            digits = [int(d) for d in s]
            waviness = 0
            
            # The first and last digits cannot be peaks or valleys, so we iterate
            # from the second digit (index 1) to the second-to-last digit.
            for i in range(1, len(digits) - 1):
                prev_digit = digits[i-1]
                curr_digit = digits[i]
                next_digit = digits[i+1]
                
                # Check for a peak: current digit is strictly greater than both neighbors.
                is_peak = prev_digit < curr_digit and curr_digit > next_digit
                
                # Check for a valley: current digit is strictly less than both neighbors.
                is_valley = prev_digit > curr_digit and curr_digit < next_digit
                
                if is_peak or is_valley:
                    waviness += 1
            
            return waviness

        total_waviness = 0
        # Iterate through each number in the inclusive range [num1, num2].
        for i in range(num1, num2 + 1):
            total_waviness += get_waviness(i)
            
        return total_waviness