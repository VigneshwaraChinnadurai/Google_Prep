from typing import List

class Solution:
    def countMajoritySubarrays(self, nums: List[int], target: int) -> int:
        n = len(nums)
        
        # The condition for a subarray is that the count of `target` is strictly
        # greater than half the length of the subarray.
        # Let k = count(target) and other = count(non-target).
        # The condition is k > (k + other) / 2, which simplifies to 2k > k + other,
        # or k > other.
        
        # We can transform the problem by creating a new array `arr` where:
        # arr[i] = 1 if nums[i] == target
        # arr[i] = -1 if nums[i] != target
        # The condition k > other for a subarray is equivalent to the sum of
        # corresponding elements in `arr` being positive.
        
        # The problem is now to count the number of subarrays with a positive sum.
        # This can be solved efficiently using prefix sums and a Fenwick Tree (BIT).
        # Let prefix[k] be the sum of the first k elements of `arr`.
        # The sum of a subarray arr[i..j] is prefix[j+1] - prefix[i].
        # We need to find pairs (i, j) with i <= j such that prefix[j+1] - prefix[i] > 0,
        # which is prefix[j+1] > prefix[i].
        
        # Let's re-index to make it clearer: we need to count pairs (i', k') with i' < k'
        # such that prefix_array[i'] < prefix_array[k'], where prefix_array has n+1 elements.
        
        # The values of the prefix sums can range from -n to n.
        # To use a BIT, we need to map these values to non-negative indices.
        # We can shift all values by an offset. Let offset = n.
        # The shifted values will be in the range [0, 2n].
        # The BIT will need a size of 2n + 2 to handle indices up to 2n+1.
        
        offset = n
        bit_size = 2 * n + 2
        bit = [0] * bit_size
        
        # BIT helper functions (1-based internal indexing)
        def update(index, delta):
          index += 1
          while index < bit_size:
            bit[index] += delta
            index += index & -index
            
        def query(index):
          if index < 0:
            return 0
          index += 1
          s = 0
          while index > 0:
            s += bit[index]
            index -= index & -index
          return s

        # Transform nums to arr
        arr = [1 if x == target else -1 for x in nums]
        
        total_count = 0
        current_prefix_sum = 0
        
        # The initial prefix sum is 0 (for an empty prefix).
        # We add this to our data structure.
        update(0 + offset, 1)
        
        # Iterate through the transformed array, calculating prefix sums on the fly.
        for val in arr:
          current_prefix_sum += val
          
          # For the current prefix sum `p_k`, we need to count how many previous
          # prefix sums `p_i` (with i < k) satisfy `p_i < p_k`.
          # This is equivalent to querying the sum of frequencies of all values
          # up to `p_k - 1`.
          
          # The argument to query is the shifted value of (current_prefix_sum - 1).
          count_smaller = query(current_prefix_sum - 1 + offset)
          total_count += count_smaller
          
          # Add the current prefix sum to the BIT.
          update(current_prefix_sum + offset, 1)
          
        return total_count