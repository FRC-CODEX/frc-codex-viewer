from typing import Any

from support.actions.base_action import BaseAction


class GetChIndexingStatsAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, Any]:
        query = "SELECT " \
            "(SELECT COUNT(*) FROM ch_archives WHERE completed_date IS NULL) AS incomplete_archives, " \
            "(SELECT COUNT(*) FROM ch_archives) AS total_archives, " \
            "(SELECT MAX(completed_date) FROM ch_archives) AS latest_archive_completed, " \
            "(SELECT COUNT(*) FROM companies WHERE completed_date IS NULL) AS incomplete_companies, " \
            "(SELECT COUNT(*) FROM companies) AS total_companies, " \
            "(SELECT MAX(completed_date) FROM companies) AS latest_company_completed "
        cursor.execute(query)
        stats = BaseAction.collect_stats(cursor)
        message = "Companies House indexing statistics retrieved."
        return True, message, stats
