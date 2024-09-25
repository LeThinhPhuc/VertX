package main.java.org.example.school.endpoint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.sqlclient.SqlAuthenticationOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.vertx.ext.auth.sqlclient.SqlAuthentication;
public class MainVerticle extends AbstractVerticle {

    // Khai báo đối tượng MSSQLPool để quản lý kết nối đến cơ sở dữ liệu MSSQL
    private MSSQLPool client;

    // Cấu hình tùy chọn cho SqlAuthentication, dùng để xác thực người dùng trong cơ sở dữ liệu
    SqlAuthenticationOptions sqlAuthOptions = new SqlAuthenticationOptions();


    AuthenticationProvider authenticationProvider;
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
                .setDatabase("ODH_Sys") // Tên cơ sở dữ liệu
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
        authenticationProvider = SqlAuthentication.create(client, sqlAuthOptions);

// Khởi tạo Router để xử lý các yêu cầu HTTP
        Router router = Router.router(vertx);

// Thêm BodyHandler để xử lý dữ liệu trong body của yêu cầu
        router.route().handler(BodyHandler.create());

// Định nghĩa các endpoint API và phương thức xử lý
        router.route("/users/*").handler(JWTAuthHandler.create(provider));
        router.get("/users").handler(this::getAllUsers); // Lấy tất cả người dùng
        router.get("/users/:id").handler(this::getUserById); // Lấy người dùng theo ID
        router.delete("/users/:userId").handler(this::deleteByUserId);
        router.put("/users/:userId").handler(this::updateStudent);

        router.post("/signup").handler(this::signUp); // Đăng ký người dùng mới

        router.post("/login").handler(this::handleLogin); // Xử lý đăng nhập

