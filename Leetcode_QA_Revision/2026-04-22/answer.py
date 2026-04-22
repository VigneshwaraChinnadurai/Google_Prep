class Solution:
    def twoEditWords(self, queries: List[str], dictionary: List[str]) -> List[str]:
        result = []
        
        for query_word in queries:
            # For each query word, check if it can be matched with any dictionary
            # word by changing at most two characters.
            for dict_word in dictionary:
                mismatches = 0
                # Since all words have the same length, we can use zip to
                # iterate over corresponding characters.
                for char_q, char_d in zip(query_word, dict_word):
                    if char_q != char_d:
                        mismatches += 1
                
                # If the number of differing characters is 2 or less,
                # the query word is a match.
                if mismatches <= 2:
                    result.append(query_word)
                    # We found a suitable word in the dictionary, so we can
                    # stop checking for the current query_word and move to the next.
                    break
                    
        return result