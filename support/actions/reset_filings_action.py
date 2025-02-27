import uuid

from support.actions.base_action import BaseAction


class ResetFilingsAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, list | int]:
        params = []
        conditions = []
        notes = []
        if 'company_number' not in options and 'filing_id' not in options:
            return False, "Must provide 'company_number', 'filing_id', or both.", 0
        if options.get('company_number') is not None:
            conditions.append("company_number = %s")
            params.append(options['company_number'])
            notes.append(f"Company number is {options['company_number']}.")
        if options.get('filing_id') is not None:
            filing_id = options['filing_id']
            try:
                uuid.UUID(filing_id)
            except ValueError:
                return False, f"filing_id must be a valid UUID: {filing_id}", 0
            conditions.append("filing_id = %s")
            params.append(filing_id)
            notes.append(f"Filing ID is {options['filing_id']}.")
        if options.get('status') is not None:
            conditions.append("status = %s")
            params.append(options['status'])
            notes.append(f"Status is {options['status']}.")

        query = "UPDATE filings SET status = 'pending' WHERE status != 'pending' AND " + " AND ".join(conditions) + ';'
        cursor.execute(query, tuple(params))
        rows_affected = cursor.rowcount
        message = f"Reset {rows_affected} filing(s) with the following filter(s): " + ' '.join(notes)
        return True, message, rows_affected
