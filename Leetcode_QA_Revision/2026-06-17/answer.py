class Solution:
    def processStr(self, s: str, k: int) -> str:
        n = len(s)
        # history[i] stores the length of the result string after processing s[0...i-1]
        history = [0] * (n + 1)
        
        # Forward pass: calculate the length of the string at each step.
        # We don't build the string, only track its length.
        length = 0
        for i in range(n):
            char = s[i]
            if 'a' <= char <= 'z':
                length += 1
            elif char == '*':
                length = max(0, length - 1)
            elif char == '#':
                length *= 2
            # '%' does not change the length
            history[i+1] = length
            
        final_length = history[n]
        
        # If k is out of bounds of the final string, return '.'
        if k >= final_length:
            return '.'
            
        # Backward pass: trace the k-th index back to the character that created it.
        # We start with the final index k and reverse the operations one by one
        # to find which character originally landed at that position.
        current_k = k
        for i in range(n - 1, -1, -1):
            char = s[i]
            len_before = history[i]
            
            if 'a' <= char <= 'z':
                # Inverse of: result = result_before + char
                # The appended character is at index len_before.
                if current_k == len_before:
                    return char
                # If current_k < len_before, the index points to a character
                # from result_before, so the index itself is unaffected as we go back.
            elif char == '*':
                # Inverse of: result = result_before[:-1]
                # The length decreased. current_k is a valid index in the shorter string,
                # so it's also a valid index in the longer string_before. The index is unaffected.
                pass
            elif char == '#':
                # Inverse of: result = result_before + result_before
                # An index in the duplicated string maps back to an index in the original
                # by taking modulo of the original length.
                if len_before > 0:
                    current_k %= len_before
            elif char == '%':
                # Inverse of: result = reverse(result_before)
                # An index j in the reversed string corresponds to index 
                # (len_before - 1 - j) in the original string.
                if len_before > 0:
                    current_k = len_before - 1 - current_k
                    
        # This part of the code should be unreachable if k was initially valid,
        # as the character must have been added by one of the letter operations.
        # It's a fallback, for instance, if the initial string was empty and k=0.
        return '.'