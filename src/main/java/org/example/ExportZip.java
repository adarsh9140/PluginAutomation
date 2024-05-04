package org.example;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportZip {

    public static boolean convertFolderToZip(String folderPath, String zipOutputPath,String packageName) {
        try {
            Path folder = Paths.get(folderPath);

            if (!Files.exists(folder) || !Files.isDirectory(folder)) {
                return false;
            }
            String zipFileName = zipOutputPath + "/" +packageName+".zip";
            Path zipFilePath = Paths.get(zipFileName);

            if (!Files.exists(zipFilePath.getParent())) {
                Files.createDirectories(zipFilePath.getParent());
            }

            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
                Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = folder.relativize(file).toString().replace(File.separator, "/");
                        zipOut.putNextEntry(new ZipEntry(entryName));

                        Files.copy(file, zipOut);

                        zipOut.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (!dir.equals(folder)) {
                            String entryName = folder.relativize(dir).toString().replace(File.separator, "/") + "/";
                            zipOut.putNextEntry(new ZipEntry(entryName));
                            zipOut.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
