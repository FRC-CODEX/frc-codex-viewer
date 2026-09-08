from collections import defaultdict
from typing import Any

from support.actions.base_action import BaseAction


class ListErrorsAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, Any]:
        query = "SELECT filing_id, error FROM filings WHERE status = 'failed'"
        cursor.execute(query)
        rows = cursor.fetchall()
        errors = defaultdict(list)
        for row in rows:
            filing_id, error = row
            errors[error or "[None]"].append(filing_id)
        count = sum(len(e) for e in errors.values())
        message = f"Found {count} failed filing(s)."
        return True, message, errors
