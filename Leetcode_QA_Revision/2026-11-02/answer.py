class Solution:
    def minMoves2(self, nums: list[int]) -> int:
        nums = sorted(nums)
        med = nums[len(nums) // 2]
        return sum(abs(x - med) for x in nums)