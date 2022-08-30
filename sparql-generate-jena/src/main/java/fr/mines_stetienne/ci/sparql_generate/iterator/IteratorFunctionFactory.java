/*
 * Copyright 2020 MINES Saint-Étienne
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.mines_stetienne.ci.sparql_generate.iterator;

import org.apache.jena.sparql.sse.builders.SSE_ExprBuildException;

/**
 * Interface for iterator function factories.
 */
public interface IteratorFunctionFactory {

    /**
     * Create a iterator function with the given URI
     *
     * @param uri URI
     * @return IteratorFunction
     * @throws SSE_ExprBuildException May be thrown if there is a problem creating a
     * iterator function
     */
    public IteratorFunction create(String uri);
}
