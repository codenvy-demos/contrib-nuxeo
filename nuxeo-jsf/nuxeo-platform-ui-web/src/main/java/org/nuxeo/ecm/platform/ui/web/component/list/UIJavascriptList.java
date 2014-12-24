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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.ui.web.component.list;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.faces.FacesException;
import javax.faces.application.Application;
import javax.faces.application.FacesMessage;
import javax.faces.application.StateManager;
import javax.faces.component.ContextCallback;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.NamingContainer;
import javax.faces.component.UIColumn;
import javax.faces.component.UIComponent;
import javax.faces.component.UIComponentBase;
import javax.faces.component.UIData;
import javax.faces.component.UIForm;
import javax.faces.component.UIInput;
import javax.faces.component.UINamingContainer;
import javax.faces.component.UIViewRoot;
import javax.faces.component.visit.VisitCallback;
import javax.faces.component.visit.VisitContext;
import javax.faces.component.visit.VisitHint;
import javax.faces.component.visit.VisitResult;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.FacesEvent;
import javax.faces.event.FacesListener;
import javax.faces.event.PhaseId;
import javax.faces.event.PostValidateEvent;
import javax.faces.event.PreValidateEvent;
import javax.faces.model.DataModel;
import javax.faces.model.ListDataModel;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.platform.el.FieldAdapterManager;
import org.nuxeo.ecm.platform.ui.web.model.EditableModel;
import org.nuxeo.ecm.platform.ui.web.model.impl.EditableModelImpl;

import com.sun.faces.facelets.tag.jsf.ComponentSupport;

/**
 * Editable list component relying
 *
 * @since 7.1
 */
public class UIJavascriptList extends UIInput implements NamingContainer {

    public static final String COMPONENT_FAMILY = "javax.faces.Data";

    public static final String COMPONENT_TYPE = UIJavascriptList.class.getName();

    private static final Log log = LogFactory.getLog(UIJavascriptList.class);

    protected static final String TEMPLATE_INDEX_MARKER = "TEMPLATE_INDEX_MARKER";

    protected static final String IS_LIST_TEMPLATE_VAR = "isListTemplate";

    public UIJavascriptList() {
        super();
        setRendererType(null);
    }

    enum PropertyKeys {
        /**
         * <p>
         * The zero-relative index of the current row number, or -1 for no current row association.
         * </p>
         */
        rowIndex,

        /**
         * <p>
         * This map contains <code>SavedState</code> instances for each descendant component, keyed by the client
         * identifier of the descendant. Because descendant client identifiers will contain the <code>rowIndex</code>
         * value of the parent, per-row state information is actually preserved.
         * </p>
         */
        saved,

        /**
         * <p>
         * The request scope attribute under which the data object for the current row will be exposed when iterating.
         * </p>
         */
        var,

        /**
         * Same but for the old model, for compatibility with {@link UIEditableList} behavior.
         */
        model,

        /**
         * <p>
         * Last id vended by {@link UIData#createUniqueId(javax.faces.context.FacesContext, String)}.
         * </p>
         */
        lastId,

        /**
         * Object representing the "bare" mapping for each new element in the list.
         */
        template,

        /**
         * Boolean representing wether a diff instance should be set instead of the whole list at submit.
         */
        diff,

        /**
         * Number of elements to display open by default.
         */
        number,

        /**
         * Boolean that should be set to true to remove empty elements from the list at submit.
         * <p>
         * Kept for compatibility with {@link UIEditableList} features.
         */
        removeEmpty,
    }

    /**
     * <p>
     * The {@link DataModel} associated with this component, lazily instantiated if requested. This object is not part
     * of the saved and restored state of the component.
     * </p>
     */
    private EditableModel model = null;

    /**
     * <p>
     * During iteration through the rows of this table, This ivar is used to store the previous "var" value for this
     * instance. When the row iteration is complete, this value is restored to the request map.
     */
    private Object oldVar;

    /**
     * <p>
     * During iteration through the rows of this table, This ivar is used to store the previous "model" value for this
     * instance. When the row iteration is complete, this value is restored to the request map.
     */
    private Object oldModel;

    /**
     * <p>
     * Holds the base client ID that will be used to generate per-row client IDs (this will be null if this UIData is
     * nested within another).
     * </p>
     * <p>
     * This is not part of the component state.
     * </p>
     */
    private String baseClientId = null;

    /**
     * <p>
     * Length of the cached <code>baseClientId</code> plus one for the {@link UINamingContainer#getSeparatorChar}.
     * </p>
     * <p>
     * This is not part of the component state.
     * </p>
     */
    private int baseClientIdLength;

    /**
     * <p>
     * StringBuilder used to build per-row client IDs.
     * </p>
     * <p>
     * This is not part of the component state.
     * </p>
     */
    private StringBuilder clientIdBuilder = null;

    /**
     * <p>
     * Flag indicating whether or not this UIData instance is nested within another UIData instance
     * </p>
     * <p>
     * This is not part of the component state.
     * </p>
     */
    private Boolean isNested = null;

    private Map<String, Object> _rowDeltaStates = new HashMap<String, Object>();

    private Map<String, Object> _rowTransientStates = new HashMap<String, Object>();

    private Object _initialDescendantFullComponentState = null;

    protected int getFirst() {
        return 0;
    }

    // XXX
    protected int getRows() {
        return 0;
    }

    public boolean isRowAvailable() {
        return (getDataModel().isRowAvailable());
    }

    public int getRowCount() {
        return (getDataModel().getRowCount());
    }

    public Object getRowData() {
        return (getDataModel().getRowData());
    }

    public int getRowIndex() {
        return (Integer) getStateHelper().eval(PropertyKeys.rowIndex, -1);
    }

    public void setRowIndex(int rowIndex) {
        setRowIndexRowStatePreserved(rowIndex);
    }

