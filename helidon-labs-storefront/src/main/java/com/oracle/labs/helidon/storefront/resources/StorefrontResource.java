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

package com.oracle.labs.helidon.storefront.resources;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.oracle.labs.helidon.common.data.ItemDetails;
import com.oracle.labs.helidon.common.exceptions.commonapi.UnknownItemException;
import com.oracle.labs.helidon.storefront.data.ItemRequest;
import com.oracle.labs.helidon.storefront.data.MinimumChange;
import com.oracle.labs.helidon.storefront.dummy.StockManagerDummy;
import com.oracle.labs.helidon.storefront.exceptions.MinimumChangeException;
import com.oracle.labs.helidon.storefront.exceptions.NotEnoughItemsException;
import com.oracle.labs.helidon.storefront.resources.fallback.StorefrontFallbackHandler;
import com.oracle.labs.helidon.storefront.restclients.StockManager;

import io.helidon.security.annotations.Authenticated;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;


// mark all REST calls in this resource as generating call counts. This will count the number of invocations, 
// if you want to track the number of currently active calls (E.g. entered, but not left a method) then you'd 
// use a @ConcurrentGauge
// Annotating the entire class will generate counters for each individual method, and also class level counters 
// on the totals for the class
// Authenticated here means for any REST call to this class we have to have a user, the user authentication is
// automatically propagated to the stock management service when we call it
// if we don't return from the method in 15 seconds return a timeout message
//Have Lombok create a logger and no args constructor for us
@Slf4j
@NoArgsConstructor
public class StorefrontResource {

	private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

	private MinimumChange minimumChange = new MinimumChange();

	private StockManager stockManager = StockManagerDummy.getStockManager();

	
	public Collection<ItemDetails> listAllStock() {
		// log the request
		log.info("Requesting listing of all stock");
		// get the list from the stock management service
		try {
			Collection<ItemDetails> items = stockManager.getAllStockLevels();
			// log the response
			log.info("Found " + items.size() + " items");
			// return the items
			return items;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * adjust the available levels for the item for example curl -X POST -u
	 * user:password -d '{"requestedItem":"wrench", "requestedCount":5}'
	 * 
	 * @param itemRequest the wonders of JAX and JSON Serialization mean that the
	 *                    framework will process the JSON Payload into the
	 *                    ItemRequest object for us
	 * @return
	 */

	public ItemDetails reserveStockItem(ItemRequest itemRequest)
			throws MinimumChangeException, UnknownItemException, NotEnoughItemsException {
		log.info("Requesting the reservation of " + itemRequest.getRequestedCount() + " items of "
				+ itemRequest.getRequestedItem());
		// make sure the change is within the minimum change allowed
		// :-)
		if (itemRequest.getRequestedCount() < minimumChange.getMinimumChange()) {
			// didn't meet the minimum requirement, log the failed request and throw the log
			// message as an error
			String problemDetails = "The reservation of " + itemRequest.getRequestedCount() + " items of "
					+ itemRequest.getRequestedItem() + " fails because it's less than the minimum delta of "
					+ minimumChange.getMinimumChange();
			log.error(problemDetails);
			throw new MinimumChangeException(problemDetails);
		}
		// OK validated the basic data, let's make sure we have enough remaining stock
		// to reserve
		ItemDetails itemDetails = stockManager.getStockItem(itemRequest.getRequestedItem());
		if (itemDetails == null) {
			// can't find the stock item, log the failed request and throw the log message
			// as
			// an error
			String problemDetails = "The reservation of " + itemRequest.getRequestedCount() + " items of "
					+ itemRequest.getRequestedItem() + " fails because the item is not known";
			log.error(problemDetails);
			throw new UnknownItemException(problemDetails);
		}
		log.info("stock item " + itemDetails.getItemName() + " exists and currently there are "
				+ itemDetails.getItemCount() + " items in stock");
		// do we have enough items to reserve ?
		if (itemDetails.getItemCount() <= itemRequest.getRequestedCount()) {
			// not enough items, log the failed request and throw the log message as an
			// error
			String problemDetails = "The reservation of " + itemRequest.getRequestedCount() + " items of "
					+ itemRequest.getRequestedItem() + " fails because there are only " + itemDetails.getItemCount()
					+ " items available";
			log.error(problemDetails);
			throw new NotEnoughItemsException(problemDetails);
		}
		// Right, passed all checks
		// work out the new level
		int newItemCount = itemDetails.getItemCount() - itemRequest.getRequestedCount();
		// log the request
		log.info("The reservation of " + itemRequest.getRequestedCount() + " items of " + itemRequest.getRequestedItem()
				+ " is being sent to the database");
		// update the DB and get the result back (the updated info)
		ItemDetails updatedItemDetails = stockManager.setStockItemLevel(itemRequest.getRequestedItem(), newItemCount);
		// log the result
		log.info("The reservation of " + itemRequest.getRequestedCount() + " items of " + itemRequest.getRequestedItem()
				+ " suceeded, the stock manager reports " + updatedItemDetails.getItemCount() + " remain");
		// pass back the resulting updated item details
		return updatedItemDetails;
	}

	/*
	 * This is a simple handler, it doesn't get handed the fault details, but we can
	 * use it to return a default object, or in this case throw an error
	 */
	public Collection<ItemDetails> failedListStockItem() {
		log.info("The listing of items failed for some reason");
		throw new WebApplicationException(
				Response.status(424, "Failed Dependency")
						.entity(JSON.createObjectBuilder()
								.add("errormessage", "Unable to connect to the stock manager service").build())
						.build());
	}
}