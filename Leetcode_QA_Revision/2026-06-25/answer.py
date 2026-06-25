from typing import List

class Solution:
    def countMajoritySubarrays(self, nums: List[int], target: int) -> int:
        n = len(nums)
        total_majority_subarrays = 0

        # Iterate through all possible starting positions of a subarray
        for i in range(n):
            # For each starting position, iterate through all possible ending positions
            # and keep track of the balance of target vs other elements.
            balance = 0  # balance = count(target) - count(other_elements)
            
            for j in range(i, n):
                # Extend the subarray nums[i..j] by one element nums[j]
                if nums[j] == target:
                    balance += 1
                else:
                    balance -= 1
                
                # Check if target is the majority element in the subarray nums[i..j].
                # The condition for target being a majority element is:
                # count(target) > length / 2
                # This simplifies to:
                # 2 * count(target) > length
                # 2 * count(target) > count(target) + count(other_elements)
                # count(target) > count(other_elements)
                # This is equivalent to our balance score being greater than 0.
                if balance > 0:
                    total_majority_subarrays += 1
        
        return total_majority_subarrays