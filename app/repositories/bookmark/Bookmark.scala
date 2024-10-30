package repositories.bookmark

import java.util.UUID

case class Bookmark(
    reportId: UUID,
    userId: UUID
    // There's also a creation_date column
    // We shouldn't need it in the code
)
