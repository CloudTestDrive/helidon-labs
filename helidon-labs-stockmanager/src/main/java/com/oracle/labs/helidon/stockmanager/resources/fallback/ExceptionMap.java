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

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
class ExceptionMap {
	private final String cause;
	private final Integer status;
	private final String message;
	private final handleType processingMode;

	public ExceptionMap(String cause, Integer status) {
		super();
		this.cause = cause;
		this.status = status;
		this.message = "";
		this.processingMode = handleType.FULL_DETAILS;
	}

	public enum handleType {
		FULL_DETAILS, MESSAGE_ONLY, MESSAGE_AND_EXCEPTION_MESSAGE, EXCEPTION_MESSAGE_ONLY
	}
}
