from typing import List

class Solution:
    def leftRightDifference(self, nums: List[int]) -> List[int]:
        """
        Calculates the absolute difference between the sum of elements to the left
        and the sum of elements to the right for each index in the array.
        """
        
        # First, calculate the total sum of all elements in the array.
        # This allows for efficient calculation of the right sum later.
        total_sum = sum(nums)
        
        # Initialize the sum of elements to the left of the current index.
        # Before the first element, this sum is 0.
        left_sum = 0
        
        # Initialize the list to store the final results.
        answer = []
        
        # Iterate through each number in the input array.
        for num in nums:
            # The sum of elements to the right of the current element is
            # the total sum minus the sum of elements to the left and the current element itself.
            right_sum = total_sum - left_sum - num
            
            # Calculate the absolute difference as required and append it to the answer list.
            answer.append(abs(left_sum - right_sum))
            
            # Update the left_sum for the next iteration by adding the current number.
            # This way, for the next element, left_sum will correctly represent the sum
            # of all elements to its left.
            left_sum += num
            
        return answer