package org.midorinext.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTransform
public abstract class WithoutTelemetryAarTransform implements TransformAction<TransformParameters.None> {
    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();
        byte[] classes;
        try (ZipFile archive = new ZipFile(input)) {
            ZipEntry jar = archive.getEntry("classes.jar");
            if (jar == null) {
                outputs.file(input);
                return;
            }
            try (java.io.InputStream source = archive.getInputStream(jar)) {
                classes = source.readAllBytes();
            }
            byte[] clean = stripJar(classes);
            if (java.util.Arrays.equals(classes, clean)) {
                outputs.file(input);
                return;
            }
            classes = clean;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        File output = outputs.file(input.getName());
        try (ZipInputStream source = new ZipInputStream(java.nio.file.Files.newInputStream(input.toPath()));
                ZipOutputStream target = new ZipOutputStream(java.nio.file.Files.newOutputStream(output.toPath()))) {
            ZipEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                byte[] data = source.readAllBytes();
                if (entry.getName().equals("classes.jar")) {
                    data = classes;
                } else if (entry.getName().equals("proguard.txt")) {
                    String rules = new String(data, StandardCharsets.UTF_8);
                    // Removed metric classes cannot be retained by the upstream consumer rules.
                    rules = rules.replaceAll("(?m)^.*(?:GleanMetrics|mozilla[./]telemetry[./]glean).*(?:\\R|$)", "");
                    data = rules.getBytes(StandardCharsets.UTF_8);
                }
                writeEntry(target, entry.getName(), data);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static byte[] stripJar(byte[] jar) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean changed = false;
        try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(jar));
                ZipOutputStream target = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                byte[] data = source.readAllBytes();
                if (entry.getName().endsWith(".class")) {
                    byte[] original = data;
                    data = MozillaTelemetryStripper.strip(original);
                    if (data != null && entry.getName().equals(GeckoPortLifecyclePatch.PORT + ".class")) {
                        data = GeckoPortLifecyclePatch.apply(data);
                    }
                    changed |= !java.util.Arrays.equals(original, data);
                    if (data != null) TelemetryReferenceAudit.assertClean(data);
                }
                if (data != null) writeEntry(target, entry.getName(), data);
            }
        }
        return changed ? bytes.toByteArray() : jar;
    }

    private static void writeEntry(ZipOutputStream target, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        target.putNextEntry(entry);
        target.write(data);
        target.closeEntry();
    }
}
