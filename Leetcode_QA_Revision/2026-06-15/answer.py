# Definition for singly-linked list.
# class ListNode:
#     def __init__(self, val=0, next=None):
#         self.val = val
#         self.next = next
from typing import Optional

class Solution:
    def deleteMiddle(self, head: Optional[ListNode]) -> Optional[ListNode]:
        # According to constraints, the number of nodes is at least 1.
        # Handle the edge case of a single-node list.
        # The middle node for n=1 is at index floor(1/2) = 0, which is the head.
        # Deleting it results in an empty list.
        if not head.next:
            return None

        # We use the two-pointer (fast and slow) technique to find the middle node.
        # To delete the middle node, we also need a pointer to the node *before* it.
        
        # `slow` will point to the middle node to be deleted.
        # `fast` will traverse twice as fast.
        # `prev_slow` will point to the node just before `slow`.
        slow = head
        fast = head
        prev_slow = None

        # Move pointers until `fast` reaches the end of the list.
        while fast and fast.next:
            prev_slow = slow
            slow = slow.next
            fast = fast.next.next
        
        # When the loop terminates, `slow` is pointing at the middle node.
        # `prev_slow` is pointing at the node right before the middle node.
        # Since we handled the n=1 case, `prev_slow` will never be None here,
        # as the loop runs at least once for any list with n >= 2.
        
        # Delete the middle node by updating the `next` pointer of its predecessor.
        prev_slow.next = slow.next
        
        return head