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
package com.oracle.labs.helidon.stockmanager.health;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import com.oracle.labs.helidon.stockmanager.database.StockId;
import com.oracle.labs.helidon.stockmanager.database.StockLevel;
import com.oracle.labs.helidon.stockmanager.providers.DepartmentProvider;

/**
 * Readiness is different form Liveness. Readiness ensures that we actually are
 * actually ready to process transactions and are fully configured, liveliness
 * is more of a Hello World situation
 * 
 * @author tg13456
 *
 */
@ApplicationScoped
@Readiness
public class ReadinessChecker implements HealthCheck {
	@PersistenceContext(unitName = "HelidonATPJTA")
	private EntityManager entityManager;
	private DepartmentProvider departmentProvider;
	private String persistenceUnit;

	@Inject
	public ReadinessChecker(@ConfigProperty(name = "app.persistenceUnit") String persistenceUnitProvided,
			DepartmentProvider departmentProviderProvided) {
		this.persistenceUnit = persistenceUnitProvided;
		this.departmentProvider = departmentProviderProvided;
	}

	@Override
	public HealthCheckResponse call() {
		// there is no easy way for the entityManager to tell us if there is actually a
		// DB connection apart from trying to do something
		try {
			// if it returns without an exception it mean's we're good
			entityManager.find(StockLevel.class, new StockId("Bad Department", "Bad Item"));
		} catch (Exception e) {
			return HealthCheckResponse.named("stockmanager-ready").state(false)
					.withData("department", departmentProvider.getDepartment())
					.withData("persistanceUnit", persistenceUnit).withData("Exception", e.getClass().getName())
					.withData("Exception message", e.getMessage()).build();
		}
		return HealthCheckResponse.named("stockmanager-ready").state(true)
				.withData("department", departmentProvider.getDepartment()).withData("persistanceUnit", persistenceUnit)
				.build();
	}

}