    private void setRowIndexWithoutRowStatePreserved(int rowIndex) {
        // Save current state for the previous row index
        saveDescendantState();

        // Update to the new row index
        // this.rowIndex = rowIndex;
        getStateHelper().put(PropertyKeys.rowIndex, rowIndex);
        EditableModel localModel = getDataModel();
        localModel.setRowIndex(rowIndex);

        // if rowIndex is -1, clear the cache
        if (rowIndex == -1) {
            setDataModel(null);
        }

        // Clear or expose the current row data as a request scope attribute
        String var = (String) getStateHelper().get(PropertyKeys.var);
        if (var != null) {
            Map<String, Object> requestMap = getFacesContext().getExternalContext().getRequestMap();
            if (rowIndex < 0) {
                oldVar = requestMap.remove(var);
            } else if (isRowAvailable()) {
                requestMap.put(var, getRowData());
            } else {
                requestMap.remove(var);
                if (null != oldVar) {
                    requestMap.put(var, oldVar);
                    oldVar = null;
                }
            }
        }

        // Reset current state information for the new row index
        restoreDescendantState();

    }

    @SuppressWarnings({ "unchecked" })
    private void setRowIndexRowStatePreserved(int rowIndex) {
        if (rowIndex < -1) {
            throw new IllegalArgumentException("rowIndex is less than -1");
        }

        if (getRowIndex() == rowIndex) {
            return;
        }

        FacesContext facesContext = getFacesContext();

        if (_initialDescendantFullComponentState != null) {
            // Just save the row
            Map<String, Object> sm = saveFullDescendantComponentStates(facesContext, null, getChildren().iterator(),
                    false);
            if (sm != null && !sm.isEmpty()) {
                _rowDeltaStates.put(getContainerClientId(facesContext), sm);
            }
            if (getRowIndex() >= 0) {
                _rowTransientStates.put(getContainerClientId(facesContext),
                        saveTransientDescendantComponentStates(facesContext, null, getChildren().iterator(), false));
            }
        }

        // Update to the new row index
        // this.rowIndex = rowIndex;
        getStateHelper().put(PropertyKeys.rowIndex, rowIndex);
        EditableModel localModel = getDataModel();
        localModel.setRowIndex(rowIndex);

        // if rowIndex is -1, clear the cache
        if (rowIndex == -1) {
            setDataModel(null);
        }

        // Clear or expose the current row data as a request scope attribute
        String var = getVar();
        String modelVar = getModel();
        boolean hasModelVar = !StringUtils.isBlank(modelVar);
        if (var != null) {
            Map<String, Object> requestMap = getFacesContext().getExternalContext().getRequestMap();
            if (rowIndex == -1) {
                oldVar = requestMap.remove(var);
                if (hasModelVar) {
                    oldModel = requestMap.remove(modelVar);
                }
            } else if (isRowAvailable()) {
                requestMap.put(var, getRowData());
                if (hasModelVar) {
                    requestMap.put(modelVar, getDataModel());
                }
            } else {
                requestMap.remove(var);
                if (null != oldVar) {
                    requestMap.put(var, oldVar);
                    oldVar = null;
                }
                if (hasModelVar) {
                    requestMap.remove(modelVar);
                    if (null != oldModel) {
                        requestMap.put(modelVar, oldModel);
                        oldModel = null;
                    }
                }
            }
        }

        if (_initialDescendantFullComponentState != null) {
            Object rowState = _rowDeltaStates.get(getContainerClientId(facesContext));
            if (rowState == null) {
                // Restore as original
                restoreFullDescendantComponentStates(facesContext, getChildren().iterator(),
                        _initialDescendantFullComponentState, false);
            } else {
                // Restore first original and then delta
                restoreFullDescendantComponentDeltaStates(facesContext, getChildren().iterator(), rowState,
                        _initialDescendantFullComponentState, false);
            }
            if (getRowIndex() == -1) {
                restoreTransientDescendantComponentStates(facesContext, getChildren().iterator(), null, false);
            } else {
                rowState = _rowTransientStates.get(getContainerClientId(facesContext));
                if (rowState == null) {
                    restoreTransientDescendantComponentStates(facesContext, getChildren().iterator(), null, false);
                } else {
                    restoreTransientDescendantComponentStates(facesContext, getChildren().iterator(),
                            (Map<String, Object>) rowState, false);
                }
            }
        }
    }

    public String getVar() {
        String var = (String) getStateHelper().get(PropertyKeys.var);
        if (StringUtils.isBlank(var)) {
            // BBB for UIEditableList behaviour
            return "var";
        }
        return var;
    }

    public void setVar(String var) {
        getStateHelper().put(PropertyKeys.var, var);
    }

    public String getModel() {
        return (String) getStateHelper().get(PropertyKeys.model);
    }

    public void setModel(String model) {
        getStateHelper().put(PropertyKeys.model, model);
    }

    @Override
    public Object getValue() {
        Object value = super.getValue();
        if (value instanceof ListProperty) {
            try {
                value = ((ListProperty) value).getValue();
                value = FieldAdapterManager.getValueForDisplay(value);
            } catch (PropertyException e) {
            }
        }
        return value;
    }

    @Override
    public void setValue(Object value) {
        setDataModel(null);
        super.setValue(value);
    }

    public boolean isDiff() {
        Boolean b = (Boolean) getStateHelper().get(PropertyKeys.diff);
        return b == null ? false : b.booleanValue();
    }

    public void setDiff(boolean diff) {
        getStateHelper().put(PropertyKeys.diff, diff);
    }

    public Integer getNumber() {
        return (Integer) getStateHelper().get(PropertyKeys.number);
    }

    public void setNumber(Integer number) {
        getStateHelper().put(PropertyKeys.number, number);
    }

    public Object getTemplate() {
        return getStateHelper().get(PropertyKeys.template);
    }

    public void setTemplate(String template) {
        getStateHelper().put(PropertyKeys.template, template);
    }

