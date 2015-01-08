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
 *     bdelbosc
 */
package org.nuxeo.elasticsearch.test.rest;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.operations.services.DocumentPageProviderOperation;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentListWriter;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.restapi.server.jaxrs.QueryObject;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.ChildrenAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.adapters.PageProviderAdapter;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.ecm.restapi.test.RestServerInit;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.google.inject.Inject;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * Test the various ways to get elasticsearch Json output.
 *
 * @since 5.9.3
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, RestServerFeature.class, RepositoryElasticSearchFeature.class /*
                                                                                                       * ,
                                                                                                       * RandomBug.Feature
                                                                                                       * .class
                                                                                                       */})
@Jetty(port = 18090)
@Deploy("org.nuxeo.ecm.platform.contentview.jsf")
@LocalDeploy({ "org.nuxeo.ecm.platform.restapi.test:pageprovider-test-contrib.xml",
        "org.nuxeo.ecm.platform.restapi.test:elasticsearch-test-contrib.xml",
        "org.nuxeo.elasticsearch.core:contentviews-test-contrib.xml",
        "org.nuxeo.elasticsearch.core:contentviews-coretype-test-contrib.xml" })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class RestESDocumentsTest extends BaseTest {

    @Inject
    AutomationService automationService;

    @Test
    public void iCanBrowseTheRepoByItsId() throws Exception {
        // Given a document
        DocumentModel doc = RestServerInit.getNote(0, session);

        // When i do a GET Request
        ClientResponse response = getResponse(RequestType.GETES, "id/" + doc.getId());

        // Then I get the document as Json will all the properties
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JsonNode node = mapper.readTree(response.getEntityInputStream());
        // System.err.println(node.toString());
        assertEquals("Note 0", node.get("note:note").getTextValue());
    }

    @Test
    public void iCanGetTheChildrenOfADocument() throws Exception {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I query for it children
        ClientResponse response = getResponse(RequestType.GETES, "id/" + folder.getId() + "/@" + ChildrenAdapter.NAME);

        // Then I get elasticsearch bulk output for the two document
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Integer.valueOf(session.getChildren(folder.getRef()).size()).toString(),
                response.getHeaders().getFirst(JsonESDocumentListWriter.HEADER_RESULTS_COUNT));
        // The first node is the an index action it looks like
        // {"index":{"_index":"nuxeo","_type":"doc","_id":"c0941844-7729-431f-9d07-57c6a6580716"}}
        String content = IOUtils.toString(response.getEntityInputStream());
        assertEquals(6714, content.length());
    }

    @Test
    public void iCanSetTheIndexAndTypeOfBulkOutput() throws Exception {
        // Given a folder
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I query for it children
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.putSingle("esIndex", "myIndex");
        queryParams.putSingle("esType", "myType");
        ClientResponse response = getResponse(RequestType.GETES, "id/" + folder.getId() + "/@" + ChildrenAdapter.NAME,
                null, queryParams, null, null);
        // Then I get elasticsearch bulk output for the two document
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // The first node is the an index action it looks like
        // {"index":{"_index":"nuxeo","_type":"doc","_id":"c0941844-7729-431f-9d07-57c6a6580716"}}
        String content = IOUtils.toString(response.getEntityInputStream());
        assertEquals(6739, content.length());
    }

    @Test
    public void iCanGetESJsonFromAPageProvider() throws Exception {
        // Given a note with "nuxeo" in its description
        DocumentModel folder = RestServerInit.getFolder(1, session);

        // When I search for "nuxeo" with appropriate header
        ClientResponse response = getResponse(RequestType.GETES, "path" + folder.getPathAsString() + "/@"
                + PageProviderAdapter.NAME + "/TEST_PP");

        // Then I get elasticsearch bulk output for the two document
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("2", response.getHeaders().getFirst(JsonESDocumentListWriter.HEADER_RESULTS_COUNT));
        // The first node is the an index action it looks like
        // {"index":{"_index":"nuxeo","_type":"doc","_id":"c0941844-7729-431f-9d07-57c6a6580716"}}
        String content = IOUtils.toString(response.getEntityInputStream());
        assertEquals(2685, content.length());
    }

    @Test
    public void iCanPerformESQLPageProviderOnRepository() throws IOException, InterruptedException {
        // wait for async jobs
        ElasticSearchAdmin esa = Framework.getLocalService(ElasticSearchAdmin.class);
        WorkManager wm = Framework.getLocalService(WorkManager.class);
        Assert.assertTrue(wm.awaitCompletion(20, TimeUnit.SECONDS));
        Assert.assertEquals(0, esa.getPendingWorkerCount());
        Assert.assertEquals(0, esa.getPendingCommandCount());
        esa.refresh();
        Assert.assertTrue(wm.awaitCompletion(20, TimeUnit.SECONDS));
        // Given a repository, when I perform a ESQL pageprovider on it
        ClientResponse response = getResponse(RequestType.GET, QueryObject.PATH + "/aggregates_2");

        // Then I get document listing as result
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        JsonNode node = mapper.readTree(response.getEntityInputStream());
        // Verify results
        assertEquals(15, getLogEntries(node).size());
        // And verify contributed aggregates
        assertEquals("terms", node.get("aggregations").get("coverage").get("type").getTextValue());
    }

    @Test
    public void iCanPerformESQLPageProviderOperationOnRepository() throws Exception {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        String providerName = "default_search";

        params.put("providerName", providerName);
        Map<String, String> namedParameters = new HashMap<>();
        namedParameters.put("defaults:dc_nature_agg", "[\"article\"]");
        Properties namedProperties = new Properties(namedParameters);
        params.put("namedParameters", namedProperties);

        PaginableDocumentModelListImpl result = (PaginableDocumentModelListImpl) automationService.run(ctx,
                DocumentPageProviderOperation.ID, params);

        // test page size
        assertEquals(20, result.getPageSize());
        assertEquals(11, result.size());
    }
}
