package com.nubebuster.cryptoprofittracker.data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsHandler {

    private static SettingsHandler instance;

    public static SettingsHandler getInstance() throws IOException, InterruptedException {
        if (instance != null) return instance;
        return new SettingsHandler();
    }


    private String apiKey;
    private String apiSecret;
    private String dataFile;
    private File configFile;


    private SettingsHandler() throws IOException, InterruptedException {
        File documents = new File(FileHandler.getDocumentsPath());
        if (!documents.exists()) {
            documents.mkdir();
        }

        configFile = new File(documents, "config.txt");
    }

    boolean loaded = false;

    public void loadData() throws IOException, InterruptedException {
        if (loaded) {
            System.err.println("Tried to load data but it is already loaded.");
            return;
        }
        if (configFile.exists()) {
            String[] configData = FileHandler.readData(configFile);
            for (String line : configData) {
                if (line.startsWith("apiKey="))
                    apiKey = line.replace("apiKey=", "");
                else if (line.startsWith("apiSecret="))
                    apiSecret = line.replace("apiSecret=", "");
                else if (line.startsWith("dataFile="))
                    dataFile = line.replace("dataFile=", "");
            }
        }
        if (dataFile == null || dataFile.isEmpty()) {
            dataFile = FileHandler.getDocumentsPath() + File.separator + "data.xlsx";
        }
        loaded = true;
    }

    public void saveSettings() throws IOException, InterruptedException {
        if (!loaded)
            return;
        if (!configFile.exists())
            configFile.createNewFile();
        FileWriter fr = new FileWriter(configFile);
        fr.write("apiKey=" + apiKey + "\napiSecret=" +
                apiSecret + "\ndataFile=" + dataFile + "\nNote: this file is not intended to be edited manually.");
        fr.flush();
        fr.close();
        System.out.println("Saved settings to " + configFile.getPath());
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getDataFile() {
        return dataFile;
    }

    public void setDataFile(String dataFile) {
        this.dataFile = dataFile;
    }
}
