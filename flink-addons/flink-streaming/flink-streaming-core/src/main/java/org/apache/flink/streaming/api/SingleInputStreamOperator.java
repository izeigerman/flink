/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.flink.streaming.api;

/**
 * The SingleInputStreamOperator represents a {@link DataStream} transformed
 * with some user defined operator.
 *
 * @param <IN>
 *            Input type of the operator.
 * @param <OUT>
 *            Output Type of the operator.
 */
public class SingleInputStreamOperator<IN, OUT> extends StreamOperator<OUT> {

	protected SingleInputStreamOperator(StreamExecutionEnvironment environment, String operatorType) {
		super(environment, operatorType);
	}

	protected SingleInputStreamOperator(DataStream<OUT> dataStream) {
		super(dataStream);
	}

	@Override
	protected DataStream<OUT> copy() {
		return new SingleInputStreamOperator<IN, OUT>(this);
	}

}
