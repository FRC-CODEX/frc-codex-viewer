package com.frc.codex.oim;

import java.io.IOException;
import java.util.zip.ZipOutputStream;
import com.frc.codex.model.Filing;
import com.frc.codex.model.OimFormat;

public interface ReportPackageProvider {

    public void writeReportPackage(Filing filing, OimFormat format, ZipOutputStream zos)
            throws IOException;

}
