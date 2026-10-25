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
package org.teavm.browserrunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.util.Map;
import org.junit.Test;
import tools.jackson.databind.ObjectMapper;

public class CoverageResponseTest {
    @Test
    public void decodesHitsAndMisses() {
        var json = new ObjectMapper().readTree("{\"Example.java\":{\"1\":1,\"2\":0}}");
        assertEquals(Map.of("Example.java", Map.of(1, true, 2, false)), BrowserRunner.readCoverage(json));
    }

    @Test
    public void missingAndMalformedCoverageFailInsteadOfProducingGreenReport() {
        assertThrows(IllegalArgumentException.class, () -> BrowserRunner.readCoverage(null));
        for (var input : new String[] {"[]", "{\"Example.java\":null}", "{\"Example.java\":{\"0\":1}}",
                "{\"Example.java\":{\"1\":true}}", "{\"Example.java\":{\"1\":2}}"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BrowserRunner.readCoverage(new ObjectMapper().readTree(input)));
        }
    }
}
