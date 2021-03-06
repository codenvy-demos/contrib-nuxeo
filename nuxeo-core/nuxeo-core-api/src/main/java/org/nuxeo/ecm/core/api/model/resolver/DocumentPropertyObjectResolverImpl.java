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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.api.model.resolver;

import java.io.Serializable;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 7.1
 */
public class DocumentPropertyObjectResolverImpl implements PropertyObjectResolver {

    protected DocumentModel doc;

    protected String xpath;

    protected ObjectResolver resolver;

    public static DocumentPropertyObjectResolverImpl create(DocumentModel doc, String xpath) {
        Field field = Framework.getService(SchemaManager.class).getField(xpath);
        if (field != null) {
            ObjectResolver resolver = field.getType().getObjectResolver();
            if (resolver != null) {
                return new DocumentPropertyObjectResolverImpl(doc, xpath, resolver);
            }
        }
        return null;
    }

    public DocumentPropertyObjectResolverImpl(DocumentModel doc, String xpath, ObjectResolver resolver) {
        this.doc = doc;
        this.xpath = xpath;
        this.resolver = resolver;
    }

    @Override
    public boolean validate() {
        return resolver.validate(doc.getPropertyValue(xpath));
    }

    @Override
    public Object fetch() {
        return resolver.fetch(doc.getPropertyValue(xpath));
    }

    @Override
    public <T> T fetch(Class<T> type) {
        return resolver.fetch(type, doc.getPropertyValue(xpath));
    }

    @Override
    public void setObject(Object object) {
        Serializable reference = resolver.getReference(object);
        doc.setPropertyValue(xpath, reference);
    }

    @Override
    public ObjectResolver getObjectResolver() {
        return resolver;
    }

}
