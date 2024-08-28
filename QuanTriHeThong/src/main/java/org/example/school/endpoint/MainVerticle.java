package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

public class MainVerticle extends AbstractVerticle {

    // Database connection options
    private MSSQLPool client;

    @Override
    public void start() {
        // Set up database connection
        MSSQLConnectOptions connectOptions = new MSSQLConnectOptions()
                .setPort(1433)
                .setHost("localhost")
                .setDatabase("testapp")
                .setUser("sa")
                .setPassword("sa");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        System.out.println("Before Attempting to create the MSSQL pool...");
        // Check if client is null or failed to initialize
        if (connectOptions == null) {
            System.out.println("connect null");
        }
        if (poolOptions == null) {
            System.out.println("pool null");
        }
        client = MSSQLPool.pool(vertx, connectOptions, poolOptions);
        System.out.println("Attempting to create the MSSQL pool..."+ client);

        client.getConnection().onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Successfully got a connection");
                ar.result().close(); // close connection after testing
            } else {
                System.err.println("Failed to get a connection: " + ar.cause().getMessage());
            }
        });



        // Initialize Router
        Router router = Router.router(vertx);

        // Define API endpoints
        router.get("/students").handler(this::getAllStudents);
        router.get("/students/:id").handler(this::getStudentById);

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

    // Handler to get all students
    private void getAllStudents(RoutingContext context) {
        client.getConnection().compose(conn ->
                conn.query("SELECT * FROM Student").execute()
                        .compose(result -> {
                            conn.close();
                            // Chuyển đổi RowSet thành JsonArray
                            JsonArray students = new JsonArray();
                            for (Row row : result) {
                                JsonObject studentJson = new JsonObject();
                                // Giả sử bảng Student có các cột id, name và age
                                studentJson.put("id", row.getString("id"));
                                studentJson.put("name", row.getString("name"));
                                studentJson.put("phone", row.getString("phone"));
                                students.add(studentJson);
                            }
                            return io.vertx.core.Future.succeededFuture(students);
                        })
        ).onComplete(ar -> {
            if (ar.succeeded()) {
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(((JsonArray) ar.result()).encode());
            } else {
                System.err.println("Error retrieving students: " + ar.cause().getMessage());
                context.response().setStatusCode(500).end("Failed to retrieve students");
            }
        });
    }

    // Handler to get student by ID
    private void getStudentById(io.vertx.ext.web.RoutingContext context) {
        String id = context.request().getParam("id");
        client.getConnection().compose(conn ->
                conn.query("SELECT * FROM Student WHERE id ='47.01.104.161'").execute()
                        .compose(result -> {
                            conn.close();
                            // Chuyển đổi RowSet thành JsonArray
                            JsonArray students = new JsonArray();
                            for (Row row : result) {
                                JsonObject studentJson = new JsonObject();
                                // Giả sử bảng Student có các cột id, name và age
                                studentJson.put("id", row.getString("id"));
                                studentJson.put("name", row.getString("name"));
                                studentJson.put("phone", row.getString("phone"));
                                students.add(studentJson);
                            }
                            return io.vertx.core.Future.succeededFuture(students);
                        })
        ).onComplete(ar -> {
            if (ar.succeeded()) {
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().toString());
            } else {
                context.response().setStatusCode(500).end("Failed to retrieve student");
            }
        });
    }
}
