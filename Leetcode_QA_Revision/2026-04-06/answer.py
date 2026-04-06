class Solution:
    def robotSim(self, commands: list[int], obstacles: list[list[int]]) -> int:
        # Direction vectors: 0:North, 1:East, 2:South, 3:West
        # Corresponds to changes in (x, y)
        dx = [0, 1, 0, -1]
        dy = [1, 0, -1, 0]

        # Robot's state
        x, y = 0, 0
        direction = 0  # Start facing North

        # Result variable to track the maximum squared distance
        max_dist_sq = 0

        # For efficient O(1) average time obstacle lookup,
        # convert the list of obstacles to a set of tuples.
        obstacle_set = set(map(tuple, obstacles))

        for cmd in commands:
            if cmd == -1:  # Turn right 90 degrees
                direction = (direction + 1) % 4
            elif cmd == -2:  # Turn left 90 degrees
                # Turning left is equivalent to turning right 3 times
                direction = (direction + 3) % 4
            else:  # Move forward k units (where k = cmd)
                # Move one unit at a time to check for obstacles at each step.
                for _ in range(cmd):
                    next_x = x + dx[direction]
                    next_y = y + dy[direction]

                    # Check if the next position is an obstacle
                    if (next_x, next_y) in obstacle_set:
                        break  # Stop moving for this command if an obstacle is ahead
                    
                    # If not an obstacle, update the robot's position
                    x = next_x
                    y = next_y
                
                # After a move command, update the maximum squared distance.
                # The furthest point from the origin during a single linear move
                # will be one of its endpoints. Since the starting point's
                # distance was already considered, we only check the new endpoint.
                max_dist_sq = max(max_dist_sq, x*x + y*y)
        
        return max_dist_sq