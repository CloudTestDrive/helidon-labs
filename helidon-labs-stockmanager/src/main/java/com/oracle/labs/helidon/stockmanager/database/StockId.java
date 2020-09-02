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

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockId implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4014326887605314630L;
	// Yes, I know that this is not the right way to do departments, and it should
	// have a separate table using department Id and a foreign key constraint
	// but this means we can have a simpler database and a simpler demo, and
	// the focus here is on Helidon not JPA :-)
	@Column(name = "departmentName")
	private String departmentName;
	@Column(name = "itemName")
	private String itemName;
}
