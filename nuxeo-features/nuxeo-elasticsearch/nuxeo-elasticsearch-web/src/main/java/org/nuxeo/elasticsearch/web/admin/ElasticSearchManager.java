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
package org.nuxeo.elasticsearch.web.admin;

import static org.jboss.seam.ScopeType.EVENT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.api.ElasticSearchIndexing;
import org.nuxeo.elasticsearch.commands.IndexingCommand;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;

/**
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 */
@Name("esAdmin")
@Scope(EVENT)
public class ElasticSearchManager {

    private static final Log log = LogFactory.getLog(ElasticSearchManager.class);

    private static final String DEFAULT_NXQL_QUERY = "SELECT * FROM Document";

    private static final String JSON_DELETE_CMD = "{\"id\":\"IndexingCommand-reindex\",\"name\":\"ES_DELETE\",\"docId\":\"%s\",\"repo\":\"%s\",\"recurse\":true,\"sync\":true}";

    @In(create = true)
    protected ElasticSearchAdmin esa;

    @In(create = true)
    protected ElasticSearchIndexing esi;

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    protected List<PageProviderStatus> ppStatuses = null;

    protected Timer indexTimer;

    protected Timer bulkIndexTimer;

    private String rootId;

    private String nxql = DEFAULT_NXQL_QUERY;

    private String repositoryName;

    private Boolean dropIndex = false;

    public String getNodesInfo() {
        NodesInfoResponse nodesInfo = esa.getClient().admin().cluster().prepareNodesInfo().execute().actionGet();
        return nodesInfo.toString();
    }

    public String getNodesStats() {
        NodesStatsResponse stats = esa.getClient().admin().cluster().prepareNodesStats().execute().actionGet();
        return stats.toString();
    }

    public String getNodesHealth() {
        ClusterHealthResponse health = esa.getClient().admin().cluster().prepareHealth().execute().actionGet();
        return health.toString();
    }

    public void startReindexAll() {
        log.warn("Re-indexing the entire repository: " + repositoryName);
        esa.dropAndInitRepositoryIndex(repositoryName);
        esi.reindex(repositoryName, "SELECT ecm:uuid FROM Document");
    }

    public void startReindexNxql() {
        log.warn(String.format("Re-indexing from a NXQL query: %s on repository: %s", getNxql(), repositoryName));
        esi.reindex(repositoryName, getNxql());
    }

    public void startReindexFrom() {
        try (CoreSession session = CoreInstance.openCoreSessionSystem(repositoryName)) {
            log.warn(String.format("Try to remove %s and its children from %s repository index", rootId, repositoryName));
            String jsonCmd = String.format(JSON_DELETE_CMD, rootId, repositoryName);
            IndexingCommand rmCmd = IndexingCommand.fromJSON(session, jsonCmd);
            esi.indexNow(rmCmd);

            DocumentRef ref = new IdRef(rootId);
            if (session.exists(ref)) {
                DocumentModel doc = session.getDocument(ref);
                log.warn(String.format("Re-indexing document: %s and its children on repository: %s", doc,
                        repositoryName));
                IndexingCommand cmd = new IndexingCommand(doc, false, true);
                esi.scheduleIndexing(cmd);
            }
        }
    }

    public void flush() {
        esa.flush();
    }

    protected void introspectPageProviders() {

        ppStatuses = new ArrayList<>();

        PageProviderService pps = Framework.getLocalService(PageProviderService.class);
        for (String ppName : pps.getPageProviderDefinitionNames()) {
            PageProviderDefinition def = pps.getPageProviderDefinition(ppName);
            // Create an instance so class replacer is taken in account
            PageProvider<?> pp = pps.getPageProvider(ppName, def, null, null, 0L, 0L, null);
            String klass = pp.getClass().getCanonicalName();
            ppStatuses.add(new PageProviderStatus(ppName, klass));
        }
        Collections.sort(ppStatuses);
    }

    public List<PageProviderStatus> getContentViewStatus() {
        if (ppStatuses == null) {
            introspectPageProviders();
        }
        return ppStatuses;
    }

    public Boolean isIndexingInProgress() {
        return esa.isIndexingInProgress();
    }

    public String getPendingCommands() {
        return Integer.valueOf(esa.getPendingCommands()).toString();
    }

    public String getRunningCommands() {
        return Integer.valueOf(esa.getRunningCommands()).toString();
    }

    public String getTotalCommandProcessed() {
        return Integer.valueOf(esa.getTotalCommandProcessed()).toString();
    }

    public String getNumberOfDocuments() {
        NodesStatsResponse stats = esa.getClient().admin().cluster().prepareNodesStats().execute().actionGet();
        return Long.valueOf(stats.getNodes()[0].getIndices().getDocs().getCount()).toString();
    }

    public String getNumberOfDeletedDocuments() {
        NodesStatsResponse stats = esa.getClient().admin().cluster().prepareNodesStats().execute().actionGet();
        return Long.valueOf(stats.getNodes()[0].getIndices().getDocs().getDeleted()).toString();
    }

    public String getIndexingRates() {
        if (indexTimer == null) {
            MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
            indexTimer = registry.timer(MetricRegistry.name("nuxeo", "elasticsearch", "service", "index"));

        }
        return String.format("%.2f, %.2f, %.2f", indexTimer.getOneMinuteRate(), indexTimer.getFiveMinuteRate(),
                indexTimer.getFifteenMinuteRate());
    }

    public String getBulkIndexingRates() {
        if (bulkIndexTimer == null) {
            MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
            bulkIndexTimer = registry.timer(MetricRegistry.name("nuxeo", "elasticsearch", "service", "bulkIndex"));

        }
        return String.format("%.2f, %.2f, %.2f", bulkIndexTimer.getOneMinuteRate(), bulkIndexTimer.getFiveMinuteRate(),
                bulkIndexTimer.getFifteenMinuteRate());
    }

    public String getRootId() {
        return rootId;
    }

    public List<String> getRepositoryNames() {
        return esa.getRepositoryNames();
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public String getNxql() {
        return nxql;
    }

    public void setNxql(String nxql) {
        this.nxql = nxql;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public Boolean getDropIndex() {
        return dropIndex;
    }

    public void setDropIndex(Boolean dropIndex) {
        this.dropIndex = dropIndex;
    }
}