    /**
     * <p>
     * Return a client identifier for this component that includes the current value of the <code>rowIndex</code>
     * property, if it is not set to -1. This implies that multiple calls to <code>getClientId()</code> may return
     * different results, but ensures that child components can themselves generate row-specific client identifiers
     * (since {@link UIData} is a {@link NamingContainer}).
     * </p>
     *
     * @throws NullPointerException if <code>context</code> is <code>null</code>
     */
    @Override
    public String getClientId(FacesContext context) {

        if (context == null) {
            throw new NullPointerException();
        }

        // If baseClientId and clientIdBuilder are both null, this is the
        // first time that getClientId() has been called.
        // If we're not nested within another UIData, then:
        // - create a new StringBuilder assigned to clientIdBuilder containing
        // our client ID.
        // - toString() the builder - this result will be our baseClientId
        // for the duration of the component
        // - append UINamingContainer.getSeparatorChar() to the builder
        // If we are nested within another UIData, then:
        // - create an empty StringBuilder that will be used to build
        // this instance's ID
        if (baseClientId == null && clientIdBuilder == null) {
            if (!isNestedWithinIterator()) {
                clientIdBuilder = new StringBuilder(super.getClientId(context));
                baseClientId = clientIdBuilder.toString();
                baseClientIdLength = (baseClientId.length() + 1);
                clientIdBuilder.append(UINamingContainer.getSeparatorChar(context));
                clientIdBuilder.setLength(baseClientIdLength);
            } else {
                clientIdBuilder = new StringBuilder();
            }
        }
        int rowIndex = getRowIndex();
        if (rowIndex >= 0) {
            String cid;
            if (!isNestedWithinIterator()) {
                // we're not nested, so the clientIdBuilder is already
                // primed with clientID +
                // UINamingContainer.getSeparatorChar(). Append the
                // current rowIndex, and toString() the builder. reset
                // the builder to it's primed state.
                cid = clientIdBuilder.append(rowIndex).toString();
                clientIdBuilder.setLength(baseClientIdLength);
            } else {
                // we're nested, so we have to build the ID from scratch
                // each time. Reuse the same clientIdBuilder instance
                // for each call by resetting the length to 0 after
                // the ID has been computed.
                cid = clientIdBuilder.append(super.getClientId(context)).append(
                        UINamingContainer.getSeparatorChar(context)).append(rowIndex).toString();
                clientIdBuilder.setLength(0);
            }
            return (cid);
        } else if (rowIndex == -2) {
            // template use case: use a marker instead of row index for easier js replacements on client side.
            String cid;
            if (!isNestedWithinIterator()) {
                cid = clientIdBuilder.append(TEMPLATE_INDEX_MARKER).toString();
                clientIdBuilder.setLength(baseClientIdLength);
            } else {
                cid = clientIdBuilder.append(super.getClientId(context)).append(
                        UINamingContainer.getSeparatorChar(context)).append(TEMPLATE_INDEX_MARKER).toString();
                clientIdBuilder.setLength(0);
            }
            return (cid);
        } else {
            if (!isNestedWithinIterator()) {
                // Not nested and no row available, so just return our baseClientId
                return (baseClientId);
            } else {
                // nested and no row available, return the result of getClientId().
                // this is necessary as the client ID will reflect the row that
                // this table represents
                return super.getClientId(context);
            }
        }

    }

    @Override
    public boolean invokeOnComponent(FacesContext context, String clientId, ContextCallback callback)
            throws FacesException {
        if (null == context || null == clientId || null == callback) {
            throw new NullPointerException();
        }

        String myId = super.getClientId(context);
        boolean found = false;
        if (clientId.equals(myId)) {
            try {
                pushComponentToEL(context, getCompositeComponentParent(this));
                callback.invokeContextCallback(context, this);
                return true;
            } catch (Exception e) {
                throw new FacesException(e);
            } finally {
                popComponentFromEL(context);
            }
        }

        // check the facets, if any, of UIData
        if (getFacetCount() > 0) {
            for (UIComponent c : getFacets().values()) {
                if (clientId.equals(c.getClientId(context))) {
                    callback.invokeContextCallback(context, c);
                    return true;
                }
            }
        }

        int lastSep, newRow, savedRowIndex = getRowIndex();
        try {
            char sepChar = UINamingContainer.getSeparatorChar(context);
            // If we need to strip out the rowIndex from our id
            // PENDING(edburns): is this safe with respect to I18N?
            if (myId.endsWith(sepChar + Integer.toString(savedRowIndex, 10))) {
                lastSep = myId.lastIndexOf(sepChar);
                assert (-1 != lastSep);
                myId = myId.substring(0, lastSep);
            }

            // myId will be something like form:outerData for a non-nested table,
            // and form:outerData:3:data for a nested table.
            // clientId will be something like form:outerData:3:outerColumn
            // for a non-nested table. clientId will be something like
            // outerData:3:data:3:input for a nested table.
            if (clientId.startsWith(myId)) {
                int preRowIndexSep, postRowIndexSep;

                if (-1 != (preRowIndexSep = clientId.indexOf(sepChar, myId.length()))) {
                    // Check the length
                    if (++preRowIndexSep < clientId.length()) {
                        if (-1 != (postRowIndexSep = clientId.indexOf(sepChar, preRowIndexSep + 1))) {
                            try {
                                newRow = Integer.valueOf(clientId.substring(preRowIndexSep, postRowIndexSep)).intValue();
                            } catch (NumberFormatException ex) {
                                // PENDING(edburns): I18N
                                String message = "Trying to extract rowIndex from clientId \'" + clientId + "\' "
                                        + ex.getMessage();
                                throw new NumberFormatException(message);
                            }
                            setRowIndex(newRow);
                            if (isRowAvailable()) {
                                found = super.invokeOnComponent(context, clientId, callback);
                            }
                        }
                    }
                }
            }
        } catch (FacesException fe) {
            throw fe;
        } catch (Exception e) {
            throw new FacesException(e);
        } finally {
            setRowIndex(savedRowIndex);
        }
        return found;
    }

    @Override
    public void queueEvent(FacesEvent event) {
        super.queueEvent(new WrapperEvent(this, event, getRowIndex()));
    }

    @Override
    public void broadcast(FacesEvent event) throws AbortProcessingException {

        if (!(event instanceof WrapperEvent)) {
            super.broadcast(event);
            return;
        }
        FacesContext context = FacesContext.getCurrentInstance();
        // Set up the correct context and fire our wrapped event
        WrapperEvent revent = (WrapperEvent) event;
        if (isNestedWithinIterator()) {
            setDataModel(null);
        }
        int oldRowIndex = getRowIndex();
        setRowIndex(revent.getRowIndex());
        FacesEvent rowEvent = revent.getFacesEvent();
        UIComponent source = rowEvent.getComponent();
        UIComponent compositeParent = null;
        try {
            if (!UIComponent.isCompositeComponent(source)) {
                compositeParent = UIComponent.getCompositeComponentParent(source);
            }
            if (compositeParent != null) {
                compositeParent.pushComponentToEL(context, null);
            }
            source.pushComponentToEL(context, null);
            source.broadcast(rowEvent);
        } finally {
            source.popComponentFromEL(context);
            if (compositeParent != null) {
                compositeParent.popComponentFromEL(context);
            }
        }
        setRowIndex(oldRowIndex);
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        preEncode(context);
        super.encodeBegin(context);
    }

