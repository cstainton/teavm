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

import org.teavm.jso.JSBody;

public final class CoverageSubject {
    private static int state = initialize(); // COVERAGE: initialization

    private CoverageSubject() {
    }

    private static int initialize() {
        return 7; // COVERAGE: initializer
    }

    public static void main(String[] args) throws InterruptedException {
        boolean first = args.length == 0;
        if (first) {
            state += 10; // COVERAGE: taken
        } else {
            state += 20; // COVERAGE: missed
        }
        boolean value = first || rightOperand();
        record(value ? state : -1);
        Thread.sleep(1);
        record(state); // COVERAGE: afterAsync
        if (args.length > 0 && args[0].length() == 4) {
            throw new IllegalStateException("intentional coverage failure"); // COVERAGE: failure
        }
    }

    private static boolean rightOperand() {
        state += 100; // COVERAGE: shortCircuit
        return true;
    }

    public static int unused() {
        return 987654; // COVERAGE: excluded
    }

    @JSBody(params = "value", script = "globalThis.coverageResult = value;")
    private static native void record(int value);
}
