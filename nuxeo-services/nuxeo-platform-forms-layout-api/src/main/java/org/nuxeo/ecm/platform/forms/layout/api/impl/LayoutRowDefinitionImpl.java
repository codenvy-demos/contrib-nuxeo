/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.forms.layout.api.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.platform.forms.layout.api.LayoutRowDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetReference;

/**
 * Default implementation for a layout row definition.
 * <p>
 * Useful to compute rows independently from the layout service.
 *
 * @author Anahide Tchertchian
 * @author Antoine Taillefer
 * @since 5.4
 */
public class LayoutRowDefinitionImpl implements LayoutRowDefinition {

    private static final long serialVersionUID = 1L;

    protected String name;

    protected Map<String, Map<String, Serializable>> properties;

    protected WidgetReference[] widgets;

    protected boolean alwaysSelected = false;

    protected boolean selectedByDefault = true;

    // needed by GWT serialization
    protected LayoutRowDefinitionImpl() {
        super();
    }

    /**
     * Instantiates a new {@code LayoutRowDefinitionImpl} with a given widget name and category.
     *
     * @param name the row name
     * @param widget the widget name
     * @param category the category
     * @since 5.6
     */
    public LayoutRowDefinitionImpl(String name, String widget, String category) {
        this.name = name;
        this.properties = null;
        if (widget == null) {
            this.widgets = new WidgetReferenceImpl[0];
        } else {
            WidgetReferenceImpl widgetRef = new WidgetReferenceImpl(category, widget);
            this.widgets = new WidgetReferenceImpl[] { widgetRef };
        }
        this.alwaysSelected = false;
        this.selectedByDefault = true;
    }

    public LayoutRowDefinitionImpl(String name, String widget) {
        this(name, widget, null);
    }

    public LayoutRowDefinitionImpl(String name, Map<String, Map<String, Serializable>> properties,
            List<WidgetReference> widgets, boolean alwaysSelected, boolean selectedByDefault) {
        super();
        this.name = name;
        this.properties = properties;
        if (widgets == null) {
            this.widgets = new WidgetReferenceImpl[0];
        } else {
            WidgetReference[] refs = new WidgetReference[widgets.size()];
            for (int i = 0; i < widgets.size(); i++) {
                refs[i] = widgets.get(i);
            }
            this.widgets = widgets.toArray(new WidgetReference[0]);
        }
        this.alwaysSelected = alwaysSelected;
        this.selectedByDefault = selectedByDefault;
    }

    public LayoutRowDefinitionImpl(String name, Map<String, Map<String, Serializable>> properties,
            WidgetReference[] widgets, boolean alwaysSelected, boolean selectedByDefault) {
        super();
        this.name = name;
        this.properties = properties;
        this.widgets = widgets;
        this.alwaysSelected = alwaysSelected;
        this.selectedByDefault = selectedByDefault;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDefaultName(int index) {
        return "layout_row_" + index;
    }

    @Override
    public Map<String, Serializable> getProperties(String layoutMode) {
        return WidgetDefinitionImpl.getProperties(properties, layoutMode);
    }

    @Override
    public Map<String, Map<String, Serializable>> getProperties() {
        return properties;
    }

    @Override
    public int getSize() {
        return widgets.length;
    }

    @Override
    public String[] getWidgets() {
        String[] names = new String[widgets.length];
        for (int i = 0; i < widgets.length; i++) {
            names[i] = widgets[i].getName();
        }
        return names;
    }

    @Override
    public WidgetReference[] getWidgetReferences() {
        return widgets;
    }

    @Override
    public boolean isAlwaysSelected() {
        return alwaysSelected;
    }

    @Override
    public boolean isSelectedByDefault() {
        return selectedByDefault;
    }

    @Override
    public LayoutRowDefinition clone() {
        Map<String, Map<String, Serializable>> cprops = null;
        if (properties != null) {
            cprops = new HashMap<String, Map<String, Serializable>>();
            for (Map.Entry<String, Map<String, Serializable>> entry : properties.entrySet()) {
                Map<String, Serializable> subProps = entry.getValue();
                Map<String, Serializable> csubProps = null;
                if (subProps != null) {
                    csubProps = new HashMap<String, Serializable>();
                    csubProps.putAll(subProps);
                }
                cprops.put(entry.getKey(), csubProps);
            }
        }
        WidgetReference[] cwidgets = null;
        if (widgets != null) {
            cwidgets = new WidgetReference[widgets.length];
            for (int i = 0; i < widgets.length; i++) {
                cwidgets[i] = widgets[i].clone();
            }
        }
        LayoutRowDefinition clone = new LayoutRowDefinitionImpl(name, cprops, cwidgets, alwaysSelected,
                selectedByDefault);
        return clone;
    }

}
