import collections
from typing import List

class Solution:
  def isGood(self, nums: List[int]) -> bool:
    # From the definition, base[n] has a length of n + 1.
    # If nums is a permutation of base[n], its length must also be n + 1.
    # This allows us to determine the value of n: n = len(nums) - 1.
    n = len(nums) - 1
    
    # The smallest base array is base[1] = [1, 1], which has length 2.
    # Thus, n must be at least 1, which means len(nums) must be at least 2.
    if n < 1:
      return False
      
    # Use a frequency counter to count the occurrences of each number.
    counts = collections.Counter(nums)
    
    # For the array to be good, the number n must appear exactly twice.
    if counts[n] != 2:
      return False
      
    # And all numbers from 1 to n-1 must appear exactly once.
    for i in range(1, n):
      if counts[i] != 1:
        return False
        
    # If all the counts match the definition of base[n], the array is good.
    # The sum of the required counts is (n-1)*1 + 2 = n+1, which is equal to len(nums).
    # This implicitly ensures that no other numbers are present in the array.
    return True