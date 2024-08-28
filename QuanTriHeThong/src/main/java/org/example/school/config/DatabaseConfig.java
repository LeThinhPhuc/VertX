package main.java.org.example.school.config;



public class DatabaseConfig {

    private static final String HOST = "localhost";
    private static final int PORT = 1433;
    private static final String DATABASE = "school_db";
    private static final String USER = "sa";
    private static final String PASSWORD = "sa";

//    public static SQLClient createClient(Vertx vertx) {
//        SQLClientOptions options = new SQLClientOptions()
//                .setDriverName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
//                .setJdbcUrl(String.format("jdbc:sqlserver://%s:%d;database=%s", HOST, PORT, DATABASE))
//                .setUser(USER)
//                .setPassword(PASSWORD);
//
//        return JDBCClient.createShared(vertx, options);
//    }
}
