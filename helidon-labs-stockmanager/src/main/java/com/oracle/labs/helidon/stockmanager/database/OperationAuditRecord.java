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

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Entity
@Table(name = "OperationAuditRecord", indexes = { @Index(columnList = "departmentName, operationTs", unique = false) })
@NoArgsConstructor
public class OperationAuditRecord {
	@Id
	@GeneratedValue
	private long operationId;
	@Column(name = "operationTs", nullable = false)
	private Timestamp operationTs = new Timestamp(System.currentTimeMillis());
	@Column(name = "succeded", nullable = false)
	private Boolean succeded;
	@Column(name = "errorMessage", nullable = true)
	private String errorMessage;
	@Column(name = "operationType", nullable = false)
	private OperationAuditType operationType;
	@Column(name = "operationUser", nullable = false)
	private String operationUser;
	@Column(name = "departmentName", nullable = false)
	private String departmentName;
	@Column(name = "itemName", nullable = false)
	private String itemName;
	@Column(name = "itemCount", nullable = true)
	private Integer itemCount;

	private OperationAuditRecord(@NonNull OperationAuditType operationType, @NonNull Boolean succeded,
			String errorMessage, @NonNull String operationUser, @NonNull String departmentName,
			@NonNull String itemName, Integer itemCount) {
		super();
		this.operationType = operationType;
		this.succeded = succeded;
		this.errorMessage = errorMessage;
		this.operationUser = operationUser;
		this.departmentName = departmentName;
		this.itemName = itemName;
		this.itemCount = itemCount;
	}

	public static OperationAuditRecord create(@NonNull Boolean succeded, String errorMessage,
			@NonNull String operationUser, @NonNull String departmentName, @NonNull String itemName,
			@NonNull Integer itemCount) {
		return new OperationAuditRecord(OperationAuditType.CREATE, succeded, errorMessage, operationUser,
				departmentName, itemName, itemCount);
	}

	public static OperationAuditRecord update(@NonNull Boolean succeded, String errorMessage,
			@NonNull String operationUser, @NonNull String departmentName, @NonNull String itemName,
			@NonNull Integer itemCount) {
		return new OperationAuditRecord(OperationAuditType.UPDATE, succeded, errorMessage, operationUser,
				departmentName, itemName, itemCount);
	}

	public static OperationAuditRecord delete(@NonNull Boolean succeded, String errorMessage,
			@NonNull String operationUser, @NonNull String departmentName, @NonNull String itemName) {
		return new OperationAuditRecord(OperationAuditType.DELETE, succeded, errorMessage, operationUser,
				departmentName, itemName, null);
	}
}
