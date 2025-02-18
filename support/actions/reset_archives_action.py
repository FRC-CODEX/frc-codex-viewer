from support.actions.base_action import BaseAction


class ResetArchivesAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, list | int]:
        params = []
        conditions = []
        notes = []
        if 'archive_type' not in options and \
                'filename' not in options and \
                'completed_after' not in options and \
                'completed_before' not in options:
            return False, "Must provide at least one of 'archive_type', 'filename', 'completed_after', or 'completed_before'.", 0
        if options.get('archive_type'):
            conditions.append("archive_type = %s")
            params.append(options['archive_type'])
            notes.append(f"Archive type is {options['archive_type']}.")
        if options.get('filename'):
            conditions.append("filename LIKE %s")
            params.append(options['filename'])
            notes.append(f"Filename matches {options['filename']}.")
        if options.get('completed_after'):
            conditions.append("completed_date >= %s")
            params.append(options['completed_after'])
            notes.append(f"Completion date is after {options['completed_after']}.")
        if options.get('completed_before'):
            conditions.append("completed_date < %s")
            params.append(options['completed_before'])
            notes.append(f"Completion date is before {options['completed_before']}.")

        query = "DELETE FROM ch_archives WHERE " + " AND ".join(conditions) + ';'
        cursor.execute(query, tuple(params))
        rows_affected = cursor.rowcount
        message = f"Reset {rows_affected} archives with the following filter(s): " + ' '.join(notes)
        return True, message, rows_affected
