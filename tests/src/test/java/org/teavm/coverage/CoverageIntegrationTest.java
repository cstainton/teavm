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
package org.teavm.coverage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.teavm.backend.javascript.JSModuleType;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.backend.javascript.coverage.JavaScriptCoverage;
import org.teavm.browserrunner.BrowserRunDescriptor;
import org.teavm.browserrunner.BrowserRunner;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMOptimizationLevel;

public class CoverageIntegrationTest {
    private static final String SOURCE = "org/teavm/coverage/CoverageSubject.java";

    @Test(timeout = 120000)
    public void ordinaryJavaScript() throws Exception {
        verify(false, false);
    }

    @Test(timeout = 120000)
    public void optimizedMinifiedModule() throws Exception {
        verify(true, true);
    }

    @Test(timeout = 120000)
    public void disabledProducesNoProbes() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getProperty("teavm.junit.js", "true")));
        var directory = compile("disabled", false, false, false);
        assertFalse(Files.readString(directory.resolve("test.js")).contains(JavaScriptCoverage.GLOBAL_NAME));
        assertFalse(Files.exists(directory.resolve("test.js.coverage.xml")));
    }

    private void verify(boolean optimized, boolean module) throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getProperty("teavm.junit.js", "true")));
        var directory = compile(optimized ? "optimized" : "ordinary", true, optimized, module);
        var inventory = Files.readString(directory.resolve("test.js.coverage.xml"));
        var markers = sourceMarkers();
        assertTrue(inventory.contains("number=\"" + markers.get("excluded") + "\" state=\"not-emitted\""));
        assertFalse(Files.readString(directory.resolve("test.js")).contains("987654"));
        var runner = new BrowserRunner(directory.toFile(), "JAVASCRIPT",
                BrowserRunner.pickBrowser(System.getProperty("teavm.junit.js.runner", "browser-chrome")), false);
        runner.start();
        try {
            var snapshots = new java.util.ArrayList<Map<String, Map<Integer, Boolean>>>();
            var first = descriptor(module, null);
            first.setCoverageConsumer(snapshots::add);
            runner.runTest(first);
            var lines = snapshots.get(0).get(SOURCE);
            var evidence = new StringBuilder("line\thit\n");
            lines.forEach((line, hit) -> evidence.append(line).append('\t').append(hit).append('\n'));
            Files.writeString(directory.resolve("first-run-lines.tsv"), evidence);
            assertEquals(Boolean.TRUE, lines.get(markers.get("initialization")));
            if (optimized) {
                // The constant-return helper is folded into the field initializer, not executed.
                assertFalse(lines.containsKey(markers.get("initializer")));
                assertTrue(inventory.contains("number=\"" + markers.get("initializer")
                        + "\" state=\"not-emitted\""));
            } else {
                assertEquals(Boolean.TRUE, lines.get(markers.get("initializer")));
            }
            assertEquals(Boolean.TRUE, lines.get(markers.get("taken")));
            assertEquals(Boolean.FALSE, lines.get(markers.get("missed")));
            assertEquals(Boolean.FALSE, lines.get(markers.get("shortCircuit")));
            assertEquals(Boolean.TRUE, lines.get(markers.get("afterAsync")));
            assertFalse(lines.containsKey(markers.get("excluded")));

            var second = descriptor(module, "other");
            second.setCoverageConsumer(snapshots::add);
            runner.runTest(second);
            lines = snapshots.get(1).get(SOURCE);
            assertEquals(Boolean.TRUE, lines.get(markers.get("initialization")));
            assertEquals(Boolean.TRUE, lines.get(markers.get("taken")));
            assertEquals(Boolean.TRUE, lines.get(markers.get("missed")));
            assertEquals(Boolean.TRUE, lines.get(markers.get("shortCircuit")));

            var failed = descriptor(module, "fail");
            failed.setCoverageConsumer(snapshots::add);
            assertThrows(RuntimeException.class, () -> runner.runTest(failed));
            assertEquals(Boolean.TRUE, snapshots.get(2).get(SOURCE).get(markers.get("failure")));
        } finally {
            runner.stop();
        }
    }

    private BrowserRunDescriptor descriptor(boolean module, String argument) {
        return new BrowserRunDescriptor("coverage", "tests/test.js", module, List.of(), argument, true);
    }

    private Path compile(String name, boolean coverage, boolean optimized, boolean module) throws Exception {
        var directory = new File(System.getProperty("teavm.junit.target"), "coverage-integration/" + name).toPath();
        Files.createDirectories(directory);
        var target = new JavaScriptTarget();
        target.setObfuscated(optimized);
        if (module) {
            target.setModuleType(JSModuleType.ES2015);
        }
        if (coverage) {
            target.setCoverage(new JavaScriptCoverage(SOURCE::equals));
        }
        var vm = new TeaVMBuilder(target).build();
        vm.installPlugins();
        vm.setEntryPoint(CoverageSubject.class.getName());
        vm.setOptimizationLevel(optimized ? TeaVMOptimizationLevel.ADVANCED : TeaVMOptimizationLevel.SIMPLE);
        vm.build(directory.toFile(), "test.js");
        TeaVMProblemRenderer.describeProblems(vm, new ConsoleTeaVMToolLog(false));
        assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty());
        return directory;
    }

    private Map<String, Integer> sourceMarkers() throws Exception {
        var source = Path.of("src/test/java").resolve(SOURCE);
        var lines = Files.readAllLines(source);
        var markers = new HashMap<String, Integer>();
        for (int i = 0; i < lines.size(); ++i) {
            int marker = lines.get(i).indexOf("// COVERAGE: ");
            if (marker >= 0) {
                markers.put(lines.get(i).substring(marker + 13).trim(), i + 1);
            }
        }
        return markers;
    }
}
