class Solution:
    def smallestSubsequence(self, s: str) -> str:
        """
        Finds the lexicographically smallest subsequence of s that contains all
        the distinct characters of s exactly once.

        This problem can be solved greedily using a monotonic stack. The core idea
        is to iterate through the string and build the result. For each character,
        we decide whether to add it to our result. To maintain the lexicographically
        smallest property, if the current character is smaller than the last character
        in our result, we should pop the last character, provided it appears again
        later in the string.

        The algorithm is as follows:
        1. Pre-calculate the last occurrence index for each character in `s`.
        2. Initialize an empty stack to build the result and a `seen` set to track
           characters in the stack.
        3. Iterate through `s` with index `i` and character `char`:
           a. If `char` is already in `seen`, skip it. The one in the stack is at
              an earlier, more optimal position.
           b. While the stack is not empty, `char` is smaller than the stack's top,
              and the stack's top character appears again later (i.e., its last
              occurrence index is > `i`), pop from the stack and remove from `seen`.
              This step ensures we keep the result as small as possible.
           c. Push `char` onto the stack and add it to `seen`.
        4. Join the characters in the stack to get the final result.
        """
        last_occurrence = {char: i for i, char in enumerate(s)}
        stack = []
        seen = set()

        for i, char in enumerate(s):
            if char in seen:
                continue

            while stack and char < stack[-1] and i < last_occurrence[stack[-1]]:
                popped_char = stack.pop()
                seen.remove(popped_char)

            stack.append(char)
            seen.add(char)

        return "".join(stack)