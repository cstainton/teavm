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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.teavm.backend.javascript.codegen.OutputSourceWriterBuilder;
import org.teavm.backend.javascript.coverage.JavaScriptCoverage;
import org.teavm.model.TextLocation;

public class CoverageReportTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void mergesHitsWithoutLosingUntouchedLines() throws Exception {
        var sources = temporary.newFolder("src").toPath();
        Files.writeString(sources.resolve("A&B.java"), "class Example {}\n");
        var output = temporary.getRoot().toPath().resolve("reports/sonar.xml");
        var report = new CoverageReport(sources.toString(), output);
        report.merge(Map.of("A&B.java", Map.of(1, true, 2, false), "External.java", Map.of(1, true)));
        report.merge(Map.of("A&B.java", Map.of(1, false, 3, true)));
        var xml = Files.readString(output);
        assertTrue(xml.contains("A&amp;B.java"));
        assertTrue(xml.contains("lineNumber=\"1\" covered=\"true\""));
        assertTrue(xml.contains("lineNumber=\"2\" covered=\"false\""));
        assertTrue(xml.contains("lineNumber=\"3\" covered=\"true\""));
        assertFalse(xml.contains("External.java"));
        assertFalse(xml.contains("branchesToCover"));
    }

    @Test
    public void compiledButNeverLoadedCodeIsUncovered() throws Exception {
        var sources = temporary.newFolder("baseline").toPath();
        Files.writeString(sources.resolve("Example.java"), "class Example {}\n");
        var output = temporary.getRoot().toPath().resolve("baseline.xml");
        var report = new CoverageReport(sources.toString(), output);
        var coverage = new JavaScriptCoverage(report::includes);
        var writer = new OutputSourceWriterBuilder(null).build(new StringBuilder());
        coverage.emitProbe(writer, new TextLocation("Example.java", 7));
        report.include(coverage);
        assertTrue(Files.readString(output).contains("lineNumber=\"7\" covered=\"false\""));
    }

    @Test
    public void rejectsAmbiguousAndEscapingPaths() throws Exception {
        var first = temporary.newFolder("first").toPath();
        var second = temporary.newFolder("second").toPath();
        Files.writeString(first.resolve("Same.java"), "");
        Files.writeString(second.resolve("Same.java"), "");
        var report = new CoverageReport(first + File.pathSeparator + second,
                temporary.getRoot().toPath().resolve("sonar.xml"));
        assertThrows(IllegalArgumentException.class, () -> report.includes("Same.java"));
        assertFalse(report.includes("../second/Same.java"));
        assertFalse(report.includes(first.resolve("Same.java").toString()));
    }
}
