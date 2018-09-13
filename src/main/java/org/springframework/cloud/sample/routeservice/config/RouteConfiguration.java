/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sample.routeservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.sample.routeservice.filter.LoggingGatewayFilterFactory;
import org.springframework.cloud.sample.routeservice.filter.SessionIdKeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import static org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory.X_CF_FORWARDED_URL;

@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class RouteConfiguration {

	public static String currentServiceInstanceID;

	public static Map<String, RedisRateLimiter> myRateLimitMap = new HashMap<String, RedisRateLimiter>();

	private final Logger log = LoggerFactory.getLogger(RouteConfiguration.class);

	@Bean
	public KeyResolver keyResolver() {
		return new SessionIdKeyResolver();
	}

	@Bean
	public Predicate<ServerWebExchange> cloudFoundryPredicate() {
		return new CloudFoundryRouteServiceRoutePredicateFactory().apply(config -> {});
	}

	@Bean
	public GatewayFilter logger() {
		return new LoggingGatewayFilterFactory().apply(config -> {});
	}

	@Bean
	/*public RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(1, 1);
	}
	*/
	public RedisRateLimiter redisRateLimiter() {
		log.info(">>>>>						>>>>>");
		log.info("currentServiceInstanceID={}", currentServiceInstanceID);
		RedisRateLimiter rrl = (RedisRateLimiter) myRateLimitMap.get(currentServiceInstanceID);
		if (rrl == null) {
			rrl = new RedisRateLimiter
					(giveMeRandom().nextInt(2) + 1,
							giveMeRandom().nextInt(2) + 1);
			myRateLimitMap.put
					(currentServiceInstanceID, rrl);
		}
		log.info("rrl = {},{}", rrl.getReplenishRateHeader(), rrl.getBurstCapacityHeader());
		log.info(">>>>>						>>>>>");
		return rrl;

	}

	@Bean
	public Random giveMeRandom() { return new Random(); }

	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
				.route(r -> r
						.path("/instanceId/{instanceId}")
						.and()
						.predicate(cloudFoundryPredicate())
						.filters(f -> { f
							.filter(logger())
							.requestRateLimiter(config -> config
									.setRateLimiter(redisRateLimiter())
									.setKeyResolver(keyResolver())
							)
							.requestHeaderToRequestUri(X_CF_FORWARDED_URL);
							return f;
						})
						.uri("https://cloud.spring.io"))
				.build();
	}
}
