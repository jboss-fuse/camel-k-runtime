/**
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
package org.apache.camel.k.jvm.loader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.k.RoutesLoader;
import org.apache.camel.k.Source;
import org.apache.camel.k.support.URIResolver;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.spi.RestConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joor.Reflect;

public class JavaSourceLoader implements RoutesLoader {
    @Override
    public List<String> getSupportedLanguages() {
        return Collections.singletonList("java");
    }

    @Override
    public RouteBuilder load(CamelContext camelContext, Source source) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final CamelContext context = getContext();

                try (InputStream is = URIResolver.resolve(context, source)) {
                    // compile the source in memory
                    String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                    String name = determineQualifiedName(source, content);
                    Reflect compiled = Reflect.compile(name, content);

                    // create the builder
                    RoutesBuilder builder = compiled.create().get();

                    if (builder instanceof RouteBuilder) {
                        RouteBuilder rb = ((RouteBuilder) builder);

                        rb.setContext(context);
                        rb.configure();

                        Map<String, RestConfigurationDefinition> configurations = rb.getRestConfigurations();

                        //
                        // TODO: RouteBuilder.getRestConfigurations() should not
                        //       return null
                        //
                        if (configurations != null) {
                            for (RestConfigurationDefinition definition : configurations.values()) {
                                RestConfiguration conf = definition.asRestConfiguration(context);

                                //
                                // this is an hack to copy routes configuration
                                // to the camel context
                                //
                                // TODO: fix RouteBuilder.includeRoutes to include
                                //       rest configurations
                                //
                                context.addRestConfiguration(conf);
                            }
                        }

                        setRouteCollection(rb.getRouteCollection());
                        setRestCollection(rb.getRestCollection());
                    }
                }
            }
        };
    }

    private static String determineQualifiedName(Source source, String content) throws Exception {
        String name = source.getName();
        name = StringUtils.removeEnd(name, ".java");

        Pattern pattern = Pattern.compile("^\\s*package\\s+([a-zA_Z_][\\.\\w]*)\\s*;.*");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            name = matcher.group(1) + "." + name;
        }

        return name;
    }
}
