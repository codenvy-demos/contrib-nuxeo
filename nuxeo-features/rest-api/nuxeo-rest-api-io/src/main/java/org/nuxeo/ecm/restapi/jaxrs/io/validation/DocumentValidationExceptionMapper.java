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

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.api.validation.DocumentValidationReport;

/**
 * JAX-RS {@link ExceptionMapper} which is able to marshall {@link DocumentValidationException} as JSON.
 * <p>
 * This {@link ExceptionMapper} returns a {@link Response} containing a {@link DocumentValidationReport} as entity.
 * JAX-RS would manage {@link DocumentValidationReport} marshalling.
 * </p>
 * <p>
 * Use as singleton in JAX-RS modules.
 * </p>
 *
 * @since 7.2
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class DocumentValidationExceptionMapper implements ExceptionMapper<DocumentValidationException> {

    @Override
    public Response toResponse(DocumentValidationException exception) {
        return Response.status(Status.BAD_REQUEST).entity(exception.getReport()).build();
    }

}