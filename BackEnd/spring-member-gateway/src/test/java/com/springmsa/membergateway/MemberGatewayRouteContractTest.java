package com.springmsa.membergateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "GATEWAY_CORS_ALLOWED_ORIGIN=https://app.example.com",
                "GATEWAY_BFF_URI=http://member-bff.example:8079",
                "GATEWAY_BFF_WEBSOCKET_URI=ws://member-bff.example:8079",
                "GATEWAY_USER_SERVICE_URI=http://user-service.example:8081",
                "GATEWAY_COMMUNITY_SERVICE_URI=http://community-service.example:8083",
                "GATEWAY_STOCK_SERVICE_URI=http://stock-service.example:8084",
                "GATEWAY_AUTHORIZATION_SERVER_URI=http://authorization-server.example:9000"
        })
@ActiveProfiles("prod")
class MemberGatewayRouteContractTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void chatWebSocketRouteUsesWebSocketSchemeAndPrecedesGeneralBffRoute() {
        var routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block()
                .stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        RouteDefinition websocketRoute = routes.get("member-bff-websocket");
        RouteDefinition generalBffRoute = routes.get("member-bff-service");

        assertThat(websocketRoute).isNotNull();
        assertThat(websocketRoute.getUri()).isEqualTo(URI.create("ws://member-bff.example:8079"));
        assertThat(websocketRoute.getOrder()).isLessThan(generalBffRoute.getOrder());
        assertThat(websocketRoute.getPredicates())
                .anySatisfy(predicate -> {
                    assertThat(predicate.getName()).isEqualTo("Path");
                    assertThat(predicate.getArgs()).containsValue("/bff/chat/ws");
                });
        assertThat(websocketRoute.getFilters())
                .anySatisfy(filter -> {
                    assertThat(filter.getName()).isEqualTo("StripPrefix");
                    assertThat(filter.getArgs()).containsValue("1");
                });
        assertThat(generalBffRoute.getUri()).isEqualTo(URI.create("http://member-bff.example:8079"));
    }
}
