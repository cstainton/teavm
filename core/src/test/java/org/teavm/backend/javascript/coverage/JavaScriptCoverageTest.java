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
package org.teavm.backend.javascript.coverage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.StringWriter;
import org.junit.Test;
import org.teavm.backend.javascript.codegen.OutputSourceWriterBuilder;
import org.teavm.model.TextLocation;

public class JavaScriptCoverageTest {
    @Test
    public void inventoryEscapesPathsAndResetsBetweenCompilations() throws Exception {
        var coverage = new JavaScriptCoverage(file -> file.endsWith(".java"));
        assertFalse(coverage.accepts(null));
        assertFalse(coverage.accepts(new TextLocation("Example.java", -1)));
        assertFalse(coverage.accepts(new TextLocation("runtime.js", 10)));
        var text = new StringBuilder();
        var writer = new OutputSourceWriterBuilder(null).build(text);
        coverage.emitProbe(writer, new TextLocation("A&B.java", 10));
        writer.finish();
        var inventory = new StringWriter();
        coverage.writeInventory(inventory);
        assertTrue(inventory.toString().contains("A&amp;B.java"));
        assertTrue(inventory.toString().contains("number=\"10\" state=\"emitted\""));
        coverage.clear();
        inventory = new StringWriter();
        coverage.writeInventory(inventory);
        assertFalse(inventory.toString().contains("A&amp;B.java"));
    }
}
