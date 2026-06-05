import collections
from typing import List

class Solution:
    def stringIndices(self, wordsContainer: List[str], wordsQuery: List[str]) -> List[int]:
        trie = {}

        # Step 1: Build the Trie with reversed words
        # Each node is a dictionary.
        # Special key 'terminals' holds a list of (length, index) for words ending at this node.
        for i, word in enumerate(wordsContainer):
            node = trie
            for char in reversed(word):
                node = node.setdefault(char, {})
            
            node.setdefault('terminals', []).append((len(word), i))

        # Step 2: Iterative DFS (post-order) to populate 'best' info in each node.
        # 'best' will be a tuple (min_length, min_index) for the subtree.
        # We use a standard iterative post-order traversal pattern with two stacks
        # to avoid Python's recursion depth limits.
        stack1 = [trie]
        post_order_nodes = []
        while stack1:
            node = stack1.pop()
            post_order_nodes.append(node)
            for char in node:
                if len(char) == 1:  # Distinguish children (single char) from special keys
                    stack1.append(node[char])
        
        # Process nodes in post-order (by iterating through post_order_nodes in reverse)
        for node in reversed(post_order_nodes):
            # The best candidate is the one with the minimum length, breaking ties with the minimum index.
            # Python's tuple comparison handles this lexicographically.
            best_candidate = (float('inf'), float('inf'))
            
            # Check for words that terminate at this node
            if 'terminals' in node:
                best_candidate = min(node['terminals'])
            
            # Propagate the best candidate up from children nodes
            for char in node:
                if len(char) == 1:
                    child_node = node[char]
                    best_candidate = min(best_candidate, child_node['best'])
            
            node['best'] = best_candidate

        # Step 3: Process queries
        ans = []
        for query in wordsQuery:
            node = trie
            last_valid_node = trie
            for char in reversed(query):
                if char in node:
                    node = node[char]
                    last_valid_node = node
                else:
                    break
            ans.append(last_valid_node['best'][1])
            
        return ans