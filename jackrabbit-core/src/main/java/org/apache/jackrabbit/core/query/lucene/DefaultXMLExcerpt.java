/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.lucene;

import org.apache.lucene.index.TermPositionVector;

import java.io.IOException;

/**
 * <code>DefaultXMLExcerpt</code> creates an XML excerpt of a matching node.
 * <br/>
 * E.g. if you search for 'jackrabbit' and 'query' you may get the following
 * result for a node:
 * <pre>
 * &lt;excerpt>
 *     &lt;fragment>&lt;highlight>Jackrabbit&lt;/highlight> implements both the mandatory XPath and optional SQL &lt;highlight>query&lt;/highlight> syntax.&lt;/fragment>
 *     &lt;fragment>Before parsing the XPath &lt;highlight>query&lt;/highlight> in &lt;highlight>Jackrabbit&lt;/highlight>, the statement is surrounded&lt;/fragment>
 * &lt;/excerpt>
 * </pre>
 */
public class DefaultXMLExcerpt extends AbstractExcerpt {

    /**
     * {@inheritDoc}
     */
    protected String createExcerpt(TermPositionVector tpv,
                                   String text,
                                   int maxFragments,
                                   int maxFragmentSize)
            throws IOException {
        return DefaultHighlighter.highlight(tpv, getQueryTerms(), text,
                maxFragments, maxFragmentSize / 2);
    }
}
