import collections
from typing import List

class Solution:
    def maximumLength(self, nums: List[int]) -> int:
        """
        Finds the maximum number of elements in a subset that can form a special pattern.
        The pattern is of the form [x, x^2, x^4, ..., x^(2^k), ..., x^4, x^2, x].
        """
        counts = collections.Counter(nums)
        max_len = 0

        # Handle the special case of x = 1.
        # For x=1, the sequence is [1, 1, ..., 1]. The length must be odd.
        # The maximum length is the count of 1s if it's odd, or one less if it's even.
        if 1 in counts:
            count_1 = counts[1]
            if count_1 % 2 == 1:
                max_len = count_1
            else:
                max_len = count_1 - 1
        else:
            # If there are no 1s, any single element forms a valid subset of length 1.
            # So the answer is at least 1.
            max_len = 1

        # Use a set to keep track of numbers that have been part of a processed chain
        # to avoid redundant calculations.
        processed = {1}

        for num in counts:
            if num in processed:
                continue
            
            curr_len = 0
            curr_num = num
            
            while True:
                c = counts.get(curr_num, 0)
                
                if c == 0:
                    # The chain is broken. The previous element must have been the peak.
                    # We added 2 for the previous element, assuming it was a symmetric pair.
                    # Since it's the peak, it's used only once. So, we subtract 1.
                    curr_len -= 1
                    break
                
                processed.add(curr_num)
                
                if c == 1:
                    # This element can only be used once, so it must be the peak.
                    # The chain ends here.
                    curr_len += 1
                    break
                
                # c >= 2, we can use two of curr_num for the symmetric parts.
                curr_len += 2
                
                # Optimization: if curr_num > sqrt(10^9), its square will exceed the
                # maximum possible value in nums.
                if curr_num > 31622: # sqrt(10^9) is approx 31622.77
                    # The next number in the sequence will be too large.
                    # This is equivalent to the next element having a count of 0.
                    # The current element must be the peak.
                    curr_len -= 1
                    break
                
                curr_num *= curr_num

            max_len = max(max_len, curr_len)
            
        return max_len