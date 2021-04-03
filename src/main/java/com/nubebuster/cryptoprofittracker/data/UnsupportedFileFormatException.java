package com.nubebuster.cryptoprofittracker.data;

public class UnsupportedFileFormatException extends Exception {
    public UnsupportedFileFormatException(String errorMessage) {
        super(errorMessage);
    }
}