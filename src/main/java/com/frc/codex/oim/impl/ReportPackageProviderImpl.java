package com.frc.codex.oim.impl;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.frc.codex.FilingIndexProperties;
import com.frc.codex.model.Filing;
import com.frc.codex.model.OimFormat;
import com.frc.codex.oim.ReportPackageProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
public class ReportPackageProviderImpl implements ReportPackageProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ReportPackageProviderImpl.class);
    private static byte[] REPORT_PACKAGE_JSON = "{\"documentInfo\": {\"documentType\": \"https://xbrl.org/report-package/2023\"}}".getBytes();

    private final FilingIndexProperties properties;
    private final S3Client s3Client;

    public ReportPackageProviderImpl(FilingIndexProperties properties, S3Client s3Client) {
        this.properties = requireNonNull(properties);
        this.s3Client = requireNonNull(s3Client);
    }

    @Override
    public void writeReportPackage(Filing filing, OimFormat format, ZipOutputStream zos) throws IOException {
        String s3Prefix = filing.getFilingId() + "/" + filing.getOimDirectory() + "/";
        String stld = filing.getFilenameStem();
        addPriorPackageFiles(filing, stld, s3Prefix, zos);
        addReportPackageJson(filing, stld, zos);
        addOIMReports(format, stld, s3Prefix, zos);
        zos.finish();
    }

    private void addPriorPackageFiles(Filing filing, String stld, String s3Prefix, ZipOutputStream zos) throws IOException {
        GetObjectRequest getReportRequest = GetObjectRequest.builder()
                .bucket(properties.s3ResultsBucketName())
                .key(s3Prefix + filing.getFilename())
                .build();
        try (ResponseInputStream<GetObjectResponse> ris = s3Client.getObject(getReportRequest);
                ZipInputStream zis = new ZipInputStream(ris)) {
            // Filing was a zip. Use it as our base because it may contain an extension taxonomy.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.endsWith("META-INF/reportPackage.json") || entryName.contains("/reports")) {
                    // Skip the report package json file and report files. We will add our own.
                    continue;
                }
                String[] parts = entryName.split("/", 2);
                String newEntryName = stld + "/" + (parts.length > 1 ? parts[1] : "");
                zos.putNextEntry(new ZipEntry(newEntryName));
                zis.transferTo(zos);
                zis.closeEntry();
                zos.closeEntry();
            }
        } catch (NoSuchKeyException e) {
            LOG.debug("Filing was not a package zip. Nothing to copy to the OIM report package.", e);
        }
    }

    private void addReportPackageJson(Filing filing, String stld, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(stld + "/META-INF/reportPackage.json"));
        zos.write(REPORT_PACKAGE_JSON, 0, REPORT_PACKAGE_JSON.length);
        zos.closeEntry();
    }

    private void addOIMReports(OimFormat format, String stld, String s3Prefix, ZipOutputStream zos) throws IOException {
        String s3OimReportsPrefix = s3Prefix + format.getFormat();
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(properties.s3ResultsBucketName())
                .prefix(s3OimReportsPrefix)
                .build();
        ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);
        if (listObjectsResponse.contents().isEmpty()) {
            throw new RuntimeException(String.format("No OIM reports found under path '%s'.", s3OimReportsPrefix)); 
        }
        for (S3Object s3Object : listObjectsResponse.contents()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(properties.s3ResultsBucketName())
                    .key(s3Object.key())
                    .build();

            try (ResponseInputStream<GetObjectResponse> ris = s3Client.getObject(getObjectRequest)) {
                String newEntryName = stld + "/reports" + s3Object.key().substring(s3OimReportsPrefix.length());
                zos.putNextEntry(new ZipEntry(newEntryName));
                ris.transferTo(zos);
                zos.closeEntry();
            }
        }
    }
}
