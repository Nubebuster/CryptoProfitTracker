package com.nubebuster.cryptoprofittracker.data;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileHandler {

    public static final String SUPPORTED_FORMATS = "[.xlsx, .csv]";

    public static List<TradeOrder> loadOrders(File dataFile, String symbol) throws IOException, InvalidFormatException, UnsupportedFileFormatException {
        if (!dataFile.exists()) {
            System.err.println("Data file not found: " + dataFile.getPath());
            return null;
        }
        List<TradeOrder> orders = null;
        if (dataFile.getName().endsWith(".xlsx")) {
            OPCPackage pkg = OPCPackage.open(dataFile);
            Workbook wb = new XSSFWorkbook(pkg);
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rows = sheet.rowIterator();
            orders = parseOrdersXLSX(symbol, rows);
            try {
                pkg.close();
            } catch (FileNotFoundException ex) {
                //if its open in another program
            }
        } else if (dataFile.getName().endsWith(".csv")) {
            List<String> data = new ArrayList<String>();
            BufferedReader br = new BufferedReader(new FileReader(dataFile));
            String line = "";
            while ((line = br.readLine()) != null)
                data.add(line);
            orders = parseOrdersCSV(symbol, data);
        } else {
            throw new UnsupportedFileFormatException("Unsupported data format. Current support: " + SUPPORTED_FORMATS);
        }
        return orders;
    }

    /**
     * For parsing .csv format
     *
     * @param pair
     * @param data
     * @return
     */
    private static List<TradeOrder> parseOrdersCSV(String pair, List<String> data) {
        List<TradeOrder> orders = new ArrayList<TradeOrder>();
        for (String r : data) {
            if (r.startsWith("Date")) //headers
                continue;
            String[] row = r.replaceAll("(\"[^\",]+),([^\"]+\")", "$1$2").replace("\"", "").split(",");
            String linePair = row[1];
            if (!linePair.equals(pair))
                continue;

            long time = Timestamp.valueOf(row[0]).getTime();
            boolean buy = row[2].equals("BUY");
            double price = Double.parseDouble(row[3]);
            double amount = Double.parseDouble(row[4].replaceAll("[^\\d.]", ""));
            double total = Double.parseDouble(row[5].replaceAll("[^\\d.]", ""));
            double fee = Double.parseDouble(row[6].replaceAll("[^\\d.]", ""));
            String feeCoin = row[6].replaceAll("[\\d.]", "");
            orders.add(new TradeOrder(pair, buy, price, amount, total, fee, feeCoin, time));
        }
        return orders;
    }


    /**
     * For parsing .xlsx format
     *
     * @param pair
     * @param rows
     * @return
     */
    private static List<TradeOrder> parseOrdersXLSX(String pair, Iterator<Row> rows) {
        List<TradeOrder> orders = new ArrayList<TradeOrder>();
        while (rows.hasNext()) {
            Row row = rows.next();
            if (row.getRowNum() == 0) //headers
                continue;
            String linePair = row.getCell(1).getStringCellValue();
            if (!linePair.equals(pair))
                continue;

            long time = Timestamp.valueOf(row.getCell(0).getStringCellValue()).getTime();
            boolean buy = row.getCell(2).getStringCellValue().equals("BUY");
            double price = Double.parseDouble(row.getCell(3).getStringCellValue());
            double amount = Double.parseDouble(row.getCell(4).getStringCellValue());
            double total = Double.parseDouble(row.getCell(5).getStringCellValue());
            double fee = Double.parseDouble(row.getCell(6).getStringCellValue());
            String feeCoin = row.getCell(7).getStringCellValue();

            orders.add(new TradeOrder(pair, buy, price, amount, total, fee, feeCoin, time));
        }
        return orders;
    }

    public static String getDocumentsPath() throws InterruptedException, IOException {
        Process p = Runtime.getRuntime().exec("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v personal");
        p.waitFor();
        InputStream in = p.getInputStream();
        byte[] b = new byte[in.available()];
        in.read(b);
        in.close();
        return new String(b).split("\\s\\s+")[4] + File.separator + "CryptoProfitTracker";
    }

    public static String[] readData(File f) throws IOException {
        FileReader fr = new FileReader(f);
        char[] data = new char[(int) f.length()];
        fr.read(data);
        fr.close();
        return new String(data).split("\n");
    }
}
