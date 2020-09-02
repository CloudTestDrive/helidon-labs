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
package com.oracle.labs.helidon.stockmanager.database;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * These annotations are processed by Lombok, you cn of course create your own
 * getters. setters, all and no args constructors, but Lombok makes it easier.
 * You do however need to include Lombok in your pom.xml AND ALSO install it in
 * the Eclipse development environment (it runs in the background processing
 * annotations as part of the Eclipse continuous background compilation process)
 * 
 * To Run it in the background just run the Lombok jar file which will try and
 * locate the appropriate IDE's for you
 */
// Creates setters, getters, equals, hashcode and toString
@Data
// setup the constructors for us
@NoArgsConstructor
@AllArgsConstructor
// Tell JPA This is something that we want to save, hibernate will do a bunch of stuff for 
// us on this basis, including creating the required tables (assuming we've setup it's 
// properties file correctly
@Entity
@Table(name = "StockLevel")
public class StockLevel {
	@EmbeddedId
	private StockId stockId;

	@Column(name = "itemCount")
	private int itemCount;
}
