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
 *     Nuxeo
 */

package org.nuxeo.elasticsearch.work;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelIterator;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.elasticsearch.api.ElasticSearchIndexing;
import org.nuxeo.elasticsearch.commands.IndexingCommand;
import org.nuxeo.runtime.api.Framework;

/**
 * Worker to index children recursively
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
public class ChildrenIndexingWorker extends AbstractIndexingWorker implements Work {

    private static final long serialVersionUID = 1L;

    public ChildrenIndexingWorker(IndexingCommand cmd) {
        super(cmd);
    }

    @Override
    public String getTitle() {
        String title = " ElasticSearch indexing children for doc " + cmd.getDocId() + " in repository "
                + cmd.getRepository();
        if (path != null) {
            title = title + " (" + path + ")";
        }
        return title;
    }

    @Override
    protected void doIndexingWork(ElasticSearchIndexing esi, IndexingCommand cmd) {
        DocumentModel doc = cmd.getTargetDocument();
        DocumentModelIterator iter = session.getChildrenIterator(doc.getRef());
        while (iter.hasNext()) {
            // Add a session save to process cache invalidation
            session.save();
            DocumentModel child = iter.next();

            IndexingCommand childCommand = cmd.clone(child);

            if (!esi.isAlreadyScheduled(childCommand)) {
                esi.indexNow(childCommand);
            }
            if (child.isFolder()) {
                ChildrenIndexingWorker subWorker = new ChildrenIndexingWorker(childCommand);
                WorkManager wm = Framework.getLocalService(WorkManager.class);
                wm.schedule(subWorker);
            }
        }

    }

}
