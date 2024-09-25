package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
public class MainVerticle extends AbstractVerticle {
    private MSSQLPool client;

    @Override
    public void start() {

        // Cấu hình SQLAuthentication để quản lý xác thực người dùng
        // Tạo các tùy chọn kết nối đến cơ sở dữ liệu MSSQL
        MSSQLConnectOptions connectOptions = new MSSQLConnectOptions()
                .setPort(1433) // Cổng kết nối mặc định cho MSSQL
                .setHost("localhost") // Địa chỉ máy chủ cơ sở dữ liệu
                .setDatabase("ODHDB_2") // Tên cơ sở dữ liệu
                .setUser("sa") // Tài khoản người dùng cơ sở dữ liệu
                .setPassword("sa"); // Mật khẩu người dùng cơ sở dữ liệu

        // Cấu hình tùy chọn cho kết nối pool
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5); // Kích thước tối đa của pool kết nối

        System.out.println("Before Attempting to create the MSSQL pool...");

        // Kiểm tra xem các tùy chọn kết nối và pool có null không
        if (connectOptions == null) {
            System.out.println("connect null");
        }
        if (poolOptions == null) {
            System.out.println("pool null");
        }

        // Tạo pool kết nối MSSQL
        client = MSSQLPool.pool(vertx, connectOptions, poolOptions);

        System.out.println("Attempting to create the MSSQL pool..."+ client);

