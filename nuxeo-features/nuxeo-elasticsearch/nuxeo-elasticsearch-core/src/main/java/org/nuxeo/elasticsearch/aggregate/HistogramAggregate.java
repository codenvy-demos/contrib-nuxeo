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
package org.nuxeo.elasticsearch.aggregate;

import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_EXTENDED_BOUND_MAX_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_EXTENDED_BOUND_MIN_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_INTERVAL_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MIN_DOC_COUNT_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_ORDER_COUNT_ASC;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_ORDER_COUNT_DESC;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_ORDER_KEY_ASC;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_ORDER_KEY_DESC;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_ORDER_PROP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramBuilder;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.query.api.AggregateDefinition;
import org.nuxeo.ecm.platform.query.core.BucketRange;

/**
 * @since 6.0
 */
public class HistogramAggregate extends AggregateEsBase<BucketRange> {

    private Integer interval;

    public HistogramAggregate(AggregateDefinition definition, DocumentModel searchDocument) {
        super(definition, searchDocument);
    }

    @JsonIgnore
    @Override
    public HistogramBuilder getEsAggregate() {
        HistogramBuilder ret = AggregationBuilders.histogram(getId()).field(getField());
        Map<String, String> props = getProperties();
        ret.interval(getInterval());
        if (props.containsKey(AGG_MIN_DOC_COUNT_PROP)) {
            ret.minDocCount(Long.parseLong(props.get(AGG_MIN_DOC_COUNT_PROP)));
        }
        if (props.containsKey(AGG_ORDER_PROP)) {
            switch (props.get(AGG_ORDER_PROP).toLowerCase()) {
            case AGG_ORDER_COUNT_DESC:
                ret.order(Histogram.Order.COUNT_DESC);
                break;
            case AGG_ORDER_COUNT_ASC:
                ret.order(Histogram.Order.COUNT_ASC);
                break;
            case AGG_ORDER_KEY_DESC:
                ret.order(Histogram.Order.KEY_DESC);
                break;
            case AGG_ORDER_KEY_ASC:
                ret.order(Histogram.Order.KEY_ASC);
                break;
            default:
                throw new IllegalArgumentException("Invalid order: " + props.get(AGG_ORDER_PROP));
            }
        }
        if (props.containsKey(AGG_EXTENDED_BOUND_MAX_PROP) && props.containsKey(AGG_EXTENDED_BOUND_MIN_PROP)) {
            ret.extendedBounds(Long.parseLong(props.get(AGG_EXTENDED_BOUND_MIN_PROP)),
                    Long.parseLong(props.get(AGG_EXTENDED_BOUND_MAX_PROP)));
        }
        return ret;
    }

    @JsonIgnore
    @Override
    public OrFilterBuilder getEsFilter() {
        if (getSelection().isEmpty()) {
            return null;
        }
        OrFilterBuilder ret = FilterBuilders.orFilter();
        for (String sel : getSelection()) {
            RangeFilterBuilder rangeFilter = FilterBuilders.rangeFilter(getField());
            long from = Long.parseLong(sel);
            long to = from + getInterval();
            rangeFilter.gte(from).lt(to);
            ret.add(rangeFilter);
        }
        return ret;
    }

    @JsonIgnore
    @Override
    public void parseEsBuckets(Collection<? extends MultiBucketsAggregation.Bucket> buckets) {
        List<BucketRange> nxBuckets = new ArrayList<>(buckets.size());
        for (MultiBucketsAggregation.Bucket bucket : buckets) {
            Histogram.Bucket histoBucket = (Histogram.Bucket) bucket;
            nxBuckets.add(new BucketRange(bucket.getKey(), histoBucket.getKeyAsNumber(),
                    histoBucket.getKeyAsNumber().intValue() + getInterval(), histoBucket.getDocCount()));
        }
        this.buckets = nxBuckets;
    }

    public int getInterval() {
        if (interval == null) {
            Map<String, String> props = getProperties();
            if (props.containsKey(AGG_INTERVAL_PROP)) {
                interval = Integer.parseInt(props.get(AGG_INTERVAL_PROP));
            } else {
                throw new IllegalArgumentException("interval property must be defined for " + toString());
            }
        }
        return interval;
    }
}
