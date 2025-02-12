import datetime
import logging
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import cast

from arelle.api.Session import Session  # type: ignore
from arelle.packages.report.DetectReportPackage import isReportPackageExtension  # type: ignore
from arelle.RuntimeOptions import RuntimeOptions  # type: ignore

from processor.base.filing_download_result import FilingDownloadResult
from processor.base.job_message import JobMessage
from processor.base.worker import Worker, WorkerResult
from processor.processor_options import ProcessorOptions

VIEWER_HTML_FILENAME = 'ixbrlviewer.html'
OIM_DIRECTORY = 'OIM'
XBRL_CSV_DIRECTORY = 'CSV'
XBRL_JSON_DIRECTORY = 'JSON'


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class IxbrlViewerResult:
    success: bool
    logs: str
    company_name: str | None
    company_number: str | None
    document_date: datetime.datetime | None


class IxbrlViewerWorker(Worker):

    def __init__(self, processor_options: ProcessorOptions, http_cache_directory: Path | None = None):
        self._http_cache_directory = http_cache_directory or processor_options.http_cache_directory

    def work(
            self,
            job_message: JobMessage,
            filing_download: FilingDownloadResult,
            viewer_directory: Path,
            taxonomy_package_urls: list[str],
    ) -> WorkerResult:
        assert filing_download.download_path is not None
        result = self._generate_viewer(filing_download.download_path, viewer_directory, taxonomy_package_urls)
        if not result.success:
            return WorkerResult(
                job_message.filing_id,
                error='Viewer generation failed within Arelle. Check the logs for details.',
                logs=result.logs
            )
        viewer_path = viewer_directory / VIEWER_HTML_FILENAME
        if not viewer_path.exists():
            return WorkerResult(
                job_message.filing_id,
                error='Arelle reported success but viewer was not found. Check the logs for details.',
                logs=result.logs
            )
        oim_path = viewer_directory / OIM_DIRECTORY
        xbrl_csv_files = []
        xbrl_json_files = []
        for f in oim_path.iterdir():
            if f.name.endswith(('.csv', 'metadata.json')):
                xbrl_csv_files.append(f)
            elif f.name.endswith('.json'):
                xbrl_json_files.append(f)
            else:
                logger.error(f'Unexpected file found in OIM directory: {f.name}')
        if len(xbrl_csv_files) > 0:
            xbrl_csv_path = oim_path / XBRL_CSV_DIRECTORY
            xbrl_csv_path.mkdir(exist_ok=True)
            for f in xbrl_csv_files:
                shutil.move(f, xbrl_csv_path)
        if len(xbrl_json_files) > 0:
            xbrl_json_path = oim_path / XBRL_JSON_DIRECTORY
            xbrl_json_path.mkdir(exist_ok=True)
            for f in xbrl_json_files:
                shutil.move(f, xbrl_json_path)
        generated_oim_files = len(xbrl_csv_files) > 0 and len(xbrl_json_files) > 0
        if generated_oim_files and isReportPackageExtension(filing_download.download_path.name):
            # Save packages for constructing OIM versions of the packages.
            oim_package_dest = oim_path / filing_download.download_path.name
            shutil.copy(filing_download.download_path, oim_package_dest)
        return WorkerResult(
            job_message.filing_id,
            success=True,
            viewer_entrypoint=VIEWER_HTML_FILENAME,
            oim_directory=OIM_DIRECTORY if generated_oim_files else None,
            logs=result.logs,
            filename=filing_download.download_path.name,
            company_name=result.company_name,
            company_number=result.company_number,
            document_date=result.document_date,
        )

    def _get_value_by_local_name(self, model_xbrl, local_name: str) -> str | None:
        facts = model_xbrl.factsByLocalName.get(local_name, [])
        if facts:
            return next(iter(facts)).xValue
        return None

    def _generate_viewer(self, target_path: Path, viewer_directory: Path, packages: list[str]) -> IxbrlViewerResult:
        runtime_options = RuntimeOptions(
            cacheDirectory=str(self._http_cache_directory),
            disablePersistentConfig=True,
            entrypointFile=str(target_path),
            internetLogDownloads=True,
            internetRecheck='never',
            keepOpen=True,
            logFormat="[%(messageCode)s] %(message)s - %(file)s",
            logFile='logToBuffer',
            packages=packages,
            pluginOptions={
                'saveLoadableOIMDirectory': str(viewer_directory / OIM_DIRECTORY),
                'saveViewerDest': str(viewer_directory),
                'useStubViewer': True,
                'viewerNoCopyScript': True,
                'viewerURL': '/ixbrlviewer.js',
                'viewer_feature_mandatory_facts': 'companies-house'
            },
            plugins="inlineXbrlDocumentSet|ixbrl-viewer|saveLoadableOIM",
        )
        with Session() as session:
            success = session.run(runtime_options)
            company_name: str | None = None
            company_number: str | None = None
            document_date: datetime.datetime | None = None
            model_xbrls = session.get_models()
            if model_xbrls:
                model_xbrl = model_xbrls[0]
                company_name = self._get_value_by_local_name(model_xbrl, 'EntityCurrentLegalOrRegisteredName')
                company_number = self._get_value_by_local_name(model_xbrl, 'UKCompaniesHouseRegisteredNumber')
                document_date = cast(datetime.datetime, self._get_value_by_local_name(model_xbrl, 'BalanceSheetDate'))
            logs = session.get_logs('text', clear_logs=True)
            return IxbrlViewerResult(
                success=success,
                logs=logs,
                company_name=company_name,
                company_number=company_number,
                document_date=document_date,
            )
