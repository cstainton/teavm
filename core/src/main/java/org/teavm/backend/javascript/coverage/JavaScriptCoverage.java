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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.model.ClassReader;
import org.teavm.model.TextLocation;
import org.teavm.model.instructions.AbstractInstructionReader;

/**
 * Opt-in source line probes for the JavaScript backend. Probes are emitted after optimization,
 * so enabling coverage does not make otherwise unreachable Java methods reachable.
 * Instances belong to a single compilation and must not be shared between concurrent builds.
 */
public class JavaScriptCoverage {
    public static final String GLOBAL_NAME = "__teavmCoverage__";
    private final Predicate<String> sourceFilter;
    private final Map<String, Set<Integer>> candidates = new TreeMap<>();
    private final Map<String, Set<Integer>> emitted = new TreeMap<>();

    public JavaScriptCoverage(Predicate<String> sourceFilter) {
        this.sourceFilter = sourceFilter;
    }

    /** A baseline of emitted lines, including code that no browser has executed yet. */
    public Map<String, Set<Integer>> getEmittedLines() {
        var result = new TreeMap<String, Set<Integer>>();
        emitted.forEach((file, lines) -> result.put(file, Set.copyOf(lines)));
        return Map.copyOf(result);
    }

    public void clear() {
        candidates.clear();
        emitted.clear();
    }

    public boolean accepts(TextLocation location) {
        return location != null && location.getFileName() != null && location.getLine() > 0
                && sourceFilter.test(location.getFileName());
    }

    public void examine(ClassReader cls) {
        if (cls == null) {
            return;
        }
        var reader = new AbstractInstructionReader() {
            @Override
            public void location(TextLocation location) {
                if (accepts(location)) {
                    add(candidates, location);
                }
            }
        };
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            if (program != null) {
                for (var block : program.getBasicBlocks()) {
                    block.readAllInstructions(reader);
                }
            }
        }
    }

    private void add(Map<String, Set<Integer>> lines, TextLocation location) {
        lines.computeIfAbsent(location.getFileName(), key -> new TreeSet<>()).add(location.getLine());
    }

    /** Writes an assignment expression; the caller supplies statement or comma-expression delimiters. */
    public void emitProbe(SourceWriter writer, TextLocation location) {
        add(emitted, location);
        writer.append("globalThis.").append(GLOBAL_NAME).append("[\"")
                .append(RenderingUtil.escapeString(location.getFileName())).append("\"][")
                .append(location.getLine()).append("]=1");
    }

    /** Runs before any generated application initialization, and preserves hits from other bundles. */
    public void emitInitialization(SourceWriter writer) {
        writer.append("globalThis.").append(GLOBAL_NAME).append(" ??= Object.create(null);").softNewLine();
        for (var file : emitted.entrySet()) {
            var access = "globalThis." + GLOBAL_NAME + "[\""
                    + RenderingUtil.escapeString(file.getKey()) + "\"]";
            writer.append(access).append(" ??= Object.create(null);").softNewLine();
            for (var line : file.getValue()) {
                writer.append(access).append('[').append(line).append("] ??= 0;").softNewLine();
            }
        }
    }

    /**
     * Diagnostic inventory, not a coverage denominator for the entire classpath. Not-emitted lines
     * are source locations in examined classes without a generated probe (including optimized code).
     */
    public void writeInventory(Writer output) throws IOException {
        try {
            var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartElement("teavmCoverageInventory");
            xml.writeAttribute("version", "1");
            var files = new TreeSet<>(candidates.keySet());
            files.addAll(emitted.keySet());
            for (var file : files) {
                xml.writeStartElement("file");
                xml.writeAttribute("path", file);
                var lines = new TreeSet<>(candidates.getOrDefault(file, Set.of()));
                var included = emitted.getOrDefault(file, Set.of());
                lines.addAll(included);
                for (var line : lines) {
                    xml.writeEmptyElement("line");
                    xml.writeAttribute("number", line.toString());
                    xml.writeAttribute("state", included.contains(line) ? "emitted" : "not-emitted");
                }
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Could not write coverage inventory", e);
        }
    }
}
