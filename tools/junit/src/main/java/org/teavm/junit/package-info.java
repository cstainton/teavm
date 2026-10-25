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
/**
 * JUnit integration for compiling Java tests with TeaVM and executing them in target runtimes.
 *
 * <h2>Experimental JavaScript line coverage</h2>
 * Keep using {@code @RunWith(TeaVMTestRunner.class)}. Set these properties in the test JVM:
 * <pre>
 * teavm.junit.js.coverage=true
 * teavm.junit.js=true
 * teavm.junit.js.runner=browser-chrome
 * teavm.junit.target=target/teavm-tests
 * teavm.junit.sourceDirs=/project/src/main/java
 * </pre>
 * Source directories are separated by the platform path separator. They select which sources
 * are instrumented and resolve compiler source names to actual files. Use only the source roots
 * that should be reported to the consuming project; duplicate source names fail explicitly.
 * Coverage does not require source maps or TeaVM debugger metadata: probes use compiler locations.
 *
 * <p>The compiler seeds a baseline before execution. Browser results are collected after asynchronous
 * completion, including failed tests, and merged by logical OR across cached runs, separately
 * compiled methods and compiler configurations. Hits from class initialization are retained.
 * Each test JVM writes {@code target/teavm-tests/coverage/sonar-PID.xml}; separate forks never
 * overwrite one another. Clean the coverage directory before a new test invocation. Supply the
 * comma-separated paths of this invocation's XML files to {@code sonar.coverageReportPaths}.
 * These are generic coverage reports, not JaCoCo reports or test-result reports.
 *
 * <p>Beside each compiled JavaScript file, {@code *.js.coverage.xml} records emitted and not-emitted
 * source lines. The latter are locations examined in dependency-reached classes with no generated
 * probe, including unused methods and optimized code. This is not an inventory of the entire
 * classpath, and a not-emitted line is not counted as an unexecuted line in the coverage XML.
 * A source line shared by multiple expressions is covered when any instrumented expression or
 * statement on it executes; this is line coverage, not branch or instruction coverage.
 *
 * <p>Limitations: the initial implementation supports the JavaScript backend only. Native JavaScript,
 * generated intrinsic bodies and code without Java line information do not acquire Java coverage.
 * Browser instrumentation is independent of Chrome's debugging protocol, but each browser/harness
 * combination must be validated. Sonar's treatment of files or lines absent from the report still
 * depends on its analysis configuration; the inventory does not automatically configure exclusions.
 * No production instrumentation or quality-gate changes are enabled by default.
 *
 * <p>Within the TeaVM repository, use {@code -Pteavm.tests.coverage=true} with the usual explicit
 * backend properties. A focused demonstration is {@code org.teavm.coverage.CoverageIntegrationTest};
 * {@code CoverageRunnerTest} exercises the regular runner and writes Sonar reports when enabled.
 */
package org.teavm.junit;
