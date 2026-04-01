class Solution:
    def survivedRobotsHealths(self, positions: List[int], healths: List[int], directions: str) -> List[int]:
        n = len(positions)
        
        # Create a list of robot properties, including their original index
        # Format: [position, health, direction, original_index]
        robots = []
        for i in range(n):
            robots.append([positions[i], healths[i], directions[i], i])
            
        # Sort robots based on their positions to process them from left to right
        robots.sort()
        
        # Stack to keep track of robots moving to the right ('R')
        # These are candidates for collision with any left-moving robot we encounter later.
        # We store the robot objects (which are lists) themselves in the stack.
        # Since lists are mutable, changes to a robot in the stack will reflect
        # in the main `robots` list, which simplifies state tracking.
        stack = []
        
        for robot in robots:
            if robot[2] == 'R':
                # If a robot is moving right, it can't collide with anything processed so far
                # (as they are all to its left). Push it onto the stack to await
                # collisions with future left-moving robots.
                stack.append(robot)
            else: # robot is moving left ('L')
                # This left-moving robot will collide with any right-moving robots on the stack.
                # We process collisions until this robot is destroyed or the stack is empty.
                while stack and robot[1] > 0:
                    # Get the right-moving robot at the top of the stack
                    top_robot = stack[-1]
                    
                    # Collision occurs
                    if top_robot[1] > robot[1]:
                        # The right-moving robot (top_robot) wins.
                        # Its health decreases by 1.
                        top_robot[1] -= 1
                        # The current left-moving robot is destroyed.
                        robot[1] = 0
                    elif top_robot[1] < robot[1]:
                        # The current left-moving robot wins.
                        # The right-moving robot (top_robot) is destroyed.
                        stack.pop()
                        top_robot[1] = 0
                        # The current robot's health decreases by 1 and it continues
                        # to check for collisions with the next robot on the stack.
                        robot[1] -= 1
                    else: # top_robot[1] == robot[1]
                        # Both robots have the same health, so both are destroyed.
                        stack.pop()
                        top_robot[1] = 0
                        robot[1] = 0
                        
        # After all collisions are resolved, collect the healths of surviving robots.
        # The `robots` list now contains the final state of all robots.
        # A robot has survived if its health is greater than 0.
        survivors = []
        for robot in robots:
            if robot[1] > 0:
                # We need to return the healths in the order of their original indices.
                # Store [original_index, health] pairs for sorting.
                survivors.append([robot[3], robot[1]])
                
        # Sort the survivors based on their original index
        survivors.sort()
        
        # Extract just the healths to form the final result
        result = [health for index, health in survivors]
        
        return result