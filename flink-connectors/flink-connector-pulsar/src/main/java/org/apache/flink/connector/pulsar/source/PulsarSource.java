/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.connector.pulsar.source;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.pulsar.common.config.ConfigurationDataCustomizer;
import org.apache.flink.connector.pulsar.source.config.SourceConfiguration;
import org.apache.flink.connector.pulsar.source.enumerator.PulsarSourceEnumState;
import org.apache.flink.connector.pulsar.source.enumerator.PulsarSourceEnumStateSerializer;
import org.apache.flink.connector.pulsar.source.enumerator.PulsarSourceEnumerator;
import org.apache.flink.connector.pulsar.source.enumerator.SplitsAssignmentState;
import org.apache.flink.connector.pulsar.source.enumerator.cursor.StartCursor;
import org.apache.flink.connector.pulsar.source.enumerator.cursor.StopCursor;
import org.apache.flink.connector.pulsar.source.enumerator.subscriber.PulsarSubscriber;
import org.apache.flink.connector.pulsar.source.enumerator.topic.range.RangeGenerator;
import org.apache.flink.connector.pulsar.source.reader.PulsarSourceReaderFactory;
import org.apache.flink.connector.pulsar.source.reader.deserializer.PulsarDeserializationSchema;
import org.apache.flink.connector.pulsar.source.reader.deserializer.PulsarDeserializationSchemaInitializationContext;
import org.apache.flink.connector.pulsar.source.split.PulsarPartitionSplit;
import org.apache.flink.connector.pulsar.source.split.PulsarPartitionSplitSerializer;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import org.apache.pulsar.client.impl.conf.ClientConfigurationData;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;

/**
 * The Source implementation of Pulsar. Please use a {@link PulsarSourceBuilder} to construct a
 * {@link PulsarSource}. The following example shows how to create a PulsarSource emitting records
 * of <code>String</code> type.
 *
 * <pre>{@code
 * PulsarSource<String> source = PulsarSource
 *     .builder()
 *     .setTopics(TOPIC1， TOPIC2)
 *     .setServiceUrl(getServiceUrl())
 *     .setAdminUrl(getAdminUrl())
 *     .setSubscriptionName("test")
 *     .setDeserializationSchema(PulsarDeserializationSchema.flinkSchema(new SimpleStringSchema()))
 *     .setBounded(StopCursor::defaultStopCursor)
 *     .build();
 * }</pre>
 *
 * <p>See {@link PulsarSourceBuilder} for more details.
 *
 * @param <OUT> The output type of the source.
 */
@PublicEvolving
public final class PulsarSource<OUT>
        implements Source<OUT, PulsarPartitionSplit, PulsarSourceEnumState>,
                ResultTypeQueryable<OUT> {
    private static final long serialVersionUID = 7773108631275567433L;

    /**
     * The common configuration for pulsar source, we don't support the pulsar's configuration class
     * directly.
     */
    private final Configuration configuration;

    private final SourceConfiguration sourceConfiguration;

    private final PulsarSubscriber subscriber;

    private final RangeGenerator rangeGenerator;

    private final StartCursor startCursor;

    private final StopCursor stopCursor;

    private final Boundedness boundedness;

    /** The pulsar deserialization schema used for deserializing message. */
    private final PulsarDeserializationSchema<OUT> deserializationSchema;

    /** Modify the flink generated {@link ClientConfigurationData}. */
    private final ConfigurationDataCustomizer<ClientConfigurationData>
            clientConfigurationCustomizer;

    /** Modify the flink generated {@link ConsumerConfigurationData}. */
    private final ConfigurationDataCustomizer<ConsumerConfigurationData<byte[]>>
            consumerConfigurationCustomizer;

    /**
     * The constructor for PulsarSource, it's package protected for forcing using {@link
     * PulsarSourceBuilder}.
     */
    PulsarSource(
            Configuration configuration,
            PulsarSubscriber subscriber,
            RangeGenerator rangeGenerator,
            StartCursor startCursor,
            StopCursor stopCursor,
            Boundedness boundedness,
            PulsarDeserializationSchema<OUT> deserializationSchema,
            ConfigurationDataCustomizer<ClientConfigurationData> clientConfigurationCustomizer,
            ConfigurationDataCustomizer<ConsumerConfigurationData<byte[]>>
                    consumerConfigurationCustomizer) {

        this.configuration = configuration;
        this.sourceConfiguration = new SourceConfiguration(configuration);
        this.subscriber = subscriber;
        this.rangeGenerator = rangeGenerator;
        this.startCursor = startCursor;
        this.stopCursor = stopCursor;
        this.boundedness = boundedness;
        this.deserializationSchema = deserializationSchema;
        this.clientConfigurationCustomizer = clientConfigurationCustomizer;
        this.consumerConfigurationCustomizer = consumerConfigurationCustomizer;
    }

    /**
     * Get a PulsarSourceBuilder to builder a {@link PulsarSource}.
     *
     * @return a Pulsar source builder.
     */
    @SuppressWarnings("java:S4977")
    public static <OUT> PulsarSourceBuilder<OUT> builder() {
        return new PulsarSourceBuilder<>();
    }

    @Override
    public Boundedness getBoundedness() {
        return boundedness;
    }

    @Override
    public SourceReader<OUT, PulsarPartitionSplit> createReader(SourceReaderContext readerContext)
            throws Exception {
        // Initialize the deserialization schema before creating the pulsar reader.
        deserializationSchema.open(
                new PulsarDeserializationSchemaInitializationContext(readerContext));

        return PulsarSourceReaderFactory.create(
                readerContext,
                deserializationSchema,
                configuration,
                sourceConfiguration,
                clientConfigurationCustomizer,
                consumerConfigurationCustomizer);
    }

    @Override
    public SplitEnumerator<PulsarPartitionSplit, PulsarSourceEnumState> createEnumerator(
            SplitEnumeratorContext<PulsarPartitionSplit> enumContext) {
        SplitsAssignmentState assignmentState =
                new SplitsAssignmentState(startCursor, stopCursor, sourceConfiguration);
        return new PulsarSourceEnumerator(
                subscriber,
                rangeGenerator,
                configuration,
                sourceConfiguration,
                clientConfigurationCustomizer,
                enumContext,
                assignmentState);
    }

    @Override
    public SplitEnumerator<PulsarPartitionSplit, PulsarSourceEnumState> restoreEnumerator(
            SplitEnumeratorContext<PulsarPartitionSplit> enumContext,
            PulsarSourceEnumState checkpoint) {
        SplitsAssignmentState assignmentState =
                new SplitsAssignmentState(startCursor, stopCursor, sourceConfiguration, checkpoint);
        return new PulsarSourceEnumerator(
                subscriber,
                rangeGenerator,
                configuration,
                sourceConfiguration,
                clientConfigurationCustomizer,
                enumContext,
                assignmentState);
    }

    @Override
    public SimpleVersionedSerializer<PulsarPartitionSplit> getSplitSerializer() {
        return PulsarPartitionSplitSerializer.INSTANCE;
    }

    @Override
    public SimpleVersionedSerializer<PulsarSourceEnumState> getEnumeratorCheckpointSerializer() {
        return PulsarSourceEnumStateSerializer.INSTANCE;
    }

    @Override
    public TypeInformation<OUT> getProducedType() {
        return deserializationSchema.getProducedType();
    }
}
