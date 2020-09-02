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
package com.oracle.labs.helidon.stockmanager.resources;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import lombok.extern.slf4j.Slf4j;

@Path("/status")
@ApplicationScoped
@Slf4j
public class StatusResource {
	public final static String VERSION = "0.0.1";
	public final static int FROZEN_TIME = 60;
	private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
	private static final SimpleDateFormat format = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS");

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	/**
	 * very simple little thing to acknowledge we're alive, is less overhead than
	 * interrogating the health status
	 * 
	 * @return
	 */
	public JsonObject isAlive() throws InterruptedException {
		// if there is a file /frozen then just lock up for 60 seconds
		// this let's us emulate a lockup which will trigger a pod restart
		// if we have enabled liveliness testing against this API
		if (new File("/frozen").exists()) {
			log.info("/frozen exists, locking for " + FROZEN_TIME + " seconds");
			Thread.sleep(FROZEN_TIME * 1000);
			// to be nice return something after a while
			return JSON.createObjectBuilder().add("name", "stockmanager").add("alive", false).add("version", VERSION)
					.add("timestamp", format.format(new Date())).build();
		} else {
			log.info("Not frozen, Returning alive status true");
			return JSON.createObjectBuilder().add("name", "stockmanager").add("alive", true).add("version", VERSION)
					.add("timestamp", format.format(new Date())).build();
		}
	}
}