    @Override
    public void encodeEnd(FacesContext context) throws IOException {
        super.encodeEnd(context);
    }

    @Override
    public boolean getRendersChildren() {
        return true;
    }

    /**
     * Repeatedly render the children as many times as needed.
     */
    @Override
    public void encodeChildren(final FacesContext context) throws IOException {
        if (!isRendered()) {
            return;
        }

        iterate(context, PhaseId.RENDER_RESPONSE);

        encodeTemplate(context);
    }

    /**
     * Renders an element using rowIndex -2 and client side marker {@link #TEMPLATE_INDEX_MARKER}.
     * <p>
     * This element will be used on client side by js code to handle addition of a new element.
     */
    protected void encodeTemplate(FacesContext context) {
        int oldIndex = getRowIndex();
        setRowIndexWithoutRowStatePreserved(-2);

        // expose a boolean that can be used on client side to hide this element without disturbing the DOM
        Map<String, Object> requestMap = getFacesContext().getExternalContext().getRequestMap();
        boolean hasVar = false;
        if (requestMap.containsKey(IS_LIST_TEMPLATE_VAR)) {
            hasVar = true;
        }
        Object oldIsTemplateBoolean = requestMap.remove(IS_LIST_TEMPLATE_VAR);
        requestMap.put(IS_LIST_TEMPLATE_VAR, Boolean.TRUE);

        if (getChildCount() > 0) {
            for (UIComponent kid : getChildren()) {
                if (!kid.isRendered()) {
                    continue;
                }
                try {
                    ComponentSupport.encodeRecursive(context, kid);
                } catch (IOException err) {
                    log.error("Error while rendering component " + kid);
                }
            }
        }
        setRowIndexWithoutRowStatePreserved(oldIndex);

        // restore
        if (hasVar) {
            requestMap.put(IS_LIST_TEMPLATE_VAR, oldIsTemplateBoolean);
        } else {
            requestMap.remove(IS_LIST_TEMPLATE_VAR);
        }
    }

    @Override
    public void processDecodes(FacesContext context) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (!isRendered()) {
            return;
        }

