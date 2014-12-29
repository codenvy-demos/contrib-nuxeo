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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;

import com.google.common.base.CaseFormat;

/**
 * Abstract object to build JAX-RS {@link MessageBodyWriter} for marshalling an entity as json.
 * <p>
 * This object provides utility methods for sub-classes:
 * <ul>
 * <li>{@link #writeEntityFields(JsonGenerator, String)}</li>
 * <li>{@link #writeMarshallable(JsonGenerator, String, Object, Class, MultivaluedMap)}</li>
 * <li>{@link #writeMarshallableField(JsonGenerator, String, Object, Class, MultivaluedMap)}</li>
 * <li>{@link #getMarshallableJson(Object, Class, MultivaluedMap)}</li>
 * <li>{@link #camelToSnake(String)}</li>
 * </ul>
 * </p>
 *
 * @param <T> The type of the entity to marshal.
 * @since 7.2
 */
@Provider
@Produces(APPLICATION_JSON)
public abstract class JSONMessageBodyWriter<T> implements MessageBodyWriter<T> {

    private static final String ENTITY_TYPE_FIELD_NAME = "entity-type";

    private static final Log log = LogFactory.getLog(JSONMessageBodyWriter.class);

    /**
     * This property allows to retrieve any JAX-RS object : {@link MessageBodyWriter}, {@link MessageBodyReader},
     * {@link ExceptionMapper} or {@link ContextResolver}.
     */
    @Context
    protected Providers providers;

    /**
     * This property allows to create {@link JsonGenerator}.
     */
    @Context
    protected JsonFactory jsonFactory;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (!mediaType.isCompatible(APPLICATION_JSON_TYPE)) {
            return false;
        }
        ParameterizedType ptype = (ParameterizedType) this.getClass().getGenericSuperclass();
        Type[] ts = ptype.getActualTypeArguments();
        Class<?> c = (Class<?>) ts[0];
        return c.isAssignableFrom(type);
    }

    @Override
    public long getSize(T entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1l;
    }

    /**
     * Creates a UTF-8 {@link JsonGenerator}.
     *
     * @since 7.2
     */
    protected JsonGenerator createGenerator(OutputStream entityStream) throws IOException {
        return jsonFactory.createJsonGenerator(entityStream, JsonEncoding.UTF8);
    }

    /**
     * Converts CamelCaseString to snake_case_string.
     *
     * @param camelCaseString The CamelCase string.
     * @return A snake case string.
     * @since 7.2
     */
    protected String camelToSnake(String camelCaseString) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, camelCaseString);
    }

    /**
     * Writes entity fields in a {@link JsonGenerator}.
     *
     * <pre>
     * {@code
     * entity-type: <entityType>
     * }
     * </pre>
     *
     * @param jg
     * @param entityType The string type of the entity.
     * @since 7.2
     */
    protected void writeEntityFields(JsonGenerator jg, String entityType, Object entity)
            throws JsonGenerationException, IOException {
        jg.writeStringField(ENTITY_TYPE_FIELD_NAME, entityType);
    }

    /**
     * Delegates writing of an object to JAX-RS. This method will search for a JSON {@link MessageBodyWriter} for the
     * entity.
     *
     * @since 7.2
     */
    protected <ObjectType> void writeMarshallableField(JsonGenerator jg, String fieldName, ObjectType object,
            Class<ObjectType> clazz, MultivaluedMap<String, Object> httpHeaders) throws WebApplicationException,
            IOException {
        jg.writeFieldName(fieldName);
        String marshalled = getMarshallableJson(object, clazz, httpHeaders);
        jg.writeRawValue(marshalled);
    }

    /**
     * Delegates writing of an object to JAX-RS. This method will search for a JSON {@link MessageBodyWriter} for the
     * entity.
     *
     * @since 7.2
     */
    protected <ObjectType> void writeMarshallable(JsonGenerator jg, ObjectType object, Class<ObjectType> clazz,
            MultivaluedMap<String, Object> httpHeaders) throws WebApplicationException, IOException {
        String marshalled = getMarshallableJson(object, clazz, httpHeaders);
        jg.writeRawValue(marshalled);
    }

    /**
     * Gets the JSON representation of an entity marshalled by JAX-RS. This method will search for a JSON
     * {@link MessageBodyWriter} for the entity.
     *
     * @since 7.2
     */
    protected <ObjectType> String getMarshallableJson(ObjectType object, Class<ObjectType> clazz,
            MultivaluedMap<String, Object> httpHeaders) throws WebApplicationException, IOException {
        MessageBodyWriter<ObjectType> writer = providers.getMessageBodyWriter(clazz, null, null, APPLICATION_JSON_TYPE);
        if (writer == null) {
            log.error(String.format("No MessageBodyWriter found to manage marshalling of Java Class %s as %s",
                    clazz.getName(), APPLICATION_JSON));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.writeTo(object, object.getClass(), null, null, APPLICATION_JSON_TYPE, httpHeaders, baos);
        return baos.toString(JsonEncoding.UTF8.getJavaName());
    }

}
