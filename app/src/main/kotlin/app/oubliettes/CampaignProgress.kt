package app.oubliettes

/** The next unfinished level after [current], or 0 for the level list when none remains. */
internal fun nextCampaignLevel(current: Int, total: Int, isDone: (Int) -> Boolean): Int =
    ((current + 1)..total).firstOrNull { !isDone(it) } ?: 0