        router.post("/changepassword").handler(this::changePassword);

// Khởi động máy chủ HTTP và lắng nghe trên cổng 8080
        vertx.createHttpServer()
                .requestHandler(router) // Đăng ký router để xử lý các yêu cầu HTTP
                .listen(8081, result -> {
                    if (result.succeeded()) {
                        System.out.println("Server started on port 8081");
                    } else {
                        result.cause().printStackTrace(); // In lỗi nếu không khởi động được máy chủ
                    }
                });

    }

    private void signUp(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String username = body.getString("username");
        String password = body.getString("password");

        client.getConnection().compose(conn -> {
            String checkSql = "SELECT COUNT(*) AS count FROM E00T00001 WHERE UserName = @p1";
            return conn.preparedQuery(checkSql)
                    .execute(Tuple.of(username))
                    .onComplete(ar -> conn.close())
                    .compose(result -> {
                        if (result.iterator().next().getInteger("count") > 0) {
                            return Future.failedFuture("Username already exists");
                        } else {
                            String salt = VertxContextPRNG.current().nextString(32);
                            String hash = sqlAuth.hash(
                                    "pbkdf2",
                                    salt,
                                    password
                            );

                            // Chuyển đổi salt và hash thành mảng byte
                            byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
                            byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);

                            String insertSql = "INSERT INTO E00T00001 (UserName, UserPasswordHash, UserSalt) OUTPUT INSERTED.UserID VALUES (@p1, @p2, @p3)";
                            return conn.preparedQuery(insertSql)
                                    .execute(Tuple.of(username, Buffer.buffer(hashBytes), Buffer.buffer(saltBytes)))
                                    .compose(insertResult -> {
                                        if (insertResult.rowCount() > 0) {
                                            Row row = insertResult.iterator().next();
                                            BigDecimal UserID = row.getBigDecimal("UserID");
                                            JsonObject response = new JsonObject().put("Message", "Registered success !");
                                            return Future.succeededFuture(response);
                                        } else {
                                            return Future.failedFuture("Failed to retrieve UserID");
                                        }
                                    });
                        }
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().toString());
            } else {
                ctx.response().setStatusCode(400).end(ar.cause().getMessage());
            }
        });
    }



    // Login
    private void handleLogin(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String username = body.getString("username");
        String password = body.getString("password");
        JsonObject authInfo = new JsonObject()
                .put("username", username)
                .put("password", password);

        authenticationProvider.authenticate(authInfo)
                .onSuccess(user -> System.out.println("User: " + user.principal()))
                .onFailure(err -> {
                    // Failed!
                    System.out.println("Khong xac thuc duoc user");
                });

        if (username == null || password == null) {
            ctx.response().setStatusCode(400).end("Username and password are required");
            return;
        }

        client.getConnection().compose(conn -> {
            return conn.preparedQuery("SELECT UserID, UserPasswordHash, UserSalt FROM E00T00001 WHERE username = @p1")
                    .execute(Tuple.of(username))
                    .onComplete(ar -> conn.close())
                    .compose(res -> {
                        if (res.size() == 0) {
                            return Future.failedFuture("User not found");
                        } else {
                            Row row = res.iterator().next();

                            byte[] storedHashBytes = row.getBuffer("UserPasswordHash").getBytes();
                            byte[] saltBytes = row.getBuffer("UserSalt").getBytes();

                            // Chuyển đổi mảng byte về chuỗi để hash mật khẩu
                            String salt = new String(saltBytes, StandardCharsets.UTF_8);
                            String computedHash = sqlAuth.hash("pbkdf2", salt, password);

                            // So sánh kết quả hash với giá trị trong cơ sở dữ liệu
                            if (computedHash.equals(new String(storedHashBytes, StandardCharsets.UTF_8))) {
                                BigDecimal UserID = row.getBigDecimal("UserID");
                                String token = provider.generateToken(new JsonObject().put("UserID", UserID));
                                return Future.succeededFuture(token);
                            } else {
                                return Future.failedFuture("Invalid password");
                            }
                        }
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response().putHeader("content-type", "application/json")
                        .end(new JsonObject().put("token", ar.result()).encode());
            } else {
                ctx.response().setStatusCode(401).end(ar.cause().getMessage());
            }
        });
    }


//    private void handleLogin(RoutingContext ctx){
//
//        JsonObject body = ctx.getBodyAsJson();
//        String username = body.getString("username");
//        String password = body.getString("password");
//        client.getConnection().compose(conn -> {
//            // Truy vấn người dùng để lấy salt
//            return conn.preparedQuery("SELECT UserID, UserPasswordHash FROM E00T00001 WHERE username = @p1")
//                    .execute(Tuple.of(username))
//                    .onComplete(ar -> conn.close())
//                    .compose(res -> {
//                        if (res.size() == 0) {
//                            return Future.failedFuture("User not found");
//                        } else {
//                            Row row = res.iterator().next();
//                            String storedHash = row.getString("UserPasswordHash");
//                            System.out.println("Hash store; "+storedHash);
//                            BigDecimal UserID = row.getBigDecimal("UserID");
//                            // So sánh kết quả hash với giá trị trong cơ sở dữ liệu
//                                // Mật khẩu đúng, tạo JWT và trả về cho người dùng
//                                String token = provider.generateToken(new JsonObject().put("UserID", UserID));
//                                return Future.succeededFuture(token);
//
//                        }
//                    });
//        }).onComplete(ar -> {
//            if (ar.succeeded()) {
//                ctx.response().putHeader("content-type", "application/json")
//                        .end(new JsonObject().put("token", ar.result()).encode());
//            } else {
//                ctx.response().setStatusCode(401).end(ar.cause().getMessage());
//            }
//        });
//    }

    // Handler to get all users
    private void getAllUsers(RoutingContext context) {
        client.getConnection().compose(conn ->
                conn.query("SELECT * FROM E00T00001").execute()
                        .compose(result -> {
                            conn.close();
                            // Chuyển đổi RowSet thành JsonArray
                            JsonArray users = new JsonArray();
                            for (Row row : result) {
                                JsonObject userJson = new JsonObject()
                                        .put("UserCode", row.getString("UserCode") != null ? row.getString("UserCode") : "")
                                        .put("UserName", row.getString("UserName") != null ? row.getString("UserName") : "")
                                        .put("EmployeeID", row.getBigDecimal("EmployeeID") != null ? row.getBigDecimal("EmployeeID") : BigDecimal.ZERO)
                                        .put("RecoveryEmail", row.getString("RecoveryEmail") != null ? row.getString("RecoveryEmail") : "")
                                        .put("IsFirstChangePassword", row.getBoolean("IsFirstChangePassword") != null ? row.getBoolean("IsFirstChangePassword") : false)
                                        .put("ActivatedDate", row.getLocalDateTime("ActivatedDate") != null ? row.getLocalDateTime("ActivatedDate").toString() : "")
                                        .put("DivisionIDList", row.getString("DivisionIDList") != null ? row.getString("DivisionIDList") : "")
                                        .put("IsSystem", row.getBoolean("IsSystem") != null ? row.getBoolean("IsSystem") : false)
                                        .put("Active", row.getBoolean("Active") != null ? row.getBoolean("Active") : false)
                                        .put("CreatedDate", row.getLocalDateTime("CreatedDate") != null ? row.getLocalDateTime("CreatedDate").toString() : "")
                                        .put("CreatedUserID", row.getBigDecimal("CreatedUserID") != null ? row.getBigDecimal("CreatedUserID") : BigDecimal.ZERO)
                                        .put("UpdatedDate", row.getLocalDateTime("UpdatedDate") != null ? row.getLocalDateTime("UpdatedDate").toString() : "")
                                        .put("UpdatedUserID", row.getBigDecimal("UpdatedUserID") != null ? row.getBigDecimal("UpdatedUserID") : BigDecimal.ZERO);


                                users.add(userJson);
                            }
                            return io.vertx.core.Future.succeededFuture(users);
                        })
        ).onComplete(ar -> {
            if (ar.succeeded()) {
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(((JsonArray) ar.result()).encode());
            } else {
                System.err.println("Error retrieving users: " + ar.cause().getMessage());
                context.response().setStatusCode(500).end("Failed to retrieve users");
            }
        });
    }


    private void getUserById(RoutingContext context) {
        String id = context.request().getParam("id");

        // Kiểm tra xem id có hợp lệ không
        if (id == null || id.isEmpty()) {
            context.response().setStatusCode(400).end("Missing or empty 'id' parameter");
            return;
        }

        System.out.println("Request ID: " + id); // Log ID nhận được
        BigDecimal big = new BigDecimal(id);
        Tuple tuple = Tuple.tuple()
                .addBigDecimal(big);

        client.getConnection().compose(conn -> {
            System.out.println("Got a connection");

            // Tạo câu lệnh SQL với tham số gán tên
//            String sql = "SELECT * FROM Student WHERE id = "+"'"+id+"'";
            String sql = "SELECT * FROM E00T00001 WHERE UserID = @p1";

            System.out.println("Got a query: "+ sql);
            // Tạo prepared query và truyền tham số vào
            return conn.preparedQuery(sql)
                    .execute(tuple) // Thay thế tham số với giá trị 'id'
                    .compose(result -> {
                        conn.close(); // Đóng kết nối sau khi hoàn tất truy vấn
                        System.out.println("Query executed successfully");

                        JsonArray users = new JsonArray();
                        for (Row row : result) {
                            JsonObject userJson = new JsonObject()
                                    .put("UserCode", row.getString("UserCode") != null ? row.getString("UserCode") : "")
                                    .put("UserName", row.getString("UserName") != null ? row.getString("UserName") : "")
                                    .put("EmployeeID", row.getBigDecimal("EmployeeID") != null ? row.getBigDecimal("EmployeeID") : BigDecimal.ZERO)
                                    .put("RecoveryEmail", row.getString("RecoveryEmail") != null ? row.getString("RecoveryEmail") : "")
                                    .put("IsFirstChangePassword", row.getBoolean("IsFirstChangePassword") != null ? row.getBoolean("IsFirstChangePassword") : false)
                                    .put("ActivatedDate", row.getLocalDateTime("ActivatedDate") != null ? row.getLocalDateTime("ActivatedDate").toString() : "")
                                    .put("DivisionIDList", row.getString("DivisionIDList") != null ? row.getString("DivisionIDList") : "")
                                    .put("IsSystem", row.getBoolean("IsSystem") != null ? row.getBoolean("IsSystem") : false)
                                    .put("Active", row.getBoolean("Active") != null ? row.getBoolean("Active") : false)
                                    .put("CreatedDate", row.getLocalDateTime("CreatedDate") != null ? row.getLocalDateTime("CreatedDate").toString() : "")
                                    .put("CreatedUserID", row.getBigDecimal("CreatedUserID") != null ? row.getBigDecimal("CreatedUserID") : BigDecimal.ZERO)
                                    .put("UpdatedDate", row.getLocalDateTime("UpdatedDate") != null ? row.getLocalDateTime("UpdatedDate").toString() : "")
                                    .put("UpdatedUserID", row.getBigDecimal("UpdatedUserID") != null ? row.getBigDecimal("UpdatedUserID") : BigDecimal.ZERO);


                            users.add(userJson);
                        }
                        return io.vertx.core.Future.succeededFuture(users);
                    });
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Query completed successfully");
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().toString());
            } else {
                System.out.println("Failure: " + ar.cause().getMessage());
                context.response().setStatusCode(500).end("Failed to retrieve user");
            }
        });
    }

    // Handler to delete student by ID
    private void deleteByUserId(RoutingContext context) {
        String id = context.request().getParam("userId");

        if (id == null || id.isEmpty()) {
            context.response().setStatusCode(400).end("User ID is required");
            return;
        }

        try {
            Integer userId = Integer.parseInt(id);

            client.getConnection().compose(conn -> {
                String sql = "DELETE FROM E00T00001 WHERE UserID = @p1";
                return conn.preparedQuery(sql)
                        .execute(Tuple.of(userId))
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
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("User ID must be a valid integer");
        }
    }


    // Handler to update student by ID
    // Handler to update student by ID
    private void updateStudent(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();

        // Check if the body is null
        if (body == null) {
            ctx.response().setStatusCode(400).end("Request body must be in JSON format");
            return;
        }

        String idParam = ctx.request().getParam("userId");

        if (idParam == null || idParam.isEmpty()) {
            ctx.response().setStatusCode(400).end("User ID is required");
            return;
        }

        try {
            Integer userId = Integer.parseInt(idParam);
            String userName = body.getString("UserName");
            String userCode = body.getString("UserCode");

            if (userName == null || userCode == null) {
                ctx.response().setStatusCode(400).end("UserName and UserCode are required");
                return;
            }

            client.getConnection().compose(conn -> {
                String sql = "UPDATE E00T00001 SET UserName = @p1, UserCode = @p2 WHERE UserID = @p3";

                return conn.preparedQuery(sql)
                        .execute(Tuple.of(userName, userCode, userId))
                        .onComplete(ar -> {
                            conn.close();
                            if (ar.succeeded()) {
                                if (ar.result().rowCount() > 0) {
                                    ctx.response().putHeader("content-type", "application/json")
                                            .end(new JsonObject().put("status", "success").put("message", "User updated successfully").encode());
                                } else {
                                    ctx.response().setStatusCode(404).end(new JsonObject().put("status", "fail").put("message", "User not found").encode());
                                }
                            } else {
                                ctx.response().setStatusCode(500).end("Failed to update user: " + ar.cause().getMessage());
                            }
                        });
            }).onFailure(err -> {
                ctx.response().setStatusCode(500).end("Database connection failed: " + err.getMessage());
            });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("User ID must be a valid integer");
        }
    }

    private void changePassword(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String password = body.getString("Password");
        String newPassword = body.getString("NewPassword");
        String confirmNewPassword = body.getString("ConfirmNewPassword");
        String email = body.getString("RecoveryEmail");

        if (password == null || newPassword == null || confirmNewPassword == null) {
            ctx.response().setStatusCode(400).end("Password, new password and confirm new password are required");
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            ctx.response().setStatusCode(400).end("New Password and Confirm New Password don't match");
            return;
        }


        client.getConnection().compose(conn ->
                conn.preparedQuery("SELECT UserSalt, UserPasswordHash FROM E00T00001 WHERE RecoveryEmail = @p1")
                        .execute(Tuple.of(email))
                        .onComplete(ar -> conn.close())
                        .compose(res -> {
                            if (res.size() == 0) {
                                return Future.failedFuture("User not found");
                            } else {
                                Row row = res.iterator().next();

                                byte[] storedHashBytes = row.getBuffer("UserPasswordHash").getBytes();
                                byte[] saltBytes = row.getBuffer("UserSalt").getBytes();

                                // Chuyển đổi mảng byte về chuỗi để hash mật khẩu
                                String salt = new String(saltBytes, StandardCharsets.UTF_8);
                                String computedHash = sqlAuth.hash("pbkdf2", salt, password);



                                if (computedHash.equals(new String(storedHashBytes, StandardCharsets.UTF_8))) {
                                    // Hash mật khẩu mới với salt cũ
                                    String newHash = sqlAuth.hash("pbkdf2", salt, newPassword);

                                    return conn.preparedQuery("UPDATE E00T00001 SET UserPasswordHash = @p1, IsFirstChangePassword = @p2 WHERE RecoveryEmail = @p3")
                                            .execute(Tuple.of(Buffer.buffer(newHash.getBytes(StandardCharsets.UTF_8)), true, email))
                                            .onComplete(ar2 -> {
                                                conn.close();
                                                if (ar2.succeeded()) {
                                                    if (ar2.result().rowCount() > 0) {
                                                        ctx.response().putHeader("content-type", "application/json")
                                                                .end(new JsonObject().put("status", "success").put("message", "Password changed successfully").encode());
                                                    } else {
                                                        ctx.response().setStatusCode(404).end(new JsonObject().put("status", "fail").put("message", "User not found").encode());
                                                    }
                                                } else {
                                                    ctx.response().setStatusCode(500).end("Failed to change password: " + ar2.cause().getMessage());
                                                }
                                            });
                                } else {
                                    return Future.failedFuture("Current password is incorrect");
                                }
                            }
                        })
        ).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.response().putHeader("content-type", "application/json")
                        .end(new JsonObject().put("message", "Password changed successfully").encode());
            } else {
                ctx.response().setStatusCode(401).end(ar.cause().getMessage());
            }
        });
    }




}
