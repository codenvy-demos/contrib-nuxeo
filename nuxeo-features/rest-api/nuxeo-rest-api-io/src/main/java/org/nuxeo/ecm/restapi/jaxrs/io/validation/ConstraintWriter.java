/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     nchapurlatn <nc@nuxeo.com>
 */

package org.nuxeo.ecm.restapi.jaxrs.io.validation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint.Description;

/**
 * JAX-RS {@link MessageBodyWriter} which is able to marshall {@link ConstraintWriter} as JSON.
 * <p>
 * Use as singleton in JAX-RS modules.
 * </p>
 *
 * @since 7.2
 */
public class ConstraintWriter extends JSONMessageBodyWriter<Constraint> {

    private static final String ENTITY_TYPE = "constraint";

    @Override
    public void writeTo(Constraint constraint, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        JsonGenerator jg = createGenerator(entityStream);
        Description description = constraint.getDescription();
        jg.writeStartObject();
        writeEntityFields(jg, ENTITY_TYPE, constraint);
        String name = description.getName();
        jg.writeStringField("name", camelToSnake(name));
        // constraint parameters
        jg.writeObjectFieldStart("parameters");
        for (Map.Entry<String, Serializable> param : description.getParameters().entrySet()) {
            jg.writeStringField(camelToSnake(param.getKey()), param.getValue().toString());
        }
        jg.writeEndObject();
        jg.writeEndObject();
        jg.flush();
    }

}