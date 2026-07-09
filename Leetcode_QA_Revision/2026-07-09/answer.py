class Solution:
    def pathExistenceQueries(self, n: int, nums: list[int], maxDiff: int, queries: list[list[int]]) -> list[bool]:
        # Union-Find data structure implemented with nested functions
        parent = list(range(n))
        size = [1] * n

        def find(i: int) -> int:
            """Finds the representative of the set containing node i with path compression."""
            if parent[i] == i:
                return i
            parent[i] = find(parent[i])
            return parent[i]

        def union(i: int, j: int) -> None:
            """Merges the sets containing nodes i and j using union-by-size."""
            root_i = find(i)
            root_j = find(j)
            if root_i != root_j:
                # Attach the smaller tree to the root of the larger tree
                if size[root_i] < size[root_j]:
                    root_i, root_j = root_j, root_i
                parent[root_j] = root_i
                size[root_i] += size[root_j]
        
        # The core insight is that the connected components of the graph are determined
        # solely by the edges between adjacent nodes in the sorted `nums` array.
        # If an edge exists between non-adjacent nodes u and v (with u < v),
        # meaning nums[v] - nums[u] <= maxDiff, then for any k where u <= k < v,
        # it must be that nums[k+1] - nums[k] <= nums[v] - nums[u] <= maxDiff.
        # This is because nums is non-decreasing, so nums[k+1] - nums[k] >= 0,
        # and the sum of these differences from u to v-1 equals nums[v] - nums[u].
        # Therefore, a path u -> u+1 -> ... -> v exists, connecting u and v.
        # This simplifies the problem to only considering adjacent nodes.
        
        # Build the connected components by iterating through adjacent nodes.
        for i in range(n - 1):
            if nums[i+1] - nums[i] <= maxDiff:
                union(i, i+1)
        
        # Process each query.
        answer = []
        for u, v in queries:
            # Two nodes have a path between them if they are in the same component.
            answer.append(find(u) == find(v))
            
        return answer