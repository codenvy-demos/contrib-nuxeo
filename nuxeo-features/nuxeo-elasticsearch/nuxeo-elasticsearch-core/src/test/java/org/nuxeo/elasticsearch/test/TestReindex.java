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

package org.nuxeo.elasticsearch.test;

import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.trash.TrashService;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.api.ElasticSearchIndexing;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.listener.ElasticSearchInlineListener;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

/**
 * Test "on the fly" indexing via the listener system
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */

@RunWith(FeaturesRunner.class)
@Features({ RepositoryElasticSearchFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.tag" })
@LocalDeploy("org.nuxeo.elasticsearch.core:elasticsearch-test-contrib.xml")
public class TestReindex {

    @Inject
    protected CoreSession session;

    @Inject
    protected ElasticSearchService ess;

    @Inject
    protected ElasticSearchIndexing esi;

    @Inject
    protected TrashService trashService;

    @Inject
    ElasticSearchAdmin esa;

    @Inject
    protected TagService tagService;

    private int commandProcessed;

    private boolean syncMode = false;

    public void startCountingCommandProcessed() {
        Assert.assertEquals(0, esa.getPendingCommands());
        Assert.assertEquals(0, esa.getPendingDocs());
        commandProcessed = esa.getTotalCommandProcessed();
    }

    public void assertNumberOfCommandProcessed(int processed) throws Exception {
        Assert.assertEquals(processed, esa.getTotalCommandProcessed() - commandProcessed);
    }

    public void _waitForIndexing() throws Exception {
        int count = 10;
        for (int i = 0; (i < 1000) && (count > 0); i++) {
            Thread.sleep(100);
            if (esa.isIndexingInProgress()) {
                count = 3;
            } else {
                count--;
            }
        }
    }

    /**
     * Wait for sync and async job and refresh the index
     */
    public void waitForIndexing() throws Exception {
        _waitForIndexing();
        Assert.assertFalse("Still indexing in progress", esa.isIndexingInProgress());
        esa.refresh();
    }

    public void activateSynchronousMode() throws Exception {
        ElasticSearchInlineListener.useSyncIndexing.set(true);
        syncMode = true;
    }

    @After
    public void disableSynchronousMode() {
        ElasticSearchInlineListener.useSyncIndexing.set(false);
        syncMode = false;
    }

    public void startTransaction() {
        if (syncMode) {
            ElasticSearchInlineListener.useSyncIndexing.set(true);
        }
        if (!TransactionHelper.isTransactionActive()) {
            TransactionHelper.startTransaction();
        }
    }

    @After
    public void cleanupIndexed() throws Exception {
        esa.initIndexes(true);
    }

    @Test
    public void shouldReindexDocument() throws Exception {
        buildDocs();
        startTransaction();

        String nxql = "SELECT * FROM Document, Relation order by ecm:uuid";
        ElasticSearchService ess = Framework.getLocalService(ElasticSearchService.class);
        DocumentModelList coreDocs = session.query(nxql);
        DocumentModelList docs = ess.query(new NxQueryBuilder(session).nxql(nxql).limit(100));

        Assert.assertEquals(coreDocs.totalSize(), docs.totalSize());
        Assert.assertEquals(getDigest(coreDocs), getDigest(docs));
        // can not do that because of NXP-16154
        // Assert.assertEquals(getDigest(coreDocs), 42, docs.totalSize());
        esa.initIndexes(true);
        esa.refresh();
        DocumentModelList docs2 = ess.query(new NxQueryBuilder(session).nxql("SELECT * FROM Document"));
        Assert.assertEquals(0, docs2.totalSize());
        esi.reindex(session.getRepositoryName(), "SELECT * FROM Document");
        esi.reindex(session.getRepositoryName(), "SELECT * FROM Relation");
        waitForIndexing();
        docs2 = ess.query(new NxQueryBuilder(session).nxql(nxql).limit(100));

        Assert.assertEquals(getDigest(coreDocs), getDigest(docs2));

    }

    private void buildDocs() throws Exception {
        startTransaction();

        DocumentModel folder = session.createDocumentModel("/", "section", "Folder");
        session.createDocument(folder);
        folder = session.saveDocument(folder);
        for (int i = 0; i < 10; i++) {
            DocumentModel doc = session.createDocumentModel("/", "testDoc" + i, "File");
            doc.setPropertyValue("dc:title", "TestMe" + i);
            BlobHolder holder = doc.getAdapter(BlobHolder.class);
            holder.setBlob(new StringBlob("You know for search" + i));
            doc = session.createDocument(doc);
            tagService.tag(session, doc.getId(), "mytag" + i, "Administrator");
        }
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        waitForIndexing();
        startTransaction();

        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.getDocument(new PathRef("/testDoc" + i));
            doc.setPropertyValue("dc:description", "Description TestMe" + i);
            doc = session.saveDocument(doc);
            DocumentModel proxy = session.publishDocument(doc, folder);
            if (i % 2 == 0) {
                trashService.trashDocuments(Arrays.asList(doc));
            }
        }
        TransactionHelper.commitOrRollbackTransaction();
        waitForIndexing();
    }

    protected String getDigest(DocumentModelList docs) {
        StringBuilder sb = new StringBuilder();
        for (DocumentModel doc : docs) {
            String nameOrTitle = doc.getName();
            if (nameOrTitle == null || nameOrTitle.isEmpty()) {
                nameOrTitle = doc.getTitle();
            }
            sb.append(doc.getType() + " " + doc.isProxy() + " " + doc.getId() + " ");
            sb.append(nameOrTitle);
            sb.append("\n");
        }
        return sb.toString();
    }

}
