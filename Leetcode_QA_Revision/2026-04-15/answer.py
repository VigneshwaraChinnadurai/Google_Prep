class Solution:
    def closestTarget(self, words: List[str], target: str, startIndex: int) -> int:
        n = len(words)
        
        # We can search outwards from the startIndex. This is equivalent to a
        # Breadth-First Search (BFS) on the circular array.
        # The distance 'dist' increases from 0.
        # For each distance, we check both clockwise and counter-clockwise positions.
        # The first time we find the target, that distance is guaranteed to be the shortest.
        
        # The maximum shortest distance in a circular array of size n is n // 2.
        # Looping 'dist' from 0 to n // 2 is sufficient to check every index.
        for dist in range(n // 2 + 1):
            # Check 'dist' steps to the right (clockwise)
            right_index = (startIndex + dist) % n
            if words[right_index] == target:
                return dist
            
            # Check 'dist' steps to the left (counter-clockwise)
            # The '+ n' ensures the result is non-negative before the modulo.
            left_index = (startIndex - dist + n) % n
            if words[left_index] == target:
                return dist
                
        # If the loop completes, the target was not found in the array.
        return -1