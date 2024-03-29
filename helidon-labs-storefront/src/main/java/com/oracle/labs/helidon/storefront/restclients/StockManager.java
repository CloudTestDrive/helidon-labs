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
package com.oracle.labs.helidon.storefront.restclients;

import java.util.Collection;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.oracle.labs.helidon.storefront.data.ItemDetails;

// Specify a config key here, this makes it easier in the configuration as we can just use that rather than having 
// to define things using a fully qualified class name
public interface StockManager {
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<ItemDetails> getAllStockLevels();

	@GET
	@Path("/{itemName}")
	@Produces(MediaType.APPLICATION_JSON)
	public ItemDetails getStockItem(@PathParam("itemName") String itemName);

	@POST
	@Path("/{itemName}/{itemCount}")
	@Produces(MediaType.APPLICATION_JSON)
	public ItemDetails setStockItemLevel(@PathParam("itemName") String itemName,
			@PathParam("itemCount") Integer itemCount);
}
