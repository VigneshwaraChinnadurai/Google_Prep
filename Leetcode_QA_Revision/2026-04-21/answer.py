from typing import List
from collections import defaultdict, Counter

class Solution:
    def minimumHammingDistance(self, source: List[int], target: List[int], allowedSwaps: List[List[int]]) -> int:
        n = len(source)
        parent = list(range(n))

        def find(i: int) -> int:
            if parent[i] == i:
                return i
            # Path compression
            parent[i] = find(parent[i])
            return parent[i]

        def union(i: int, j: int) -> None:
            root_i = find(i)
            root_j = find(j)
            if root_i != root_j:
                parent[root_j] = root_i

        # 1. Build connected components of indices using Union-Find
        for u, v in allowedSwaps:
            union(u, v)

        # 2. Group indices by their component
        components = defaultdict(list)
        for i in range(n):
            components[find(i)].append(i)

        # 3. For each component, calculate the maximum possible matches
        total_matches = 0
        for indices in components.values():
            # Create multisets of source and target values for the current component
            source_counts = Counter(source[i] for i in indices)
            target_counts = Counter(target[i] for i in indices)
            
            # The number of matches is the size of the intersection of these multisets.
            # The '&' operator for Counters calculates this intersection.
            intersection = source_counts & target_counts
            total_matches += sum(intersection.values())
            
        # 4. The minimum Hamming distance is the total number of elements
        # minus the maximum number of matches we can achieve.
        return n - total_matches