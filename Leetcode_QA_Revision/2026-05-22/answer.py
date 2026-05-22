from typing import List

class Solution:
    def search(self, nums: List[int], target: int) -> int:
        left, right = 0, len(nums) - 1

        while left <= right:
            mid = left + (right - left) // 2

            if nums[mid] == target:
                return mid

            # Determine which half is sorted
            # Case 1: The left half [left...mid] is sorted
            if nums[left] <= nums[mid]:
                # Check if the target is within the range of the sorted left half
                if nums[left] <= target < nums[mid]:
                    # If yes, search in the left half
                    right = mid - 1
                else:
                    # If no, the target must be in the right half
                    left = mid + 1
            # Case 2: The right half [mid...right] is sorted
            else: # nums[left] > nums[mid]
                # Check if the target is within the range of the sorted right half
                if nums[mid] < target <= nums[right]:
                    # If yes, search in the right half
                    left = mid + 1
                else:
                    # If no, the target must be in the left half
                    right = mid - 1
        
        # Target was not found
        return -1