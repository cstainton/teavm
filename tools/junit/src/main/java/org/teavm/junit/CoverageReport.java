/*
 *  Copyright 2026 TeaVM contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.junit;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.teavm.backend.javascript.coverage.JavaScriptCoverage;

/** Merges browser hits for this JVM; each fork owns a separate Sonar report. */
final class CoverageReport {
    private final List<Path> sourceRoots = new ArrayList<>();
    private final Map<String, Path> resolvedSources = new TreeMap<>();
    private final Map<String, Map<Integer, Boolean>> files = new TreeMap<>();
    private final Path output;

    CoverageReport(String sourceDirs, Path output) {
        if (sourceDirs == null || sourceDirs.isBlank()) {
            throw new IllegalArgumentException("Coverage requires teavm.junit.sourceDirs to select project sources");
        }
        for (var directory : sourceDirs.split(Pattern.quote(File.pathSeparator))) {
            if (!directory.isBlank()) {
                sourceRoots.add(Path.of(directory).toAbsolutePath().normalize());
            }
        }
        this.output = output;
    }

    synchronized boolean includes(String source) {
        return resolve(source) != null;
    }

    private Path resolve(String source) {
        if (resolvedSources.containsKey(source)) {
            return resolvedSources.get(source);
        }
        Path found = null;
        var relative = Path.of(source);
        if (!relative.isAbsolute() && relative.normalize().equals(relative)
                && !relative.startsWith("..")) {
            for (var root : sourceRoots) {
                var candidate = root.resolve(relative).normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
                    if (found != null && !found.equals(candidate)) {
                        throw new IllegalArgumentException("Ambiguous coverage source: " + source);
                    }
                    found = candidate;
                }
            }
        }
        resolvedSources.put(source, found);
        return found;
    }

    void include(JavaScriptCoverage coverage) {
        var baseline = new TreeMap<String, Map<Integer, Boolean>>();
        coverage.getEmittedLines().forEach((file, numbers) -> {
            var lines = new TreeMap<Integer, Boolean>();
            numbers.forEach(number -> lines.put(number, false));
            baseline.put(file, lines);
        });
        merge(baseline);
    }

    synchronized void merge(Map<String, Map<Integer, Boolean>> snapshot) {
        for (var file : snapshot.entrySet()) {
            var source = resolve(file.getKey());
            if (source == null) {
                continue;
            }
            var lines = files.computeIfAbsent(source.toString(), key -> new TreeMap<>());
            for (var line : file.getValue().entrySet()) {
                lines.merge(line.getKey(), line.getValue(), Boolean::logicalOr);
            }
        }
        try {
            write();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write() throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        var temporary = Files.createTempFile(output.toAbsolutePath().getParent(), "coverage-", ".xml.tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
                xml.writeStartElement("coverage");
                xml.writeAttribute("version", "1");
                for (var file : files.entrySet()) {
                    xml.writeStartElement("file");
                    xml.writeAttribute("path", file.getKey());
                    for (var line : file.getValue().entrySet()) {
                        xml.writeEmptyElement("lineToCover");
                        xml.writeAttribute("lineNumber", line.getKey().toString());
                        xml.writeAttribute("covered", line.getValue().toString());
                    }
                    xml.writeEndElement();
                }
                xml.writeEndElement();
                xml.flush();
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } catch (XMLStreamException e) {
            throw new IOException("Could not write Sonar coverage report", e);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
