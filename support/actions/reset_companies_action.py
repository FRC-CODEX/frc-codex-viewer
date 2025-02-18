from support.actions.base_action import BaseAction


class ResetCompaniesAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, list | int]:
        params = []
        conditions = []
        notes = []

        value = options.get('value')
        if value:
            conditions.append("completed_date IS NULL")
            params.append(value)
        else:
            conditions.append("completed_date IS NOT NULL")
            params.append(None)
        if 'company_number' not in options and \
                'completed_after' not in options and \
                'completed_before' not in options and \
                'discovered_after' not in options and \
                'discovered_before' not in options:
            return False, "Must provide at least one of 'company_number', " \
                          "'completed_after', 'completed_before', 'discovered_after', " \
                          "or 'discovered_before'.", 0
        if options.get('company_number'):
            conditions.append("company_number = %s")
            params.append(options['company_number'])
            notes.append(f"Company number is {options['company_number']}.")
        if options.get('completed_after'):
            conditions.append("completed_date >= %s")
            params.append(options['completed_after'])
            notes.append(f"Completion date is after {options['completed_after']}.")
        if options.get('completed_before'):
            conditions.append("completed_date < %s")
            params.append(options['completed_before'])
            notes.append(f"Completion date is before {options['completed_before']}.")
        if options.get('discovered_after'):
            conditions.append("discovered_date >= %s")
            params.append(options['discovered_after'])
            notes.append(f"Discovery date is after {options['discovered_after']}.")
        if options.get('discovered_before'):
            conditions.append("discovered_date < %s")
            params.append(options['discovered_before'])
            notes.append(f"Discovery date is before {options['discovered_before']}.")

        query = "UPDATE companies SET completed_date = %s WHERE " + " AND ".join(conditions) + ';'
        cursor.execute(query, tuple(params))
        rows_affected = cursor.rowcount
        if value:
            message = f"Completed {rows_affected} companies with the following filter(s): " + ' '.join(notes)
        else:
            message = f"Reset {rows_affected} companies with the following filter(s): " + ' '.join(notes)
        return True, message, rows_affected
