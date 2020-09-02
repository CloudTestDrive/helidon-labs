/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oracle.labs.helidon.stockmanager.resources.fallback;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.enterprise.context.Dependent;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

import com.oracle.labs.helidon.common.data.ItemDetails;
import com.oracle.labs.helidon.common.exceptions.commonapi.UnknownItemException;
import com.oracle.labs.helidon.common.exceptions.stockmanagerapi.ItemAlreadyExistsException;
import com.oracle.labs.helidon.stockmanager.resources.fallback.ExceptionMap.handleType;

import lombok.extern.slf4j.Slf4j;

@Dependent
@Slf4j
public class StockManagerItemDetailsFallbackHandler implements FallbackHandler<ItemDetails> {
	// List the specific exceptions here, if there isn't a match it will default to
	// 500 INTERNAL_SERVER_ERROR
	private final static ExceptionMap defaultExceptionMap = new ExceptionMap("Unhandled exception",
			Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Exception is null", ExceptionMap.handleType.FULL_DETAILS);
	private final static List<ExceptionMap> exceptionToCode = Arrays.asList(
			new ExceptionMap(UnknownHostException.class.getName(), 424),
			new ExceptionMap(ConnectException.class.getName(), 424),
			new ExceptionMap(ItemAlreadyExistsException.class.getName(), Status.CONFLICT.getStatusCode(), "",
					ExceptionMap.handleType.EXCEPTION_MESSAGE_ONLY),
			new ExceptionMap(UnknownItemException.class.getName(), Status.NOT_FOUND.getStatusCode(), "",
					ExceptionMap.handleType.EXCEPTION_MESSAGE_ONLY),
			new ExceptionMap("NullCause", Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Exception info is null",
					ExceptionMap.handleType.FULL_DETAILS),
			new ExceptionMap(java.lang.NullPointerException.class.getName(),
					Status.INTERNAL_SERVER_ERROR.getStatusCode()),
			defaultExceptionMap);
	private final static Map<String, ExceptionMap> exceptionsToCode = exceptionToCode.stream()
			.collect(Collectors.toMap(info -> info.getCause(), info -> info));

	/*
	 * This really attempts to show a whole bunch of possibilities for handling a
	 * problem making a call. In reality it's unlikely you'd want to do all of
	 * these, but this may give you some ideas
	 */
	@Override
	public ItemDetails handle(ExecutionContext context) {
		// for now we just extract the details out, but in reality we'd look at the
		// exception to see what's happened and do different processing based on that
		JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
		JsonObjectBuilder partial = JSON.createObjectBuilder();
		// the top level cause is probabaly a JAX-RX problem of some kind, it will
		// contain the embedded cause
		Throwable cause = context.getFailure();
		String causeName = "NULL";
		Throwable embeddedCause;
		if (cause != null) {
			// in some situations the cause will be some form or wrapper, for example a
			// JAX-RS persistence exception, that's pretty boring, so see if it contains an
			// embedded cause, if not just use the one we were given
			embeddedCause = cause.getCause();
			if (embeddedCause == null) {
				embeddedCause = cause;
			}
			if (embeddedCause != null) {
				causeName = embeddedCause.getClass().getName();
				log.info("Cause name is " + causeName);
			}
		} else {
			causeName = "NullCause";
			embeddedCause = null;
		}

		// get the response code to map the exception to from the map
		ExceptionMap info = exceptionsToCode.get(causeName);
		if (info == null) {
			info = defaultExceptionMap;
		}
		if ((info.getProcessingMode() == handleType.FULL_DETAILS)
				|| (info.getProcessingMode() == handleType.EXCEPTION_MESSAGE_ONLY)
				|| (info.getProcessingMode() == handleType.MESSAGE_AND_EXCEPTION_MESSAGE)) {
			partial.add("Exception", causeName);
			if (embeddedCause.getMessage() == null) {
				partial.add("Exception", "Message is null");
			} else {
				partial.add("Exception message", embeddedCause.getMessage());
			}
		}
		if ((info.getProcessingMode() == handleType.FULL_DETAILS)
				|| (info.getProcessingMode() == handleType.MESSAGE_ONLY)
				|| (info.getProcessingMode() == handleType.MESSAGE_AND_EXCEPTION_MESSAGE)) {

			partial.add("message", info.getMessage());
		}
		if (info.getProcessingMode() == handleType.FULL_DETAILS) {
			// they want all of the other info
			// get the expected param types for the method
			String paramTypes = Arrays.stream(context.getMethod().getParameters())
					.map(param -> param.getType().getName()).collect(Collectors.joining(","));
			// build the basic info to let us know what class / method and it's param types
			// were called
			partial.add("Problem processing request in ", context.getMethod().getDeclaringClass().getName() + "."
					+ context.getMethod().getName() + "(" + paramTypes + ")");
			if (embeddedCause != null) {
				StackTraceElement stackHeader = embeddedCause.getStackTrace()[0];
				partial.add("Source class", stackHeader.getClassName());
				partial.add("Source file", stackHeader.getFileName());
				partial.add("Line number", stackHeader.getLineNumber());
			}
			// get the args as a array of strings
			List<String> paramsList = Arrays.stream(context.getParameters()).map(obj -> obj.toString())
					.collect(Collectors.toList());
			JsonArray params = JSON.createArrayBuilder(paramsList).build();
			partial.add("param values", params);

		}
		Response resp = Response.status(info.getStatus()).entity(partial.build()).build();
		// package it all up and throw it, the runtime will convert it into the proper
		// response structure with the redault we provided
		throw new WebApplicationException("Problem processing request", cause, resp);

	}

}
