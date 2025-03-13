import os

import boto3
import uuid

from support.actions.base_action import BaseAction


class DeleteFilingAction(BaseAction):

    def _run(self, options, cursor) -> tuple[bool, str, list | int | dict]:
        if 'filing_id' not in options:
            return False, "Must provide 'filing_id'.", 0
        filing_id = options['filing_id']
        try:
            uuid.UUID(filing_id)
        except ValueError:
            return False, f"filing_id must be a valid UUID: {filing_id}", 0

        if 'test_mode' not in options:
            return False, "Must provide 'test_mode'.", 0
        test_mode = bool(options['test_mode'])

        if test_mode:
            query = "SELECT COUNT(*) as count FROM filings WHERE filing_id = %s AND status != 'deleted';"
            cursor.execute(query, (filing_id,))
            stats = BaseAction.collect_stats(cursor)
            rows_affected = stats['count']
            message = f"Test matched {rows_affected} filing(s) to change status to 'deleted'."
            deleted_items = self.delete_subfolder(filing_id, True)
            message += f" Test matched {len(deleted_items)} item(s) to delete from S3."
        else:
            query = "UPDATE filings SET status = 'deleted' WHERE filing_id = %s AND status != 'deleted';"
            cursor.execute(query, (filing_id,))
            rows_affected = cursor.rowcount
            message = f"Changed {rows_affected} filing(s) status to 'deleted'."
            deleted_items = self.delete_subfolder(filing_id, False)
            message += f" Deleted {len(deleted_items)} item(s) from S3."
        return True, message, {
            'deleted_items': deleted_items,
            'rows_affected': rows_affected,
            'test_mode': test_mode,
        }

    def delete_subfolder(self, subfolder_key: str, test_mode: bool) -> list[tuple[str, str]]:
        region_name = str(os.getenv('S3_REGION_NAME'))
        bucket_name = str(os.getenv('S3_RESULTS_BUCKET_NAME'))
        s3_client = boto3.client(
            's3',
            region_name=region_name,
        )

        # List objects in the subfolder
        response = s3_client.list_objects_v2(Bucket=bucket_name, Prefix=subfolder_key)

        deleted_items = []
        # Delete each object in the subfolder
        if 'Contents' in response:
            for obj in response['Contents']:
                key = str(obj['Key'])
                if not test_mode:
                    s3_client.delete_object(Bucket=bucket_name, Key=key)
                deleted_items.append((bucket_name, key))
        return deleted_items
