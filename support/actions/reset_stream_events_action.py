from support.actions.base_action import BaseAction


class ResetStreamEventsAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, list | int]:
        params = []
        conditions = []
        notes = []
        if 'created_after' not in options and \
                'created_before' not in options and \
                'timepoint_after' not in options and \
                'timepoint_before' not in options:
            return False, "Must provide at least one of 'created_after', " \
                          "'created_before', 'timepoint_after', or " \
                          "'timepoint_before'.", 0
        if options.get('created_after'):
            conditions.append("created_date >= %s")
            params.append(options['created_after'])
            notes.append(f"Creation date is after {options['created_after']}.")
        if options.get('created_before'):
            conditions.append("created_date < %s")
            params.append(options['created_before'])
            notes.append(f"Creation date is before {options['created_before']}.")
        if options.get('timepoint_after'):
            conditions.append("timepoint >= %s")
            params.append(options['timepoint_after'])
            notes.append(f"Timepoint is after {options['timepoint_after']}.")
        if options.get('timepoint_before'):
            conditions.append("timepoint < %s")
            params.append(options['timepoint_before'])
            notes.append(f"Timepoint is before {options['timepoint_before']}.")

        query = "DELETE FROM stream_events WHERE " + " AND ".join(conditions) + ';'
        cursor.execute(query, tuple(params))
        rows_affected = cursor.rowcount
        message = f"Deleted {rows_affected} stream events with the following filter(s): " + ' '.join(notes)
        return True, message, rows_affected
