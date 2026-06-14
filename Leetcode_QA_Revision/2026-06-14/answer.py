# Definition for singly-linked list.
# class ListNode:
#     def __init__(self, val=0, next=None):
#         self.val = val
#         self.next = next
from typing import Optional

class Solution:
    def pairSum(self, head: Optional[ListNode]) -> int:
        # Step 1: Find the middle of the linked list.
        # Use the slow and fast pointer technique. When the list has an even number of nodes,
        # this will position `slow` at the head of the second half.
        slow = head
        fast = head
        while fast and fast.next:
            slow = slow.next
            fast = fast.next.next
        
        # 'slow' now points to the start of the second half.
        
        # Step 2: Reverse the second half of the list.
        # We use a standard iterative reversal algorithm.
        prev = None
        curr = slow
        while curr:
            next_node = curr.next
            curr.next = prev
            prev = curr
            curr = next_node
        
        # 'prev' is now the new head of the reversed second half.
        
        # Step 3: Calculate twin sums and find the maximum.
        # Iterate with two pointers: one from the original head (first half)
        # and one from the new head of the reversed second half.
        max_twin_sum = 0
        first_half_ptr = head
        second_half_ptr = prev
        
        while second_half_ptr:
            current_sum = first_half_ptr.val + second_half_ptr.val
            max_twin_sum = max(max_twin_sum, current_sum)
            first_half_ptr = first_half_ptr.next
            second_half_ptr = second_half_ptr.next
            
        return max_twin_sum