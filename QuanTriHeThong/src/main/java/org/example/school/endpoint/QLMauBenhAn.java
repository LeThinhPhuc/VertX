package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.sqlclient.SqlAuthentication;
import io.vertx.ext.auth.sqlclient.SqlAuthenticationOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class QLMauBenhAn extends AbstractVerticle {
    private MSSQLPool client;

    // Cấu hình tùy chọn cho SqlAuthentication, dùng để xác thực người dùng trong cơ sở dữ liệu
    SqlAuthenticationOptions sqlAuthOptions = new SqlAuthenticationOptions();

    // Khai báo đối tượng SqlAuthentication để thực hiện xác thực người dùng
    SqlAuthentication sqlAuth;

    // Khai báo đối tượng JWTAuth để xử lý JSON Web Tokens (JWT) cho xác thực và phân quyền
    JWTAuth provider;


    @Override
    public void start() {
        // Tạo JWTAuth provider với các tùy chọn cấu hình
        provider = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256") // Sử dụng thuật toán HS256 để mã hóa JWT
                        .setBuffer("ODH System"))); // Khóa bí mật dùng để ký JWT

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

// Cấu hình SQLAuthentication với client và các tùy chọn
        sqlAuth = SqlAuthentication.create(client, sqlAuthOptions);

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
//        router.route("/odh/*").handler(JWTAuthHandler.create(provider));


        router.get("/odh/getAllCriteriaGroup").handler(this::getAllCriteriaGroup);
// Khởi động máy chủ HTTP và lắng nghe trên cổng 8080
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
                                    .put("CriteriaGroupName", row.getString("CriteriaGroupName") != null ? row.getString("CriteriaGroupName") : "");

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


}
