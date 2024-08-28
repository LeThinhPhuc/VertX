package main.java.org.example.school.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.RowSet;

public class StudentDao {
    private final SqlClient sqlClient;

    public StudentDao(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

//    public Future<RowSet> fetchAll() {
//        return sqlClient.getConnection().compose(conn ->
//                conn.query("SELECT * FROM students").execute()
//                        .onSuccess(result -> {
//                            conn.close();
//                            return Future.succeededFuture(result);
//                        })
//                        .onFailure(err -> {
//                            conn.close();
//                            return Future.failedFuture(err);
//                        })
//        );
//    }
//
//    public Future<RowSet> fetchById(int id) {
//        JsonArray params = new JsonArray().add(id);
//        return sqlClient.getConnection().compose(conn ->
//                conn.preparedQuery("SELECT * FROM students WHERE id = ?")
//                        .execute(params)
//                        .onSuccess(result -> {
//                            conn.close();
//                            return Future.succeededFuture(result);
//                        })
//                        .onFailure(err -> {
//                            conn.close();
//                            return Future.failedFuture(err);
//                        })
//        );
//    }
}
