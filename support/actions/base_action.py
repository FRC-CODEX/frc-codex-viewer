import os
from abc import ABC, abstractmethod

import psycopg2


class BaseAction(ABC):

    @abstractmethod
    def _run(self, options, connection):
        pass

    def run(self, options) -> tuple[bool, str, list | int]:
        connection = psycopg2.connect(
            dbname=os.getenv('DB_DATABASE'),
            user=os.getenv('DB_USERNAME'),
            password=os.getenv('DB_PASSWORD'),
            host=os.getenv('DB_HOST'),
            port=os.getenv('DB_PORT')
        )
        try:
            cursor = connection.cursor()
            try:
                return self._run(options, cursor)
            finally:
                connection.commit()
                cursor.close()
        finally:
            connection.close()

    @staticmethod
    def collect_stats(cursor):
        column_names = [desc[0] for desc in cursor.description]
        first_row = cursor.fetchone()
        stats = dict(zip(column_names, first_row))
        return {
            k: str(v) for k, v in stats.items()
        }
