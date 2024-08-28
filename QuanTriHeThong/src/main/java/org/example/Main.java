package main.java.org.example;

import io.vertx.core.Vertx;
import main.java.org.example.school.endpoint.MainVerticle;
import main.java.org.example.school.endpoint.SchoolApiVerticle;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }
}