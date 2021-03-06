/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.ui.web.auth.interfaces;

import org.nuxeo.ecm.platform.ui.web.auth.CachableUserIdentificationInfo;

public interface NuxeoAuthenticationPropagator {

    /**
     * Cleanup callback called when the filter return
     *
     * @since 5.6
     */
    interface CleanupCallback {
        void cleanup();
    }

    /**
     * Propagates userIdentification information from the web context to the ejb context.
     */
    CleanupCallback propagateUserIdentificationInformation(CachableUserIdentificationInfo cachableUserIdent);

}
