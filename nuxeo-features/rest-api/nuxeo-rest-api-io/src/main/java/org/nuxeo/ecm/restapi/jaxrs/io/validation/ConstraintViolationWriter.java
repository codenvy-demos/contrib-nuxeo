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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation.PathNode;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * JAX-RS {@link MessageBodyWriter} which is able to marshall {@link ConstraintViolation} as JSON.
 * <p>
 * This {@link MessageBodyWriter} delegates marshalling of {@link ConstraintViolation}'s {@link Constraint} to JAX-RS
 * which provides a {@link MessageBodyWriter} to manage them.
 * </p>
 * <p>
 * Use as singleton in JAX-RS modules.
 * </p>
 *
 * @since 7.2
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class ConstraintViolationWriter extends JSONMessageBodyWriter<ConstraintViolation> {

    private static final String ENTITY_TYPE = "constraint_violation";

    @Context
    protected HttpHeaders headers;

    @Override
    public void writeTo(ConstraintViolation violation, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        JsonGenerator jg = createGenerator(entityStream);
        jg.writeStartObject();
        writeEntityFields(jg, ENTITY_TYPE, violation);
        // violation message
        String message = violation.getMessage(headers.getLanguage());
        jg.writeStringField("message", message);
        // invalid value
        Object invalidValue = violation.getInvalidValue();
        if (invalidValue == null) {
            jg.writeNullField("invalid_value");
        } else {
            jg.writeStringField("invalid_value", invalidValue.toString());
        }
        // violated constraint
        Constraint constraint = violation.getConstraint();
        writeMarshallableField(jg, "constraint", constraint, Constraint.class, httpHeaders);
        // violation place
        jg.writeArrayFieldStart("path");
        for (PathNode node : violation.getPath()) {
            jg.writeStartObject();
            jg.writeStringField("field_name", node.getField().getName().getPrefixedName());
            jg.writeBooleanField("is_list_item", node.isListItem());
            if (node.isListItem()) {
                jg.writeNumberField("index", node.getIndex());
            }
            jg.writeEndObject();
        }
        jg.writeEndArray();
        jg.writeEndObject();
        jg.flush();
    }

}