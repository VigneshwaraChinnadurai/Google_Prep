class Solution:
    def check(self, nums: list[int]) -> bool:
        n = len(nums)
        break_count = 0
        
        for i in range(n):
            # Compare the current element with the next element in a circular fashion.
            # The modulo operator handles the wrap-around from the last element to the first.
            if nums[i] > nums[(i + 1) % n]:
                break_count += 1
        
        # A valid rotated sorted array can have at most one point of "descent".
        # - 0 descents: All elements are equal (e.g., [3, 3, 3]).
        # - 1 descent: This is the standard case for a rotated sorted array 
        #   (e.g., [3, 4, 5, 1, 2]) or a non-rotated sorted array with distinct
        #   start/end values (e.g., [1, 2, 3], where the descent is from 3 to 1).
        # - More than 1 descent: The array cannot be a rotated version of a sorted array.
        return break_count <= 1