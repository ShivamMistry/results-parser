package com.speed.evision;


import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

public class EvisionParser {

    private static final String BASE_URL = "https://evision.york.ac.uk/urd/sits.urd/run/";
    private static final String OUTPUT = "output.db";

    public final Collection<Module> modules;

    private final String baseUrl;
    private final SitsAuth auth;

    public EvisionParser(final String baseUrl, SitsAuth auth) throws IOException {
        this.baseUrl = baseUrl;
        this.auth = auth;
        Document document = auth.homepage;
        Element e = document.select("a:contains(Module and Assessment)").first();
        String href = e.attr("href");
        String results = get(findCompSci(get(baseUrl + href)));
        modules = readResults(results);
    }

    private Collection<Module> readResults(final String html) throws IOException {
        Document document = Jsoup.parse(html);
        Elements rows = document.select("input[name=\"butselect\"]");
        List<Module> modules = new LinkedList<>();
        for (Element row : rows) {
            String url = baseUrl + pathFromOnclick(row.attr("onclick"));
            String moduleHtml = get(url);
            modules.add(readModule(moduleHtml));
        }
        return Collections.unmodifiableCollection(modules);
    }

    private Module readModule(String html) {
        Document document = Jsoup.parse(html);
        Module module = null;
        List<Module.Result> results = new LinkedList<>();
        for (Element e : document.select("table")) {
            String summary = e.attr("summary");
            if (summary.equals("details of overall module result")) {
                // Process module result
                Elements tds = e.select("td");
                if (tds.size() != 5) {
                    throw new RuntimeException("Format has changed, there are no longer 5 elements in the 'Overall module result' table");
                }
                String year = tds.get(0).text();
                String moduleCode = tds.get(1).text();
                String moduleName = tds.get(2).text();
                int credits = Integer.parseInt(tds.get(3).text().trim());
                String markText = tds.get(4).text().trim();
                int mark;
                if (markText.contains("(")) {
                    System.out.println("Resit detected, assuming pass (40)");
                    mark = 40;
                } else {
                    mark = markText.equals("-") ? -1 : Integer.parseInt(markText);
                }
                module = new Module(year, moduleCode, moduleName, credits, mark);
                System.out.println(module);
            } else if (summary.equals("details of assessment components and feedback if available")) {
                // Process components
                Elements rows = e.select("tr");

                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() == 0) continue; // Likely to be the headings
                    if (tds.size() != 5) {
                        throw new RuntimeException("Format has changed, there are no longer 5 elements in the 'Assessment Components' table");
                    }
                    String name = tds.get(0).text();
                    int weighting = Integer.parseInt(tds.get(1).text().trim());
                    int attempt = Integer.parseInt(tds.get(2).text().trim());
                    String markText = tds.get(3).text().trim();
                    int unconfirmedMark = markText.equals("-") ? -1 : Integer.parseInt(markText);
                    markText = tds.get(4).text().trim();
                    int confirmedMark = markText.equals("-") ? -1 : Integer.parseInt(markText);
                    Module.Result r = new Module.Result(name, weighting, attempt, unconfirmedMark, confirmedMark);
                    System.out.println(r);
                    results.add(r);
                }
            }
        }
        if (module == null) {
            System.err.println("Page did not have any module results");
        } else {
            module.setResults(results);
        }
        if (results.size() == 0) {
            System.err.println("Page did not have any module component results");
        }
        return module;
    }

    private String get(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        HttpResponse response = auth.client.execute(get);
        return EntityUtils.toString(response.getEntity());
    }

    private String pathFromOnclick(String onclick) {
        return onclick.substring(onclick.indexOf('\'') + 1, onclick.lastIndexOf('\''));
    }

    private String findCompSci(final String html) {
        Document document = Jsoup.parse(html);
        Element table = document.select("table[summary=\"student details displayed by programme of study\"]").first();
        Element row = table.select("input[value=\"Select\"]").first();
        String onclick = row.attr("onclick");
        String path = pathFromOnclick(onclick);
        return baseUrl + path;
    }

    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            try {
                InputStream is = EvisionParser.class.getResourceAsStream("/config.properties");
                if (is == null) {
                    System.err.println("No config file found, try copying the config.properties.example file");
                    System.exit(3);
                }
                props.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (props.isEmpty()) System.exit(1);
            if (!props.containsKey("username") || !props.containsKey("password")) {
                System.err.println("Properties file did not contain correct config, ensure that a username and password are present");
                System.exit(2);
            }
            String username = props.getProperty("username");
            String password = props.getProperty("password");
            String url = props.getProperty("sitsUrl", SitsAuth.YORK_URL);
            String baseUrl = props.getProperty("baseUrl", BASE_URL);
            String outputFile = props.getProperty("outputFile", OUTPUT);
            new EvisionParser(baseUrl, SitsAuth.create(url, username, password)).output(outputFile);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    public void output(String outputFile) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + outputFile);
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(5);
        statement.execute("DROP TABLE IF EXISTS modules");
        statement.execute("DROP TABLE IF EXISTS results");
        statement.execute("CREATE TABLE modules (code varchar primary key not null, modName varchar not null, yr varchar not null, mark integer, credits integer not null)");
        statement.execute("CREATE TABLE results ( moduleCode varchar not null, resultName varchar not null, weighting integer not null, attempt integer, unconfirmed_mark integer, confirmed_mark integer, foreign key(moduleCode) references modules(code))");
        PreparedStatement moduleMark = connection.prepareStatement("INSERT INTO modules (code, modName, yr, credits, mark) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement moduleNoMark = connection.prepareStatement("INSERT INTO modules (code, modName, yr, credits) VALUES (?, ?, ?, ?)");
        PreparedStatement resultConfirmedMark = connection.prepareStatement("INSERT INTO results (moduleCode, resultName, weighting, attempt, confirmed_mark) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement resultUnconfirmedMark = connection.prepareStatement("INSERT INTO results (moduleCode, resultName, weighting, attempt, unconfirmed_mark) VALUES(?, ?, ?, ?, ?)");
        PreparedStatement resultNoMark = connection.prepareStatement("INSERT INTO results (moduleCode, resultName, weighting, attempt) VALUES (?, ?, ?, ?)");
        for (Module module : modules) {
            PreparedStatement stmt = module.mark == -1 ? moduleNoMark : moduleMark;
            stmt.setString(1, module.moduleCode);
            stmt.setString(2, module.name);
            stmt.setString(3, module.year);
            stmt.setInt(4, module.credits);
            if (module.mark != -1) {
                stmt.setInt(5, module.mark);
            }
            stmt.execute();
            for (Module.Result result : module.getResults()) {
                PreparedStatement st;
                if (result.confirmedResult == -1 && result.uncomfirmedResult == -1) {
                    st = resultNoMark;
                } else if (result.confirmedResult == -1) {
                    st = resultUnconfirmedMark;
                    st.setInt(5, result.uncomfirmedResult);
                } else {
                    st = resultConfirmedMark;
                    st.setInt(5, result.confirmedResult);
                }
                st.setString(1, module.moduleCode);
                st.setString(2, result.name);
                st.setInt(3, result.weighting);
                st.setInt(4, result.attempt);
                st.execute();
                st.clearParameters();

            }
            stmt.clearParameters();
        }
        connection.close();
    }
}
