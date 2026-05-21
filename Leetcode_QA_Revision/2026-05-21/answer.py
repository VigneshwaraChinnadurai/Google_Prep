# Helper class for the Trie data structure.
# Each node represents a digit.
class TrieNode:
    def __init__(self):
        self.children = {}

class Solution:
    def longestCommonPrefix(self, arr1: List[int], arr2: List[int]) -> int:
        """
        Finds the length of the longest common prefix between any pair of numbers
        from arr1 and arr2.
        """
        
        # To potentially save space for the Trie, we build it from the smaller array.
        # The time complexity is symmetric and remains unaffected.
        if len(arr1) > len(arr2):
            arr1, arr2 = arr2, arr1

        root = TrieNode()

        # Step 1: Build the Trie.
        # Insert all numbers from arr1 into the Trie. Each number is treated as
        # a sequence of digits. Each path from the root represents a prefix.
        for num in arr1:
            s = str(num)
            node = root
            for digit in s:
                if digit not in node.children:
                    node.children[digit] = TrieNode()
                node = node.children[digit]

        max_len = 0

        # Step 2: Search for common prefixes.
        # For each number in arr2, traverse the Trie to find the length of the
        # longest prefix that also exists in the Trie (i.e., is a prefix of a number in arr1).
        for num in arr2:
            s = str(num)
            node = root
            current_len = 0
            for digit in s:
                if digit in node.children:
                    node = node.children[digit]
                    current_len += 1
                else:
                    # The path ends here, meaning no longer prefixes of the current number
                    # exist in the Trie.
                    break
            
            # Update the overall maximum length found.
            max_len = max(max_len, current_len)

        return max_len