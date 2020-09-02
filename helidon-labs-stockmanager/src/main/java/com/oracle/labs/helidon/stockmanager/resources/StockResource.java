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

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

import com.oracle.labs.helidon.common.data.ItemDetails;
import com.oracle.labs.helidon.common.exceptions.commonapi.UnknownItemException;
import com.oracle.labs.helidon.common.exceptions.stockmanagerapi.ItemAlreadyExistsException;
import com.oracle.labs.helidon.stockmanager.database.OperationAuditRecord;
import com.oracle.labs.helidon.stockmanager.database.StockId;
import com.oracle.labs.helidon.stockmanager.database.StockLevel;
import com.oracle.labs.helidon.stockmanager.providers.DepartmentProvider;

import io.helidon.security.annotations.Authenticated;
import lombok.extern.slf4j.Slf4j;

/**
 * A simple JAX-RS resource to report on and update stock levels in a database
 * for you. Examples:
 * 
 * These should really take content from the body, but I want to show how we use
 * Helidon to process path arguments here
 *
 * Create a stock item with the provided stock level, limited to admin users
 * 
 * curl -X PUT --user username:password
 * http://localhost:8080/stocklevel/spanner/20
 *
 * Get all the current stock levels users must be authenticated, but no role
 * required curl -X GET --user username:password
 * http://localhost:8080/stocklevel
 * 
 * Returns a JSON array of the form {"items" : [{"itemName" : "shoes",
 * "itemCount" : 23}, {"itemName" : "socks", "itemCount" : 42}]}
 *
 * Get the stock level for a specific stock (in this case spanner) for demo
 * purposes this does not require authentication
 * 
 * curl -X GET http://localhost:8080/stocklevel/spanner
 * 
 * Returns a JSON object of the form {"itemName" : "stock item name",
 * "itemCount" : 23}
 * 
 * Set a specific stock level (the new level is returned) must be authenticated,
 * but no role required curl -X POST --user username:password
 * http://localhost:8080/stocklevel/spanner/20
 * 
 * Remove a stock item, must be authenticated as an admin user curl -X DELETE
 * --user username:password http://localhost:8080/stocklevel/spanner
 * 
 * Returns a JSON object representing the removed itam of the form {"itemName" :
 * "stock item name", "itemCount" : 23}
 *
 * The message is returned as a JSON object.
 */
@Path("/stocklevel")
@RequestScoped
@Slf4j
public class StockResource {
	private EntityManager entityManager;

	private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
	private static String persistenceUnit;
	private static DepartmentProvider departmentProvider;

	/**
	 * Using constructor injection to get a configuration property. By default this
	 * gets the value from META-INF/microprofile-config
	 *
	 */
	@Inject
	public StockResource(@ConfigProperty(name = "app.persistenceUnit") String persistenceUnitProvided,
			DepartmentProvider departmentProviderProvided) {
		persistenceUnit = persistenceUnitProvided;
		EntityManagerFactory emfactory = Persistence.createEntityManagerFactory(persistenceUnit);
		this.entityManager = emfactory.createEntityManager();
		departmentProvider = departmentProviderProvided;
	}

