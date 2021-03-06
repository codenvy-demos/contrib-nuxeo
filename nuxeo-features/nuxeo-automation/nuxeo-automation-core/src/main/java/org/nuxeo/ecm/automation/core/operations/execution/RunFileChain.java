/*
 * Copyright (c) 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     bstefanescu
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.operations.execution;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.BlobList;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Run an embedded operation chain that returns a Blob using the current input.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Operation(id = RunFileChain.ID, category = Constants.CAT_SUBCHAIN_EXECUTION, label = "Run File Chain", description = "Run an operation chain which is returning a file in the current context. The input for the chain to run is a file or a list of files. Return the output of the chain as a file or a list of files. The 'parameters' injected are accessible in the subcontext ChainParameters. For instance, @{ChainParameters['parameterKey']}.")
public class RunFileChain {

    public static final String ID = "Context.RunFileOperation";

    @Context
    protected OperationContext ctx;

    @Context
    protected AutomationService service;

    @Context
    protected CoreSession session;

    @Param(name = "id")
    protected String chainId;

    @Param(name = "isolate", required = false, values = "false")
    protected boolean isolate = false;

    @Param(name = "parameters", description = "Accessible in the subcontext ChainParameters. For instance, @{ChainParameters['parameterKey']}.", required = false)
    protected Properties chainParameters;

    /**
     * @since 6.0 Define if the chain in parameter should be executed in new transaction.
     */
    @Param(name = "newTx", required = false, values = "false", description = "Define if the chain in parameter should be executed in new transaction.")
    protected boolean newTx = false;

    /**
     * @since 6.0 Define transaction timeout (default to 60 sec).
     */
    @Param(name = "timeout", required = false, description = "Define transaction timeout (default to 60 sec).")
    protected Integer timeout = 60;

    /**
     * @since 6.0 Define if transaction should rollback or not (default to true).
     */
    @Param(name = "rollbackGlobalOnError", required = false, values = "true", description = "Define if transaction should rollback or not (default to true)")
    protected boolean rollbackGlobalOnError = true;

    @OperationMethod
    public Blob run(Blob blob) throws OperationException {
        // Handle isolation option
        Map<String, Object> vars = isolate ? new HashMap<>(ctx.getVars()) : ctx.getVars();
        OperationContext subctx = ctx.getSubContext(isolate, blob);

        // Running chain/operation
        Blob result = null;
        if (newTx) {
            result = (Blob) service.runInNewTx(subctx, chainId, chainParameters, timeout, rollbackGlobalOnError);
        } else {
            result = (Blob) service.run(subctx, chainId, (Map) chainParameters);
        }

        // reconnect documents in the context
        if (!isolate) {
            for (String varName : vars.keySet()) {
                if (!ctx.getVars().containsKey(varName)) {
                    ctx.put(varName, vars.get(varName));
                } else {
                    Object value = vars.get(varName);
                    if (session != null && value != null && value instanceof DocumentModel) {
                        ctx.getVars().put(varName, session.getDocument(((DocumentModel) value).getRef()));
                    } else {
                        ctx.getVars().put(varName, value);
                    }
                }
            }
        }
        return result;
    }

    @OperationMethod
    public BlobList run(BlobList blobs) throws OperationException {
        BlobList result = new BlobList(blobs.size());
        for (Blob blob : blobs) {
            result.add(run(blob));
        }
        return result;
    }

}
