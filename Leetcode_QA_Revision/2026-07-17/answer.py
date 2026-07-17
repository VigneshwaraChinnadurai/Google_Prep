import bisect
from typing import List

class Solution:
  def gcdValues(self, nums: List[int], queries: List[int]) -> List[int]:
    MAX_VAL = 0
    for x in nums:
        MAX_VAL = max(MAX_VAL, x)

    freq = [0] * (MAX_VAL + 1)
    for x in nums:
        freq[x] += 1
        
    # counts[i] = number of elements in nums that are multiples of i
    counts = [0] * (MAX_VAL + 1)
    for i in range(1, MAX_VAL + 1):
        for j in range(i, MAX_VAL + 1, i):
            counts[i] += freq[j]
            
    # C[d] = number of pairs with GCD being a multiple of d
    C = [0] * (MAX_VAL + 1)
    for d in range(1, MAX_VAL + 1):
        if counts[d] > 1:
            C[d] = counts[d] * (counts[d] - 1) // 2
            
    # N[d] = number of pairs with GCD exactly d
    N = [0] * (MAX_VAL + 1)
    for d in range(MAX_VAL, 0, -1):
        if C[d] > 0:
            N[d] = C[d]
            for j in range(2 * d, MAX_VAL + 1, d):
                N[d] -= N[j]
            
    # prefix_N[g] = number of pairs with GCD <= g
    prefix_N = [0] * (MAX_VAL + 1)
    for g in range(1, MAX_VAL + 1):
        prefix_N[g] = prefix_N[g-1] + N[g]
        
    ans = []
    for q in queries:
      # For a 0-indexed query q, we need to find the (q+1)-th element.
      # This is the smallest value g such that the number of pairs with
      # GCD <= g is strictly greater than q.
      # bisect_right finds the index of the first element > q.
      # The search range for GCDs is [1, MAX_VAL], which corresponds to
      # indices [1, MAX_VAL] in prefix_N.
      res = bisect.bisect_right(prefix_N, q, lo=1)
      ans.append(res)
        
    return ans