	@Path("/{itemName}/{itemCount}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	// create a counter for this method
	// specify a name, otherwise it will default to the method name
	// The ConcurrentGage is different form the @Counter and means that this will
	// increment on entry to the method,and decrement on exit from the method, in
	// other words we can see how many calls are actually active in the method at
	// any point in time (useful for method that may run for a long time)
	@ConcurrentGauge(name = "stockCreationCountActive")
	// Only allow access to authenticated users
	@Authenticated
	// Only users with the role admin can create or delete items
	@RolesAllowed({ "admin" })
	// if this fails call the fallback handler to convert the exception to something
	// REST like
	// this class has a handle method that returns a single ItemDetails
	// @Fallback(StockManagerItemDetailsFallbackHandler.class)
	/**
	 * If it doesn't exist create a stock item, automatically add the department
	 * name.
	 * 
	 * @param itemName
	 * @param itemCount
	 * @return HTTP.CONFLICT if another item exists with the name in the department
	 *         HTTP.CREATED if the item record was created, and will include the
	 *         JSON for the created item
	 * 
	 */
	public ItemDetails createStockLevel(@PathParam("itemName") String itemName,
			@PathParam("itemCount") Integer itemCount) throws ItemAlreadyExistsException {
		String user = "Unknown";
		// this may modify the database, so we've used the Transational annotation to
		// have JTA manage that for us
		// Create the primary key
		StockId stockId = new StockId(departmentProvider.getDepartment(), itemName);
		log.info("Creating " + stockId + ", with count " + itemCount);
		StockLevel oldItem = entityManager.find(StockLevel.class, stockId);
		if (oldItem != null) {
			String errorMessage = "Item " + stockId + " already exists, can't create it again";
			log.error(errorMessage);
			// audit it
			writeCreateRecord(false, errorMessage, user, itemName, itemCount);
			// Report that there is a conflict and it exists
			throw new ItemAlreadyExistsException(errorMessage);
		}
		// Create a new item using the PK
		StockLevel item = new StockLevel(stockId, itemCount);
		// upload the new item into the database
		log.info("Creating " + item);
		try {
			entityManager.persist(item);
		} catch (Exception e) {
			String errorMessage = "Problem writing " + item + " due to " + e.getMessage();
			log.error(errorMessage);
			writeCreateRecord(false, errorMessage, user, itemName, itemCount);
			throw (e);
		}
		writeCreateRecord(true, null, user, itemName, itemCount);
		log.info("Created item " + item);
		return createItemDetails(item);
	}

	/**
	 * Return summary of all of the stock levels for this department.
	 *
	 * @return {@link JsonObject}
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	// Create a counter that reports on how often this has been called
	@Counted(name = "stockReporting")
	// Only allow access to authenticated users
	@Authenticated
	// if this fails call the fallback handler to convert the exception to something
	// REST like
	// this class has a handle method that returns a single ItemDetails
	// @Fallback(StockManagerCollectionItemDetailsFallbackHandler.class)
	public Collection<ItemDetails> getAllStockLevels() {
		log.info("Getting all stock items");
		// get the data from the database
		// build the query
		Query findAllQuery = entityManager
				.createNativeQuery("SELECT departmentName, itemName, itemCount FROM StockLevel WHERE departmentName='"
						+ departmentProvider.getDepartment() + "'", StockLevel.class);
		Collection<StockLevel> allStock = findAllQuery.getResultList();
		// the result is of type StockLevel (allowing us to transparently separate on
		// the departmentId, convert it to Items and return them
		log.info("Returning " + allStock.size() + " stock items");
		return allStock.stream()
				// .map(stockLevel -> new ItemDetails(stockLevel.getStockId().getItemName(),
				// stockLevel.getItemCount()))
				.map(stockLevel -> createItemDetails(stockLevel)).collect(Collectors.toList());
	}

	/**
	 * get the level for a specific stock item in the department
	 *
	 * @param name the name to greet
	 * @return the stock item or an exception if it can't be found
	 */
	@Path("/{itemName}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Metered(name = "stockLevelCallRates")
	@Authenticated
	// @Fallback(StockManagerItemDetailsFallbackHandler.class)
	public ItemDetails getStockItem(@PathParam("itemName") String itemName) throws UnknownItemException {
		// Let's see it there is an item there, create the primary key
		StockId stockId = new StockId(departmentProvider.getDepartment(), itemName);
		log.info("Locating stock item " + stockId);
		// search for the PK
		log.info("Entity manager == null" + (entityManager == null));
		StockLevel stockLevel = entityManager.find(StockLevel.class, stockId);
		if (stockLevel == null) {
			String errorMessage = "Item " + stockId + " was not found in the database";
			log.info(errorMessage);
			throw new UnknownItemException(errorMessage);
		}
		// build the JSON for it
		log.info("Found stock item " + stockLevel);
		return createItemDetails(stockLevel);
	}

	@Path("/{itemName}/{itemCount}")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	// this will get the name of the method and generate timing information across
	// method calls
	// this may modify the database, so need to do it in a transaction
	@Timed
	// Only allow access to authenticated users, but no role required
	@Authenticated
	// @Fallback(StockManagerItemDetailsFallbackHandler.class)
	public ItemDetails adjustStockLevel(@PathParam("itemName") String itemName,
			@PathParam("itemCount") Integer itemCount) throws UnknownItemException {
		String user = "Unknown";
		// we're going to modify the DB, but the @Transation annotation means that JTA
		// will automatically start it for us.
		// create the primary key, this is embedded
		StockId stockId = new StockId(departmentProvider.getDepartment(), itemName);
		log.info("Adjusting level of " + stockId + " to " + itemCount);
		// try to find a stock using the PK
		StockLevel origionalItem = entityManager.find(StockLevel.class, stockId);
		if (origionalItem == null) {
			String errorMessage = "Item " + stockId
					+ " was not found in the database, can't update something that doesn't exist";
			log.info(errorMessage);
			writeUpdateRecord(false, errorMessage, user, itemName, itemCount);
			throw new UnknownItemException(errorMessage);
		}
		// update the object with the new stock level
		origionalItem.setItemCount(itemCount);
		// merge the updated item into the database.
		log.info("Updating database with " + origionalItem);
		StockLevel updatedItem = entityManager.merge(origionalItem);
		writeUpdateRecord(true, null, user, itemName, itemCount);
		log.info("Adjusted data is " + updatedItem);
		// return the updated item
		return createItemDetails(updatedItem);
	}

	@Path("/{itemName}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	// counter using the default method name as the counter name
	@Counted
	// Only allow access to authenticated users
	@Authenticated
	// Only users with the role admin can create or delete items
	@RolesAllowed({ "admin" })
	// @Fallback(StockManagerItemDetailsFallbackHandler.class)
	public ItemDetails deleteStockItem(@PathParam("itemName") String itemName) throws UnknownItemException {
		String user = "Unknown";
		// we're going to modify the DB, but the @Transation annotation means that JTA
		// will automatically start it for us.
		// create the primary key, this is embedded
		StockId stockId = new StockId(departmentProvider.getDepartment(), itemName);
		log.info("Deleting item of " + stockId);
		// try to find a stock using the PK
		StockLevel itemToDelete = entityManager.find(StockLevel.class, stockId);
		if (itemToDelete == null) {
			String errorMessage = "Item " + stockId
					+ " was not found in the database, can't delete something that doesn't exist";
			log.info(errorMessage);
			writeDeleteRecord(false, errorMessage, user, itemName);
			throw new UnknownItemException(errorMessage);
		}
		// delete it from the database, and update the audit record.
		try {
			entityManager.remove(itemToDelete);
		} catch (Exception e) {
			String errorMessage = "Problem deleting " + itemToDelete + " due to " + e.getMessage();
			log.error(errorMessage);
			writeDeleteRecord(false, errorMessage, user, itemName);
			throw (e);
		}
		writeDeleteRecord(true, null, user, itemName);
		log.info("Item " + stockId + " has been removed");
		// return the deleted item
		return createItemDetails(itemToDelete);
	}

	// get the 10 most recent audit records for the department
	@Path("/audit")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	// counter using the default method name as the counter name
	@Counted
	// Only allow access to authenticated users
	@Authenticated
	// Only users with the role admin can create or delete items
	@RolesAllowed({ "admin" })
	public Collection<OperationAuditRecord> getAuditRecords() {
		return getAuditRecords(10);
	}

	@Path("/audit/{rowcount}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	// counter using the default method name as the counter name
	@Counted
	// Only allow access to authenticated users
	@Authenticated
	// Only users with the role admin can create or delete items
	@RolesAllowed({ "admin" })
	public Collection<OperationAuditRecord> getAuditRecords(@PathParam("rowcount") int rowcount) {
		log.info("Retrieving " + rowcount + " audit rows");
		// get the most recent rowcount records for the department
		Query findAuditQuery = entityManager.createNativeQuery(
				"SELECT * FROM OperationAuditRecord WHERE departmentName='" + departmentProvider.getDepartment()
						+ "' ORDER BY operationTs DESC",
				OperationAuditRecord.class).setMaxResults(rowcount);
		return findAuditQuery.getResultList();
	}

	private void writeCreateRecord(Boolean succeded, String errorMessage, String operationUser, String itemName,
			Integer itemCount) {
		OperationAuditRecord oar = OperationAuditRecord.create(succeded, errorMessage, operationUser,
				departmentProvider.getDepartment(), itemName, itemCount);
		writeAuditRecord(oar);
	}

	private void writeUpdateRecord(Boolean succeded, String errorMessage, String operationUser, String itemName,
			Integer itemCount) {
		OperationAuditRecord oar = OperationAuditRecord.update(succeded, errorMessage, operationUser,
				departmentProvider.getDepartment(), itemName, itemCount);
		writeAuditRecord(oar);
	}

	private void writeDeleteRecord(Boolean succeded, String errorMessage, String operationUser, String itemName) {
		OperationAuditRecord oar = OperationAuditRecord.delete(succeded, errorMessage, operationUser,
				departmentProvider.getDepartment(), itemName);
		writeAuditRecord(oar);
	}

	private void writeAuditRecord(OperationAuditRecord oar) {
		log.info("Writing audit record " + oar);
		entityManager.persist(oar);
	}

	private ItemDetails createItemDetails(StockLevel stockLevel) {
		return new ItemDetails(stockLevel.getStockId().getItemName(), stockLevel.getItemCount());
	}

	

}
