package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.sqlclient.SqlAuthenticationOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import io.vertx.ext.auth.sqlclient.SqlAuthentication;
public class Sy0003S03AH extends AbstractVerticle {

    // Database connection options
    private MSSQLPool client;

    @Override
    public void start() {
        // Set up database connection
        MSSQLConnectOptions connectOptions = new MSSQLConnectOptions()
                .setPort(1433)
                .setHost("localhost")
                .setDatabase("ODH_Sys")
                .setUser("sa")
                .setPassword("123");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Initialize MSSQLPool
        client = MSSQLPool.pool(vertx, connectOptions, poolOptions);

        // Initialize Router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Define API endpoints
        router.get("/getAllScreens").handler(this::getAllScreens);
//        router.get("/permissions/:id").handler(this::getPermissionById);

        // Start HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, result -> {
                    if (result.succeeded()) {
                        System.out.println("Server started on port 8080");
                    } else {
                        result.cause().printStackTrace();
                    }
                });
    }

    private void getAllScreens(RoutingContext context) {
        client.getConnection().compose(conn ->
                conn.query("SELECT GroupScreenCode AS ScreenCode, GroupScreenNameVI AS ScreenName, 0 AS DisplayOrder FROM E00T00020 WITH(NOLOCK) UNION ALL SELECT ScreenCode, ScreenNameVI AS ScreenName, 1 AS DisplayOrder FROM E00T00021 WITH(NOLOCK) ORDER BY ScreenCode, DisplayOrder DESC").execute()
                        .onComplete(ar -> {
                            conn.close();
                            if (ar.succeeded()) {
                                JsonArray responseArray = new JsonArray();
                                Map<String, JsonObject> parentMap = new HashMap<>();

                                // Process each row in the result set
                                for (Row row : ar.result()) {
                                    String screenCode = row.getString("ScreenCode");
                                    String screenName = row.getString("ScreenName");
                                    int displayOrder = row.getInteger("DisplayOrder");
                                    if (displayOrder == 0) {
                                        // Parent screen
                                        JsonObject parent = new JsonObject()
                                                .put("ScreenCode", screenCode)
                                                .put("ScreenName", screenName)
                                                .put("DisplayOrder", displayOrder)
                                                .put("Children", new JsonArray());
                                        parentMap.put(screenCode, parent);
                                        responseArray.add(parent);
                                    } else {
                                        // Child screen
                                        String parentCode = screenCode.substring(0, 3);
                                        JsonObject parent = parentMap.get(parentCode);
                                        if (parent != null) {
                                            JsonObject child = new JsonObject()
                                                    .put("ScreenCode", screenCode)
                                                    .put("ScreenName", screenName)
                                                    .put("DisplayOrder", displayOrder);
                                            parent.getJsonArray("Children").add(child);
                                        }
                                    }
                                }

                                context.response()
                                        .putHeader("content-type", "application/json")
                                        .end(responseArray.encode());
                            } else {
                                System.err.println("Error retrieving screens: " + ar.cause().getMessage());
                                context.response().setStatusCode(500).end("Failed to retrieve all screens");
                            }
                        })
        );
    }

}