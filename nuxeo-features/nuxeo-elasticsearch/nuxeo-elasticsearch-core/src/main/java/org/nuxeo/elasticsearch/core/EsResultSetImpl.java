package org.nuxeo.elasticsearch.core;/*
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.bcel.generic.NEW;
import org.apache.lucene.misc.HighFreqTerms;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.h2.util.New;
import org.nuxeo.common.collections.ArrayMap;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.elasticsearch.io.DocumentModelReaders;

/**
 * @since 7.1
 */
public class EsResultSetImpl implements IterableQueryResult {

    private final SearchResponse response;

    private final String[] selectFields;

    private long pos = 0;

    public EsResultSetImpl(SearchResponse response, String[] selectFields) {
        this.response = response;
        this.selectFields = selectFields;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isLife() {
        return false;
    }

    @Override
    public long size() {
        return response.getHits().getTotalHits();
    }

    @Override
    public long pos() {
        return pos;
    }

    @Override
    public void skipTo(long l) {
        pos = l;
    }

    @Override
    public Iterator<Map<String, Serializable>> iterator() {
        List<Map<String, Serializable>> rows = new ArrayList<>(response.getHits().getHits().length);
        Map<String, Serializable> defRow = new HashMap<>(selectFields.length);
        for (String fieldName : selectFields) {
            defRow.put(fieldName, null);
        }
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Serializable> row = new HashMap<>(defRow);
            for (SearchHitField field : hit.getFields().values()) {
                row.put(field.getName(), field.<Serializable> getValue());
            }
            rows.add(row);
        }
        return rows.iterator();
    }

}