        // Kiểm tra kết nối tới cơ sở dữ liệu
        client.getConnection().onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Successfully got a connection");
                ar.result().close(); // Đóng kết nối sau khi kiểm tra
            } else {
                System.err.println("Failed to get a connection: " + ar.cause().getMessage());
            }
        });

        // Khởi tạo Router để xử lý các yêu cầu HTTP
        Router router = Router.router(vertx);

        // Thêm BodyHandler để xử lý dữ liệu trong body của yêu cầu
        router.route().handler(BodyHandler.create());

        // Định nghĩa các endpoint API và phương thức xử lý
        router.get("/odh/getAllCriteriaGroup").handler(this::getAllCriteriaGroup);
        router.get("/odh/getCriteriaGroup/:id").handler(this::getCriteriaGroup);
        router.post("/odh/addCriteriaGroup").handler(this::addCriteriaGroup);
        router.put("/odh/updateCriteriaGroup").handler(this::updateCriteriaGroup);
        router.delete("/odh/deleteCriteriaGroups").handler(this::deleteCriteriaGroups);

        // Khởi động máy chủ HTTP và lắng nghe trên cổng 8082
        vertx.createHttpServer()
                .requestHandler(router) // Đăng ký router để xử lý các yêu cầu HTTP
                .listen(8082, result -> {
                    if (result.succeeded()) {
                        System.out.println("Server started on port 8082");
                    } else {
                        result.cause().printStackTrace(); // In lỗi nếu không khởi động được máy chủ
                    }
                });

    }

    private void getAllCriteriaGroup(RoutingContext context) {
        String code = context.request().getParam("CriteriaGroupCode");
        String name = context.request().getParam("CriteriaGroupName");
        String check = context.request().getParam("check");

        Tuple tuple = Tuple.tuple()
                .addString(code != null ? "%" + code + "%" : "%")
                .addString(name != null ? "%" + name + "%" : "%");

        // Khởi tạo câu lệnh SQL cơ bản
        String sql = "SELECT * FROM E02T00003 WHERE CriteriaGroupCode LIKE @p1 AND CriteriaGroupName LIKE @p2";

        // Nếu `check` là "true", thêm điều kiện `Active = 1` vào câu lệnh SQL
        if (check != null && check.equals("1")) {
            sql += " AND Active = 1";
        }

        String finalSql = sql;
        client.getConnection().compose(conn -> {
            return conn.preparedQuery(finalSql).execute(tuple)
                    .compose(result -> {
                        // Đóng kết nối sau khi truy vấn xong
                        conn.close();

                        // Chuyển kết quả từ RowSet sang JsonArray
                        JsonArray users = new JsonArray();
                        for (Row row : result) {
                            UUID criteriaGroupID = row.getUUID("CriteriaGroupID");
                            String criteriaGroupIDStr = criteriaGroupID != null ? criteriaGroupID.toString() : "";

                            JsonObject userJson = new JsonObject()
                                    .put("CriteriaGroupID", criteriaGroupIDStr)
                                    .put("CriteriaGroupCode", row.getString("CriteriaGroupCode") != null ? row.getString("CriteriaGroupCode") : "")
                                    .put("CriteriaGroupNameCriteriaGroupName", row.getString("CriteriaGroupName") != null ? row.getString("CriteriaGroupName") : "");

                            users.add(userJson);
                        }
                        return Future.succeededFuture(users);
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().encode());
            } else {
                System.err.println("Error retrieving users: " + ar.cause().getMessage());
                context.response().setStatusCode(500).end("Failed to retrieve users");
            }
        });
    }



    private void getCriteriaGroup(RoutingContext context) {
        UUID id = UUID.fromString(context.request().getParam("id"));
        if (id == null) {
            context.response().setStatusCode(400).end("Missing or empty 'id' parameter");
            return;
        }

        System.out.println("Request ID: " + id); // Log ID nhận được
        Tuple tuple = Tuple.tuple()
                .addUUID(id);
        client.getConnection().compose(conn -> {


                    System.out.println("Got a connection");

                    // Tạo câu lệnh SQL với tham số gán tên
                    String sql = "SELECT CriteriaGroupCode, CriteriaGroupName, ShortNameVI, ShortNameEN, DisplayNameVI, DisplayNameEN, ReportDisplayNameVI, ReportDisplayNameEN, ToolTipVI, ToolTipEN, Active FROM E02T00003 WHERE CriteriaGroupID = @p1";

                    System.out.println("Got a query: " + sql);
                    // Tạo prepared query và truyền tham số vào
                    return conn.preparedQuery(sql)
                            .execute(tuple) // Thay thế tham số với giá trị 'id'
                            .compose(result -> {
                                conn.close(); // Đóng kết nối sau khi hoàn tất truy vấn
                                System.out.println("Query executed successfully");

                                JsonArray users = new JsonArray();
                                for (Row row : result) {
                                    JsonObject userJson = new JsonObject()
                                            .put("CriteriaGroupCode", row.getString("CriteriaGroupCode") != null ? row.getString("CriteriaGroupCode") : "")
                                            .put("CriteriaGroupName", row.getString("CriteriaGroupName") != null ? row.getString("CriteriaGroupName") : "")
                                            .put("ShortNameVI", row.getString("ShortNameVI") != null ? row.getString("ShortNameVI") : "")
                                            .put("ShortNameEN", row.getString("ShortNameEN") != null ? row.getString("ShortNameEN") : "")
                                            .put("DisplayNameVI", row.getString("DisplayNameVI") != null ? row.getString("DisplayNameVI") : "")
                                            .put("DisplayNameEN", row.getString("DisplayNameEN") != null ? row.getString("DisplayNameEN") : "")
                                            .put("ReportDisplayNameVI", row.getString("ReportDisplayNameVI") != null ? row.getString("ReportDisplayNameVI") : "")
                                            .put("ReportDisplayNameEN", row.getString("ReportDisplayNameEN") != null ? row.getString("ReportDisplayNameEN") : "")
                                            .put("ToolTipVI", row.getString("ToolTipVI") != null ? row.getString("ToolTipVI") : "")
                                            .put("ToolTipEN", row.getString("ToolTipEN") != null ? row.getString("ToolTipEN") : "")
                                            .put("Active", row.getBoolean("Active") != null ? row.getBoolean("Active") : false);


                                    users.add(userJson);
                                }
                                return io.vertx.core.Future.succeededFuture(users);
                            });
                }
        ).onComplete(ar -> {
            if (ar.succeeded()) {
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().encode());
            } else {
                System.err.println("Error retrieving users: " + ar.cause().getMessage());
                context.response().setStatusCode(500).end("Failed to retrieve users");
            }
        });
    }

    private void addCriteriaGroup(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String CriteriaGroupID = UUID.randomUUID().toString();
        String CriteriaGroupCode = body.getString("CriteriaGroupCode");
        String CriteriaGroupName = body.getString("CriteriaGroupName");
        String ShortNameVI = body.getString("ShortNameVI");
        String ShortNameEN = body.getString("ShortNameEN");
        String DisplayNameVI = body.getString("DisplayNameVI");
        String DisplayNameEN = body.getString("DisplayNameEN");
        String ReportDisplayNameVI = body.getString("ReportDisplayNameVI");
        String ReportDisplayNameEN = body.getString("ReportDisplayNameEN");
        String ToolTipVI = body.getString("ToolTipVI");
        String ToolTipEN = body.getString("ToolTipEN");
        Boolean Active = body.getBoolean("Active");

        // Tạo Tuple và truyền trực tiếp các giá trị đã lấy từ body
        Tuple tuple = Tuple.tuple()
                .addString(CriteriaGroupID)
                .addString(CriteriaGroupCode)
                .addString(ShortNameVI)
                .addString(ShortNameEN)
                .addString(DisplayNameVI)
                .addString(DisplayNameEN)
                .addString(ReportDisplayNameVI)
                .addString(ReportDisplayNameEN)
                .addString(ToolTipVI)
                .addString(ToolTipEN)
                .addBoolean(Active)
                .addString(CriteriaGroupName);

        client.getConnection().compose(conn -> {
            System.out.println("Got a connection");

            // Câu lệnh SQL với các tham số
            String sql = "INSERT INTO E02T00003 (CriteriaGroupID, CriteriaGroupCode, ShortNameVI, ShortNameEN, DisplayNameVI, DisplayNameEN, ReportDisplayNameVI, ReportDisplayNameEN, ToolTipVI, ToolTipEN, Active, CriteriaGroupName)"
                    + " VALUES (@p1, @p2, @p3, @p4, @p5, @p6, @p7, @p8, @p9, @p10, @p11, @p12)";

            System.out.println("Got a query: " + sql);

            // Thực thi prepared query với tuple chứa các giá trị
            return conn.preparedQuery(sql)
                    .execute(tuple)
                    .compose(result -> {
                        conn.close(); // Đóng kết nối sau khi truy vấn hoàn tất
                        System.out.println("Query executed successfully");
                        return io.vertx.core.Future.succeededFuture("Insert successful");
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("message", "Insert successful").encode());
            } else {
                System.err.println("Error executing query: " + ar.cause().getMessage());
                ctx.response().setStatusCode(500).end("Failed to execute query");
            }
        });
    }

    private void updateCriteriaGroup(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String CriteriaGroupID = body.getString("CriteriaGroupID");
        String CriteriaGroupCode = body.getString("CriteriaGroupCode");
        String CriteriaGroupName = body.getString("CriteriaGroupName");
        String ShortNameVI = body.getString("ShortNameVI");
        String ShortNameEN = body.getString("ShortNameEN");
        String DisplayNameVI = body.getString("DisplayNameVI");
        String DisplayNameEN = body.getString("DisplayNameEN");
        String ReportDisplayNameVI = body.getString("ReportDisplayNameVI");
        String ReportDisplayNameEN = body.getString("ReportDisplayNameEN");
        String ToolTipVI = body.getString("ToolTipVI");
        String ToolTipEN = body.getString("ToolTipEN");
        Boolean Active = body.getBoolean("Active");

        // Tạo Tuple và truyền trực tiếp các giá trị đã lấy từ body
        Tuple tuple = Tuple.tuple()
                .addString(CriteriaGroupID)
                .addString(CriteriaGroupCode)
                .addString(CriteriaGroupName)
                .addString(ShortNameVI)
                .addString(ShortNameEN)
                .addString(DisplayNameVI)
                .addString(DisplayNameEN)
                .addString(ReportDisplayNameVI)
                .addString(ReportDisplayNameEN)
                .addString(ToolTipVI)
                .addString(ToolTipEN)
                .addBoolean(Active);

        client.getConnection().compose(conn -> {
            System.out.println("Got a connection");

            // Câu lệnh SQL với các tham số
            String sql = "UPDATE E02T00003 SET CriteriaGroupCode = @p2, CriteriaGroupName = @p3, ShortNameVI=@p4, ShortNameEN=@p5, DisplayNameVI=@p6, DisplayNameEN=@p7, ReportDisplayNameVI=@p8, ReportDisplayNameEN=@p9,ToolTipVI=@p10, ToolTipEN=@p11, Active =@p12 WHERE CriteriaGroupID = @p1";

            System.out.println("Got a query: " + sql);

            // Thực thi prepared query với tuple chứa các giá trị
            return conn.preparedQuery(sql)
                    .execute(tuple)
                    .compose(result -> {
                        conn.close(); // Đóng kết nối sau khi truy vấn hoàn tất
                        System.out.println("Query executed successfully");
                        return io.vertx.core.Future.succeededFuture("Update successful");
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("message", "Update successful").encode());
            } else {
                System.err.println("Error executing query: " + ar.cause().getMessage());
                ctx.response().setStatusCode(500).end("Failed to execute query");
            }
        });
    }

    private void deleteCriteriaGroups(RoutingContext context) {
        JsonObject body = context.getBodyAsJson();
        JsonArray arr = body.getJsonArray("criteriaGroupArray");
        if (arr == null || arr.size()==0) {
            context.response().setStatusCode(400).end("User ID is required");
            return;
        }

        try {

            for(int i =0 ; i< arr.size();i++){
                String id = arr.getString(i);
                client.getConnection().compose(conn -> {
                    String sql = "DELETE FROM E02T00003 WHERE CriteriaGroupID = @p1";
                    return conn.preparedQuery(sql)
                            .execute(Tuple.of(id))
                            .onComplete(ar -> {
                                conn.close(); // Close connection after operation
                                if (ar.succeeded()) {
                                    if (ar.result().rowCount() > 0) {
                                        context.response()
                                                .putHeader("content-type", "application/json")
                                                .end(new JsonObject().put("status", "success").put("message", "User deleted successfully").encode());
                                    } else {
                                        context.response()
                                                .setStatusCode(404)
                                                .end(new JsonObject().put("status", "fail").put("message", "User not found").encode());
                                    }
                                } else {
                                    System.err.println("Failed to delete user: " + ar.cause().getMessage());
                                    context.response().setStatusCode(500).end("Failed to delete user: " + ar.cause().getMessage());
                                }
                            });
                }).onFailure(err -> {
                    context.response().setStatusCode(500).end("Database connection failed: " + err.getMessage());
                });
            }


        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("User ID must be a valid integer");
        }
    }

}