        pushComponentToEL(context, this);
        preDecode(context);
        iterate(context, PhaseId.APPLY_REQUEST_VALUES);
        decode(context);
        popComponentFromEL(context);

    }

    @Override
    public void processValidators(FacesContext context) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (!isRendered()) {
            return;
        }
        pushComponentToEL(context, this);
        Application app = context.getApplication();
        app.publishEvent(context, PreValidateEvent.class, this);
        preValidate(context);
        iterate(context, PhaseId.PROCESS_VALIDATIONS);
        app.publishEvent(context, PostValidateEvent.class, this);
        popComponentFromEL(context);

    }

    @Override
    public void processUpdates(FacesContext context) {

        if (context == null) {
            throw new NullPointerException();
        }
        if (!isRendered()) {
            return;
        }

        pushComponentToEL(context, this);
        preUpdate(context);
        iterate(context, PhaseId.UPDATE_MODEL_VALUES);
        popComponentFromEL(context);

        // TODO
        EditableModel model = getDataModel();
        if (model == null) {
            // do nothing
            setSubmittedValue(null);
            setValid(true);
            return;
        }
        Object submitted = model.getWrappedData();
        if (submitted == null) {
            // set submitted to empty list to force validation
            submitted = Collections.emptyList();
        }
        setSubmittedValue(submitted);

        // execute validate now that value is submitted
        executeValidate(context);

        if (isValid() && isLocalValueSet()) {
            if (isDiff()) {
                // TODO
                // set list diff instead of the whole list
                // setValue(model.getListDiff());
            }
        }

        try {
            updateModel(context);
        } catch (RuntimeException e) {
            context.renderResponse();
            throw e;
        }

        if (!isValid()) {
            context.renderResponse();
        }
    }

    private void executeValidate(FacesContext context) {
        try {
            validate(context);
        } catch (RuntimeException e) {
            context.renderResponse();
            throw e;
        }

        if (!isValid()) {
            context.renderResponse();
            // FIXME
            // throw new ValidationException("blah");
        }
    }

    public String createUniqueId(FacesContext context, String seed) {
        Integer i = (Integer) getStateHelper().get(PropertyKeys.lastId);
        int lastId = ((i != null) ? i : 0);
        getStateHelper().put(PropertyKeys.lastId, ++lastId);
        return UIViewRoot.UNIQUE_ID_PREFIX + (seed == null ? lastId : seed);
    }

    @Override
    public boolean visitTree(VisitContext context, VisitCallback callback) {

        // First check to see whether we are visitable. If not
        // short-circuit out of this subtree, though allow the
        // visit to proceed through to other subtrees.
        if (!isVisitable(context)) {
            return false;
        }

        FacesContext facesContext = context.getFacesContext();
        // NOTE: that the visitRows local will be obsolete once the
        // appropriate visit hints have been added to the API
        boolean visitRows = requiresRowIteration(context);

        // Clear out the row index is one is set so that
        // we start from a clean slate.
        int oldRowIndex = -1;
        if (visitRows) {
            oldRowIndex = getRowIndex();
            setRowIndex(-1);
        }

        // Push ourselves to EL
        pushComponentToEL(facesContext, null);

        try {

            // Visit ourselves. Note that we delegate to the
            // VisitContext to actually perform the visit.
            VisitResult result = context.invokeVisitCallback(this, callback);

            // If the visit is complete, short-circuit out and end the visit
            if (result == VisitResult.COMPLETE) {
                return true;
            }

            // Visit children, short-circuiting as necessary
            // NOTE: that the visitRows parameter will be obsolete once the
            // appropriate visit hints have been added to the API
            if ((result == VisitResult.ACCEPT) && doVisitChildren(context, visitRows)) {

                // First visit facets
                // NOTE: that the visitRows parameter will be obsolete once the
                // appropriate visit hints have been added to the API
                if (visitFacets(context, callback, visitRows)) {
                    return true;
                }

                // And finally, visit rows
                // NOTE: that the visitRows parameter will be obsolete once the
                // appropriate visit hints have been added to the API
                if (visitRows(context, callback, visitRows)) {
                    return true;
                }
            }
        } finally {
            // Clean up - pop EL and restore old row index
            popComponentFromEL(facesContext);
            if (visitRows) {
                setRowIndex(oldRowIndex);
            }
        }

        // Return false to allow the visit to continue
        return false;
    }

    @Override
    public void markInitialState() {
        if (getFacesContext().getAttributes().containsKey(StateManager.IS_BUILDING_INITIAL_STATE)) {
            _initialDescendantFullComponentState = saveDescendantInitialComponentStates(getFacesContext(),
                    getChildren().iterator(), false);
        }
        super.markInitialState();
    }

    @SuppressWarnings("unchecked")
    private void restoreFullDescendantComponentStates(FacesContext facesContext, Iterator<UIComponent> childIterator,
            Object state, boolean restoreChildFacets) {
        Iterator<? extends Object[]> descendantStateIterator = null;
        while (childIterator.hasNext()) {
            if (descendantStateIterator == null && state != null) {
                descendantStateIterator = ((Collection<? extends Object[]>) state).iterator();
            }
            UIComponent component = childIterator.next();

            // reset the client id (see spec 3.1.6)
            component.setId(component.getId());
            if (!component.isTransient()) {
                Object childState = null;
                Object descendantState = null;
                if (descendantStateIterator != null && descendantStateIterator.hasNext()) {
                    Object[] object = descendantStateIterator.next();
                    childState = object[0];
                    descendantState = object[1];
                }

                component.clearInitialState();
                component.restoreState(facesContext, childState);
                component.markInitialState();

                Iterator<UIComponent> childsIterator;
                if (restoreChildFacets) {
                    childsIterator = component.getFacetsAndChildren();
                } else {
                    childsIterator = component.getChildren().iterator();
                }
                restoreFullDescendantComponentStates(facesContext, childsIterator, descendantState, true);
            }
        }
    }

    private Collection<Object[]> saveDescendantInitialComponentStates(FacesContext facesContext,
            Iterator<UIComponent> childIterator, boolean saveChildFacets) {
        Collection<Object[]> childStates = null;
        while (childIterator.hasNext()) {
            if (childStates == null) {
                childStates = new ArrayList<Object[]>();
            }

            UIComponent child = childIterator.next();
            if (!child.isTransient()) {
                // Add an entry to the collection, being an array of two
                // elements. The first element is the state of the children
                // of this component; the second is the state of the current
                // child itself.

                Iterator<UIComponent> childsIterator;
                if (saveChildFacets) {
                    childsIterator = child.getFacetsAndChildren();
                } else {
                    childsIterator = child.getChildren().iterator();
                }
                Object descendantState = saveDescendantInitialComponentStates(facesContext, childsIterator, true);
                Object state = child.saveState(facesContext);
                childStates.add(new Object[] { state, descendantState });
            }
        }
        return childStates;
    }

    private Map<String, Object> saveFullDescendantComponentStates(FacesContext facesContext,
            Map<String, Object> stateMap, Iterator<UIComponent> childIterator, boolean saveChildFacets) {
        while (childIterator.hasNext()) {
            UIComponent child = childIterator.next();
            if (!child.isTransient()) {
                Iterator<UIComponent> childsIterator;
                if (saveChildFacets) {
                    childsIterator = child.getFacetsAndChildren();
                } else {
                    childsIterator = child.getChildren().iterator();
                }
                stateMap = saveFullDescendantComponentStates(facesContext, stateMap, childsIterator, true);
                Object state = child.saveState(facesContext);
                if (state != null) {
                    if (stateMap == null) {
                        stateMap = new HashMap<String, Object>();
                    }
                    stateMap.put(child.getClientId(facesContext), state);
                }
            }
        }
        return stateMap;
    }

    @SuppressWarnings("unchecked")
    private void restoreFullDescendantComponentDeltaStates(FacesContext facesContext,
            Iterator<UIComponent> childIterator, Object state, Object initialState, boolean restoreChildFacets) {
        Map<String, Object> descendantStateIterator = null;
        Iterator<? extends Object[]> descendantFullStateIterator = null;
        while (childIterator.hasNext()) {
            if (descendantStateIterator == null && state != null) {
                descendantStateIterator = (Map<String, Object>) state;
            }
            if (descendantFullStateIterator == null && initialState != null) {
                descendantFullStateIterator = ((Collection<? extends Object[]>) initialState).iterator();
            }
            UIComponent component = childIterator.next();

            // reset the client id (see spec 3.1.6)
            component.setId(component.getId());
            if (!component.isTransient()) {
                Object childInitialState = null;
                Object descendantInitialState = null;
                Object childState = null;
                if (descendantStateIterator != null
                        && descendantStateIterator.containsKey(component.getClientId(facesContext))) {
                    // Object[] object = (Object[]) descendantStateIterator.get(component.getClientId(facesContext));
                    // childState = object[0];
                    childState = descendantStateIterator.get(component.getClientId(facesContext));
                }
                if (descendantFullStateIterator != null && descendantFullStateIterator.hasNext()) {
                    Object[] object = descendantFullStateIterator.next();
                    childInitialState = object[0];
                    descendantInitialState = object[1];
                }

                component.clearInitialState();
                if (childInitialState != null) {
                    component.restoreState(facesContext, childInitialState);
                    component.markInitialState();
                    component.restoreState(facesContext, childState);
                } else {
                    component.restoreState(facesContext, childState);
                    component.markInitialState();
                }

                Iterator<UIComponent> childsIterator;
                if (restoreChildFacets) {
                    childsIterator = component.getFacetsAndChildren();
                } else {
                    childsIterator = component.getChildren().iterator();
                }
                restoreFullDescendantComponentDeltaStates(facesContext, childsIterator, state, descendantInitialState,
                        true);
            }
        }
    }

    private void restoreTransientDescendantComponentStates(FacesContext facesContext,
            Iterator<UIComponent> childIterator, Map<String, Object> state, boolean restoreChildFacets) {
        while (childIterator.hasNext()) {
            UIComponent component = childIterator.next();

            // reset the client id (see spec 3.1.6)
            component.setId(component.getId());
            if (!component.isTransient()) {
                component.restoreTransientState(facesContext,
                        (state == null) ? null : state.get(component.getClientId(facesContext)));

                Iterator<UIComponent> childsIterator;
                if (restoreChildFacets) {
                    childsIterator = component.getFacetsAndChildren();
                } else {
                    childsIterator = component.getChildren().iterator();
                }
                restoreTransientDescendantComponentStates(facesContext, childsIterator, state, true);
            }
        }

    }

    private Map<String, Object> saveTransientDescendantComponentStates(FacesContext facesContext,
            Map<String, Object> childStates, Iterator<UIComponent> childIterator, boolean saveChildFacets) {
        while (childIterator.hasNext()) {
            UIComponent child = childIterator.next();
            if (!child.isTransient()) {
                Iterator<UIComponent> childsIterator;
                if (saveChildFacets) {
                    childsIterator = child.getFacetsAndChildren();
                } else {
                    childsIterator = child.getChildren().iterator();
                }
                childStates = saveTransientDescendantComponentStates(facesContext, childStates, childsIterator, true);
                Object state = child.saveTransientState(facesContext);
                if (state != null) {
                    if (childStates == null) {
                        childStates = new HashMap<String, Object>();
                    }
                    childStates.put(child.getClientId(facesContext), state);
                }
            }
        }
        return childStates;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(FacesContext context, Object state) {
        if (state == null) {
            return;
        }

        Object values[] = (Object[]) state;
        super.restoreState(context, values[0]);
        Object restoredRowStates = UIComponentBase.restoreAttachedState(context, values[1]);
        if (restoredRowStates == null) {
            if (!_rowDeltaStates.isEmpty()) {
                _rowDeltaStates.clear();
            }
        } else {
            _rowDeltaStates = (Map<String, Object>) restoredRowStates;
        }
    }

    private void resetClientIds(UIComponent component) {
        Iterator<UIComponent> iterator = component.getFacetsAndChildren();
        while (iterator.hasNext()) {
            UIComponent child = iterator.next();
            resetClientIds(child);
            child.setId(child.getId());
        }
    }

    @Override
    public Object saveState(FacesContext context) {
        resetClientIds(this);

        if (initialStateMarked()) {
            Object superState = super.saveState(context);

            if (superState == null && _rowDeltaStates.isEmpty()) {
                return null;
            } else {
                Object values[] = null;
                Object attachedState = UIComponentBase.saveAttachedState(context, _rowDeltaStates);
                if (superState != null || attachedState != null) {
                    values = new Object[] { superState, attachedState };
                }
                return values;
            }
        } else {
            Object values[] = new Object[2];
            values[0] = super.saveState(context);
            values[1] = UIComponentBase.saveAttachedState(context, _rowDeltaStates);
            return values;
        }
    }

    // --------------------------------------------------------- Protected Methods

    /**
     * <p>
     * Return the internal {@link DataModel} object representing the data objects that we will iterate over in this
     * component's rendering.
     * </p>
     * <p/>
     * <p>
     * If the model has been cached by a previous call to {@link #setDataModel}, return it. Otherwise call
     * {@link #getValue}. If the result is null, create an empty {@link ListDataModel} and return it. If the result is
     * an instance of {@link DataModel}, return it. Otherwise, adapt the result as described in {@link #getValue} and
     * return it.
     * </p>
     */
    protected EditableModel getDataModel() {
        if (model != null) {
            return (model);
        }

        EditableModel model = new EditableModelImpl(getValue());
        Integer defaultNumber = getNumber();
        int missing = 0;
        if (defaultNumber != null) {
            missing = defaultNumber - model.size();
        }
        if (defaultNumber != null && missing > 0) {
            try {
                Object template = getTemplate();
                if (template instanceof Serializable) {
                    Serializable serializableTemplate = (Serializable) template;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(out);
                    oos.writeObject(serializableTemplate);
                    oos.close();
                    for (int i = 0; i < missing; i++) {
                        // deserialize to make sure it is not the same instance
                        byte[] pickled = out.toByteArray();
                        InputStream in = new ByteArrayInputStream(pickled);
                        ObjectInputStream ois = new ObjectInputStream(in);
                        Object newTemplate = ois.readObject();
                        model.addValue(newTemplate);
                    }
                } else {
                    log.warn("Template is not serializable, cannot clone to add unreferenced value into model.");
                    model.addValue(template);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        model.setRowIndex(-1);
        assert model.getRowIndex() == -1 : "RowIndex did not reset to -1";
        setDataModel(model);
        return model;
    }

    /**
     * <p>
     * Set the internal DataModel. This <code>UIData</code> instance must use the given {@link DataModel} as its
     * internal value representation from now until the next call to <code>setDataModel</code>. If the given
     * <code>DataModel</code> is <code>null</code>, the internal <code>DataModel</code> must be reset in a manner so
     * that the next call to {@link #getDataModel} causes lazy instantion of a newly refreshed <code>DataModel</code>.
     * </p>
     * <p/>
     * <p>
     * Subclasses might call this method if they either want to restore the internal <code>DataModel</code> during the
     * <em>Restore View</em> phase or if they want to explicitly refresh the current <code>DataModel</code> for the
     * <em>Render Response</em> phase.
     * </p>
     *
     * @param dataModel the new <code>DataModel</code> or <code>null</code> to cause the model to be refreshed.
     */
    protected void setDataModel(EditableModel dataModel) {
        model = dataModel;
    }

    // ---------------------------------------------------- Private Methods

    /**
     * Called by {@link UIData#visitTree} to determine whether or not the <code>visitTree</code> implementation should
     * visit the rows of UIData or by manipulating the row index before visiting the components themselves. Once we have
     * the appropriate Visit hints for state saving, this method will become obsolete.
     *
     * @param ctx the <code>FacesContext</code> for the current request
     * @return true if row index manipulation is required by the visit to this UIData instance
     */
    private boolean requiresRowIteration(VisitContext ctx) {
        return !ctx.getHints().contains(VisitHint.SKIP_ITERATION);
    }

    // Perform pre-decode initialization work. Note that this
    // initialization may be performed either during a normal decode
    // (ie. processDecodes()) or during a tree visit (ie. visitTree()).
    @SuppressWarnings("unchecked")
    private void preDecode(FacesContext context) {
        setDataModel(null); // Re-evaluate even with server-side state saving
        Map<String, SavedState> saved = (Map<String, SavedState>) getStateHelper().get(PropertyKeys.saved);
        if (null == saved || !keepSaved(context)) {
            // noinspection CollectionWithoutInitialCapacity
            getStateHelper().remove(PropertyKeys.saved);
        }
    }

    // Perform pre-validation initialization work. Note that this
    // initialization may be performed either during a normal validation
    // (ie. processValidators()) or during a tree visit (ie. visitTree()).
    private void preValidate(FacesContext context) {
        if (isNestedWithinIterator()) {
            setDataModel(null);
        }
    }

    // Perform pre-update initialization work. Note that this
    // initialization may be performed either during normal update
    // (ie. processUpdates()) or during a tree visit (ie. visitTree()).
    private void preUpdate(FacesContext context) {
        if (isNestedWithinIterator()) {
            setDataModel(null);
        }
    }

    // Perform pre-encode initialization work. Note that this
    // initialization may be performed either during a normal encode
    // (ie. encodeBegin()) or during a tree visit (ie. visitTree()).
    private void preEncode(FacesContext context) {
        setDataModel(null); // re-evaluate even with server-side state saving
        if (!keepSaved(context)) {
            // //noinspection CollectionWithoutInitialCapacity
            // saved = new HashMap<String, SavedState>();
            getStateHelper().remove(PropertyKeys.saved);
        }
    }

    private void iterate(FacesContext context, PhaseId phaseId) {

        // Process each facet of this component exactly once
        setRowIndex(-1);
        if (getFacetCount() > 0) {
            for (UIComponent facet : getFacets().values()) {
                if (phaseId == PhaseId.APPLY_REQUEST_VALUES) {
                    facet.processDecodes(context);
                } else if (phaseId == PhaseId.PROCESS_VALIDATIONS) {
                    facet.processValidators(context);
                } else if (phaseId == PhaseId.UPDATE_MODEL_VALUES) {
                    facet.processUpdates(context);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }

        int processed = 0;
        int rowIndex = getFirst() - 1;
        int rows = getRows();

        while (true) {

            // Have we processed the requested number of rows?
            if ((rows > 0) && (++processed > rows)) {
                break;
            }

            // Expose the current row in the specified request attribute
            setRowIndex(++rowIndex);
            if (!isRowAvailable()) {
                break; // Scrolled past the last row
            }

            if (getChildCount() > 0) {
                for (UIComponent kid : getChildren()) {
                    if (!kid.isRendered()) {
                        continue;
                    }
                    if (phaseId == PhaseId.APPLY_REQUEST_VALUES) {
                        kid.processDecodes(context);
                    } else if (phaseId == PhaseId.PROCESS_VALIDATIONS) {
                        kid.processValidators(context);
                    } else if (phaseId == PhaseId.UPDATE_MODEL_VALUES) {
                        kid.processUpdates(context);
                    } else if (phaseId == PhaseId.RENDER_RESPONSE) {
                        try {
                            ComponentSupport.encodeRecursive(context, kid);
                        } catch (IOException err) {
                            log.error("Error while rendering component " + kid);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid PhaseId:" + phaseId);
                    }
                }
            }

        }

        // Clean up after ourselves
        setRowIndex(-1);

    }

    // Tests whether we need to visit our children as part of
    // a tree visit
    private boolean doVisitChildren(VisitContext context, boolean visitRows) {

        // Just need to check whether there are any ids under this
        // subtree. Make sure row index is cleared out since
        // getSubtreeIdsToVisit() needs our row-less client id.
        if (visitRows) {
            setRowIndex(-1);
        }
        Collection<String> idsToVisit = context.getSubtreeIdsToVisit(this);
        assert (idsToVisit != null);

        // All ids or non-empty collection means we need to visit our children.
        return (!idsToVisit.isEmpty());
    }

    // Visit each facet of this component exactly once.
    private boolean visitFacets(VisitContext context, VisitCallback callback, boolean visitRows) {

        if (visitRows) {
            setRowIndex(-1);
        }
        if (getFacetCount() > 0) {
            for (UIComponent facet : getFacets().values()) {
                if (facet.visitTree(context, callback)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Visit each column and row
    private boolean visitRows(VisitContext context, VisitCallback callback, boolean visitRows) {

        int processed = 0;
        int rowIndex = 0;
        int rows = 0;
        if (visitRows) {
            rowIndex = getFirst() - 1;
            rows = getRows();
        }

        while (true) {

            // Have we processed the requested number of rows?
            if (visitRows) {
                if ((rows > 0) && (++processed > rows)) {
                    break;
                }
                // Expose the current row in the specified request attribute
                setRowIndex(++rowIndex);
                if (!isRowAvailable()) {
                    break; // Scrolled past the last row
                }
            }

            if (getChildCount() > 0) {
                for (UIComponent kid : getChildren()) {
                    if (kid.visitTree(context, callback)) {
                        return true;
                    }
                }
            }

            if (!visitRows) {
                break;
            }

        }

        return false;
    }

    /**
     * <p>
     * Return <code>true</code> if we need to keep the saved per-child state information. This will be the case if any
     * of the following are true:
     * </p>
     * <ul>
     * <li>there are messages queued with severity ERROR or FATAL.</li>
     * <li>this <code>UIData</code> instance is nested inside of another <code>UIData</code> instance</li>
     * </ul>
     *
     * @param context {@link FacesContext} for the current request
     */
    private boolean keepSaved(FacesContext context) {
        return (contextHasErrorMessages(context) || isNestedWithinIterator());
    }

    private Boolean isNestedWithinIterator() {
        if (isNested == null) {
            UIComponent parent = this;
            while (null != (parent = parent.getParent())) {
                if (parent instanceof UIData || parent instanceof UIJavascriptList || parent instanceof UIEditableList
                        || parent.getClass().getName().endsWith("UIRepeat")) {
                    isNested = Boolean.TRUE;
                    break;
                }
            }
            if (isNested == null) {
                isNested = Boolean.FALSE;
            }
            return isNested;
        } else {
            return isNested;
        }
    }

    private boolean contextHasErrorMessages(FacesContext context) {
        FacesMessage.Severity sev = context.getMaximumSeverity();
        return (sev != null && (FacesMessage.SEVERITY_ERROR.compareTo(sev) >= 0));
    }

    private void restoreDescendantState() {

        FacesContext context = getFacesContext();
        if (getChildCount() > 0) {
            for (UIComponent kid : getChildren()) {
                if (kid instanceof UIColumn) {
                    restoreDescendantState(kid, context);
                }
            }
        }

    }

    @SuppressWarnings("unchecked")
    private void restoreDescendantState(UIComponent component, FacesContext context) {

        // Reset the client identifier for this component
        String id = component.getId();
        component.setId(id); // Forces client id to be reset
        Map<String, SavedState> saved = (Map<String, SavedState>) getStateHelper().get(PropertyKeys.saved);
        // Restore state for this component (if it is a EditableValueHolder)
        if (component instanceof EditableValueHolder) {
            EditableValueHolder input = (EditableValueHolder) component;
            String clientId = component.getClientId(context);

            SavedState state = (saved == null ? null : saved.get(clientId));
            if (state == null) {
                input.resetValue();
            } else {
                input.setValue(state.getValue());
                input.setValid(state.isValid());
                input.setSubmittedValue(state.getSubmittedValue());
                // This *must* be set after the call to setValue(), since
                // calling setValue() always resets "localValueSet" to true.
                input.setLocalValueSet(state.isLocalValueSet());
            }
        } else if (component instanceof UIForm) {
            UIForm form = (UIForm) component;
            String clientId = component.getClientId(context);
            SavedState state = (saved == null ? null : saved.get(clientId));
            if (state == null) {
                // submitted is transient state
                form.setSubmitted(false);
            } else {
                form.setSubmitted(state.getSubmitted());
            }
        }

        // Restore state for children of this component
        if (component.getChildCount() > 0) {
            for (UIComponent kid : component.getChildren()) {
                restoreDescendantState(kid, context);
            }
        }

        // Restore state for facets of this component
        if (component.getFacetCount() > 0) {
            for (UIComponent facet : component.getFacets().values()) {
                restoreDescendantState(facet, context);
            }
        }

    }

    private void saveDescendantState() {

        FacesContext context = getFacesContext();
        if (getChildCount() > 0) {
            for (UIComponent kid : getChildren()) {
                if (kid instanceof UIColumn) {
                    saveDescendantState(kid, context);
                }
            }
        }

    }

    @SuppressWarnings("unchecked")
    private void saveDescendantState(UIComponent component, FacesContext context) {

        // Save state for this component (if it is a EditableValueHolder)
        Map<String, SavedState> saved = (Map<String, SavedState>) getStateHelper().get(PropertyKeys.saved);
        if (component instanceof EditableValueHolder) {
            EditableValueHolder input = (EditableValueHolder) component;
            SavedState state = null;
            String clientId = component.getClientId(context);
            if (saved == null) {
                state = new SavedState();
            }
            if (state == null) {
                state = saved.get(clientId);
                if (state == null) {
                    state = new SavedState();
                }
            }
            state.setValue(input.getLocalValue());
            state.setValid(input.isValid());
            state.setSubmittedValue(input.getSubmittedValue());
            state.setLocalValueSet(input.isLocalValueSet());
            if (state.hasDeltaState()) {
                getStateHelper().put(PropertyKeys.saved, clientId, state);
            } else if (saved != null) {
                getStateHelper().remove(PropertyKeys.saved, clientId);
            }
        } else if (component instanceof UIForm) {
            UIForm form = (UIForm) component;
            String clientId = component.getClientId(context);
            SavedState state = null;
            if (saved == null) {
                state = new SavedState();
            }
            if (state == null) {
                state = saved.get(clientId);
                if (state == null) {
                    state = new SavedState();
                }
            }
            state.setSubmitted(form.isSubmitted());
            if (state.hasDeltaState()) {
                getStateHelper().put(PropertyKeys.saved, clientId, state);
            } else if (saved != null) {
                getStateHelper().remove(PropertyKeys.saved, clientId);
            }
        }

        // Save state for children of this component
        if (component.getChildCount() > 0) {
            for (UIComponent uiComponent : component.getChildren()) {
                saveDescendantState(uiComponent, context);
            }
        }

        // Save state for facets of this component
        if (component.getFacetCount() > 0) {
            for (UIComponent facet : component.getFacets().values()) {
                saveDescendantState(facet, context);
            }
        }

    }

}

class SavedState implements Serializable {

    private static final long serialVersionUID = 1L;

    private Object submittedValue;

    private boolean submitted;

    Object getSubmittedValue() {
        return (submittedValue);
    }

    void setSubmittedValue(Object submittedValue) {
        this.submittedValue = submittedValue;
    }

    private boolean valid = true;

    boolean isValid() {
        return (valid);
    }

    void setValid(boolean valid) {
        this.valid = valid;
    }

    private Object value;

    Object getValue() {
        return (value);
    }

    public void setValue(Object value) {
        this.value = value;
    }

    private boolean localValueSet;

    boolean isLocalValueSet() {
        return (localValueSet);
    }

    public void setLocalValueSet(boolean localValueSet) {
        this.localValueSet = localValueSet;
    }

    public boolean getSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }

    public boolean hasDeltaState() {
        return submittedValue != null || value != null || localValueSet || !valid || submitted;
    }

    @Override
    public String toString() {
        return ("submittedValue: " + submittedValue + " value: " + value + " localValueSet: " + localValueSet);
    }

}

// Private class to wrap an event with a row index
class WrapperEvent extends FacesEvent {

    private static final long serialVersionUID = 1L;

    public WrapperEvent(UIComponent component, FacesEvent event, int rowIndex) {
        super(component);
        this.event = event;
        this.rowIndex = rowIndex;
    }

    private FacesEvent event = null;

    private int rowIndex = -1;

    public FacesEvent getFacesEvent() {
        return (event);
    }

    public int getRowIndex() {
        return (rowIndex);
    }

    @Override
    public PhaseId getPhaseId() {
        return (event.getPhaseId());
    }

    @Override
    public void setPhaseId(PhaseId phaseId) {
        event.setPhaseId(phaseId);
    }

    @Override
    public boolean isAppropriateListener(FacesListener listener) {
        return (false);
    }

    @Override
    public void processListener(FacesListener listener) {
        throw new IllegalStateException();
    }

}
