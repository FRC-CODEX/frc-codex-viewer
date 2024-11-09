package com.frc.codex.oim.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.frc.codex.FilingIndexProperties;
import com.frc.codex.model.Filing;
import com.frc.codex.model.OimFormat;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
public class ReportPackageProviderImplTest {

    @Mock
    private FilingIndexProperties properties;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private ReportPackageProviderImpl reportPackageProvider;

    private String s3BucketName;
    private Filing filing;


    @BeforeEach
    public void setUp() {
        s3BucketName = "testBucket";
        filing = Filing.builder()
            .filingId("53bb93fb-e21c-4558-acff-0fc2cece5f5e")
            .oimDirectory("testOimDirectory")
            .filename("testFilingName.xbri")
            .build();

        when(properties.s3ResultsBucketName()).thenReturn(s3BucketName);
    }

    @Test
    public void testCsv() throws IOException {
        List<String> priorPackageFiles = List.of(
            "stld/META-INF/reportPackage.json",
            "stld/META-INF/catalog.xml",
            "stld/META-INF/taxonomyPackage.xml",
            "stld/example.com/schema.xsd",
            "stld/example.com/linkbase.xml",
            "stld/reports/report.xhtml",
            "stld/reports/report.jpg"
        );
        OimFormat format = OimFormat.CSV;
        List<String> oimFiles = List.of(
            "filing.csv",
            "filing.json"
        );
        mockS3(priorPackageFiles, format, oimFiles);

        List<String> reportPackageEntries = writeReportPackageEntries(format);

        List<String> expectedFiles = List.of(
            "testFilingName/META-INF/catalog.xml",
            "testFilingName/META-INF/reportPackage.json",
            "testFilingName/META-INF/taxonomyPackage.xml",
            "testFilingName/example.com/linkbase.xml",
            "testFilingName/example.com/schema.xsd",
            "testFilingName/reports/filing.csv",
            "testFilingName/reports/filing.json"
        );
        Collections.sort(reportPackageEntries);

        assertEquals(expectedFiles, reportPackageEntries);
    }

    @Test
    public void testJson() throws IOException {
        List<String> priorPackageFiles = List.of(
            "stld/META-INF/reportPackage.json",
            "stld/META-INF/catalog.xml",
            "stld/META-INF/taxonomyPackage.xml",
            "stld/example.com/schema.xsd",
            "stld/example.com/linkbase.xml",
            "stld/reports/report.xhtml",
            "stld/reports/report.jpg"
        );
        OimFormat format = OimFormat.JSON;
        List<String> oimFiles = List.of(
            "filing.json"
        );
        mockS3(priorPackageFiles, format, oimFiles);

        List<String> reportPackageEntries = writeReportPackageEntries(format);

        List<String> expectedFiles = List.of(
            "testFilingName/META-INF/catalog.xml",
            "testFilingName/META-INF/reportPackage.json",
            "testFilingName/META-INF/taxonomyPackage.xml",
            "testFilingName/example.com/linkbase.xml",
            "testFilingName/example.com/schema.xsd",
            "testFilingName/reports/filing.json"
        );
        Collections.sort(reportPackageEntries);

        assertEquals(expectedFiles, reportPackageEntries);
    }

    @Test
    public void testCsvNoPriorFiles() throws IOException {
        List<String> priorPackageFiles = null;
        OimFormat format = OimFormat.CSV;
        List<String> oimFiles = List.of(
            "filing.csv",
            "filing.json"
        );
        mockS3(priorPackageFiles, format, oimFiles);

        List<String> reportPackageEntries = writeReportPackageEntries(format);

        List<String> expectedFiles = List.of(
            "testFilingName/META-INF/reportPackage.json",
            "testFilingName/reports/filing.csv",
            "testFilingName/reports/filing.json"
        );
        Collections.sort(reportPackageEntries);

        assertEquals(expectedFiles, reportPackageEntries);
    }

    @Test
    public void testJsonNoPriorFiles() throws IOException {
        List<String> priorPackageFiles = null;
        OimFormat format = OimFormat.JSON;
        List<String> oimFiles = List.of(
            "filing.json"
        );
        mockS3(priorPackageFiles, format, oimFiles);

        List<String> reportPackageEntries = writeReportPackageEntries(format);

        List<String> expectedFiles = List.of(
            "testFilingName/META-INF/reportPackage.json",
            "testFilingName/reports/filing.json"
        );
        Collections.sort(reportPackageEntries);

        assertEquals(expectedFiles, reportPackageEntries);
    }

    @Test
    public void testNoOimFilesFound() throws IOException {
        List<String> priorPackageFiles = null;
        OimFormat format = OimFormat.CSV;
        List<String> oimFiles = List.of();
        mockS3(priorPackageFiles, format, oimFiles);

        assertThrows(RuntimeException.class, () -> writeReportPackageEntries(format));
    }

    private void mockS3(List<String> priorPackageFiles, OimFormat format, List<String> oimFiles) throws IOException {
        mockPriorFilingZipFiles(priorPackageFiles);
        mockOimFiles(format, oimFiles);
    }

    private void mockPriorFilingZipFiles(List<String> priorPackageFiles) throws IOException {
        GetObjectRequest getFilingObjectRequest = GetObjectRequest.builder()
            .bucket(s3BucketName)
            .key(String.format("%s/%s/%s", filing.getFilingId(), filing.getOimDirectory(), filing.getFilename()))
            .build();

        if (priorPackageFiles == null) {
            when(s3Client.getObject(getFilingObjectRequest)).thenThrow(NoSuchKeyException.class);
            return;
        }
        try (ByteArrayOutputStream boas = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(boas)) {
            for (String priorPackageFile : priorPackageFiles) {
                zos.putNextEntry(new ZipEntry(priorPackageFile));
                zos.closeEntry();
            }
            zos.finish();
            ByteArrayInputStream bais = new ByteArrayInputStream(boas.toByteArray());
            ResponseInputStream<GetObjectResponse> ris = new ResponseInputStream<>(GetObjectResponse.builder().build(), bais);

            when(s3Client.getObject(getFilingObjectRequest)).thenReturn(ris);
        }
    }

    private void mockOimFiles(OimFormat format, List<String> oimFiles) throws IOException {
        String s3OimKeyPrefix = String.format("%s/%s/%s", filing.getFilingId(), filing.getOimDirectory(), format.getFormat());
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
            .bucket(s3BucketName)
            .prefix(s3OimKeyPrefix)
            .build();
        List<S3Object> s3Objects = oimFiles.stream()
                .map(e -> S3Object.builder()
                    .key(String.format("%s/%s", s3OimKeyPrefix, e))
                    .build())
                .toList();
        ListObjectsV2Response listObjectsResponse = ListObjectsV2Response.builder()
            .contents(s3Objects)
            .build();

        when(s3Client.listObjectsV2(listObjectsRequest)).thenReturn(listObjectsResponse);

        for (S3Object s3Object : s3Objects) {
            ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
            ResponseInputStream<GetObjectResponse> ris = new ResponseInputStream<>(GetObjectResponse.builder().build(), bais);
            when(s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3Object.key())
                .build())
            ).thenReturn(ris);
        }
    }

    private List<String> writeReportPackageEntries(OimFormat oimFormat) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {
            reportPackageProvider.writeReportPackage(filing, oimFormat, zos);

            List<String> entries = new ArrayList<>();
            try (ZipInputStream zis =
                    new ZipInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    entries.add(entry.getName());
                }
            }
            return entries;
        }
    }
}
