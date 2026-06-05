import functools

class Solution:
    def totalWaviness(self, num1: int, num2: int) -> int:
        
        def solve(s: str) -> int:
            """
            Calculates the total waviness of all numbers from 0 up to the number represented by s.
            """
            L = len(s)

            @functools.lru_cache(None)
            def dp(index: int, p1: int, p2: int, is_less: bool, is_leading_zero: bool) -> tuple[int, int]:
                """
                Digit DP function to compute count and total waviness.
                
                Args:
                    index: Current digit position to fill (from left, 0-indexed).
                    p1: Previous digit (at index-1). Sentinel 10 if not present.
                    p2: Digit at index-2. Sentinel 10 if not present.
                    is_less: True if the prefix formed so far is smaller than s's prefix.
                    is_leading_zero: True if we are currently placing leading zeros.
                
                Returns:
                    A tuple (count, total_waviness) for numbers formed from this state.
                """
                if index == L:
                    return (1, 0)

                limit = int(s[index]) if not is_less else 9
                
                total_count = 0
                total_waviness = 0

                for d in range(limit + 1):
                    new_is_less = is_less or (d < limit)

                    if is_leading_zero and d == 0:
                        # Still in leading zero state. This path generates numbers with fewer digits.
                        sub_count, sub_waviness = dp(index + 1, 10, 10, new_is_less, True)
                        total_count += sub_count
                        total_waviness += sub_waviness
                    else:
                        # A non-zero digit is placed, or we are continuing a number.
                        waviness_here = 0
                        if not is_leading_zero and p2 != 10:
                            # Check for a peak or valley at the p1 position.
                            # This requires at least three digits: p2, p1, d.
                            if p2 < p1 and p1 > d:  # Peak
                                waviness_here = 1
                            elif p2 > p1 and p1 < d:  # Valley
                                waviness_here = 1
                        
                        sub_count, sub_waviness = dp(index + 1, d, p1, new_is_less, False)
                        
                        total_count += sub_count
                        # Add waviness from subproblems, plus the waviness created at this step.
                        # The waviness_here is counted for each of the sub_count numbers.
                        total_waviness += sub_waviness + waviness_here * sub_count
                
                return total_count, total_waviness

            # Initial call to dp for numbers up to s.
            # The result is the total waviness for numbers in [0, s].
            _, waviness = dp(0, 10, 10, False, True)
            return waviness

        # The total waviness in [num1, num2] is F(num2) - F(num1-1),
        # where F(n) is the total waviness in [0, n].
        waviness_up_to_num2 = solve(str(num2))
        waviness_up_to_num1_minus_1 = solve(str(num1 - 1))
        
        return waviness_up_to_num2 - waviness_up_to_num1_minus_1