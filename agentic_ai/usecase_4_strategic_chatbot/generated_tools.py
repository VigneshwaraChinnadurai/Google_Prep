def calculate_market_share_delta(previous_revenue, current_revenue):
    """Return market share delta percentage points for each provider."""
    prev_total = sum(previous_revenue.values())
    curr_total = sum(current_revenue.values())

    if prev_total <= 0 or curr_total <= 0:
        raise ValueError("Total revenue must be positive for both periods")

    deltas = {}
    for key in current_revenue:
        prev_share = (previous_revenue[key] / prev_total) * 100.0
        curr_share = (current_revenue[key] / curr_total) * 100.0
        deltas[key] = round(curr_share - prev_share, 3)
    return deltas
