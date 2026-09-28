package com.example.haas.hardware;

/** One row of the Resource page: global capacity/availability plus what the selected project holds. */
public record HardwareView(String name, String description, int capacity, int available, int checkedOut) {
}
