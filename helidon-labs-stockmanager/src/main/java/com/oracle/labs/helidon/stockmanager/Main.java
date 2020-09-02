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

package com.oracle.labs.helidon.stockmanager;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.spi.ConfigSource;
import io.helidon.microprofile.server.Server;
import lombok.extern.slf4j.Slf4j;

/**
 * Main method simulating trigger of main method of the server.
 */

@Slf4j
public final class Main {

	/**
	 * Cannot be instantiated.
	 */
	private Main() {
	}

	/**
	 * Application main entry point.
	 * 
	 * @param args command line arguments
	 * @throws IOException if there are problems reading logging properties
	 */
	public static void main(final String[] args) throws IOException {
		// Helidon will automatically locate a logging.propoerties if one exists in the
		// classpath or current working directory and will use that to configure the
		// logging for us, so we don't need to explicitly configure logging

		Server server = Server.builder().config(buildConfig()).build().start();

		log.info("http://localhost:" + server.port());
	}

	/**
	 * 
	 * @return
	 */
	private static Config buildConfig() {

		List<Supplier<? extends ConfigSource>> configSourcesToScan = new ArrayList<>(5);
		configSourcesToScan.add(ConfigSources.file("conf/stockmanager-config.yaml")
				.pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(5))).optional().build());
		configSourcesToScan.add(ConfigSources.file("conf/stockmanager-network.yaml").optional().build());
		// We actually use the env or system properties to get these, but let's leave
		// this here as it'll let people easily switch to using a config file instead of
		// them, it's optional so no harm if it's not there
		configSourcesToScan.add(ConfigSources.file("confsecure/stockmanager-database.yaml").optional().build());
		configSourcesToScan.add(ConfigSources.file("confsecure/stockmanager-security.yaml").build());
		configSourcesToScan.add(ConfigSources.classpath("META-INF/microprofile-config.properties").build());
		return Config.builder().sources(configSourcesToScan).build();
	}
}
