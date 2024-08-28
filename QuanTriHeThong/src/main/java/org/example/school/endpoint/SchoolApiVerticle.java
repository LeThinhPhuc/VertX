package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
public class SchoolApiVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(SchoolApiVerticle.class);
    private static final int OK_STATUS = 200;

    @Override
    public void start(Promise<Void> startPromise) {

        Router router = Router.router(vertx);
        router.get("/student").handler(this::getAllStudents);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, ar -> {
                    if (ar.succeeded()) {
                        logger.info("HTTP server listening on port 8080");
                        startPromise.complete();
                    } else {
                        startPromise.fail(ar.cause());
                    }
                });
    }

    private void getAllStudents(RoutingContext ctx) {
        JsonObject response = new JsonObject();
        response.put("status", 1);
        response.put("message", "hello phuc ne");

        HttpServerRequest request = ctx.request();
        String message = request.getParam("message");
        if (message != null) {
            response.put("messageInput", message);
        } else {
            response.put("messageInput", "No message parameter provided");
        }

        ctx.response()
                .setStatusCode(OK_STATUS)
                .putHeader("Content-Type", "application/json")
                .end(response.encodePrettily());

    }
}