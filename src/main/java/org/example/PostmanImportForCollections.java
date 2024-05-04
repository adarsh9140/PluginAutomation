package org.example;

import com.eka.middleware.pub.util.postman.PostmanCollection;
import com.eka.middleware.template.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class PostmanImportForCollections {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/marketplace";
    private static final String JDBC_URL_PLUGIN = "jdbc:mysql://localhost:3306/plugin";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    private static final String POSTMAN_API_URL = "https://api.getpostman.com/collections/";

    public static void main(String[] args) {

        insertDataIntoPluginCategoriesTable(retrieveCategoriesFromMarketplace());

        List<String> collectionIds = getCollectionIdsFromDatabase();
        for (String correlationId : collectionIds) {
            try {
                String collectionApiResponse = getCollectionApiResponse(correlationId);
                processCollectionApiResponse(collectionApiResponse,correlationId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static List<String> getCollectionIdsFromDatabase() {
        List<String> correlationIds = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String query = "SELECT correlationId FROM collection";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    String correlationId = resultSet.getString("correlationId");
                    correlationIds.add(correlationId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return correlationIds;
    }
    private static String getCollectionApiResponse(String correlationId) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        String apiUrl = POSTMAN_API_URL + correlationId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", "PMAK-660583b6c532470001864af7-8b1f4139f44046f8b5f271e81769a70dd7")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
    private static void processCollectionApiResponse(String collectionApiResponse, String correlationId) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(collectionApiResponse);

            JsonNode infoNode = root.path("collection").path("info");

            String packageName = infoNode.path("name").asText();

            String modifiedPackageName = packageName.replaceAll("^\\d*([^a-zA-Z0-9]|$)+", "").replaceAll("[^a-zA-Z0-9]+", "_");

            String addVersionInPackageName = modifiedPackageName + "-v1.0";

            ObjectNode collectionWithoutInfo = (ObjectNode) root.path("collection");
            collectionWithoutInfo.remove("info");

            PostmanCollection postmanCollection = new Gson().fromJson(collectionWithoutInfo.toString(), PostmanCollection.class);

            if (postmanCollection != null && postmanCollection.getItem() != null) {
                ImportPostmanV2.createFlowServicesClient(PropertyManager.getPackagePath(Tenant.getTempTenant("default")), "", modifiedPackageName, postmanCollection.getItem(), postmanCollection);
                String packagePath = PropertyManager.getPackagePath(Tenant.getTempTenant("default"));
                String buildsDirPath = packagePath + "packages/" + modifiedPackageName;
                ExportBuild.createBuild(buildsDirPath, "E:/exportedZips", addVersionInPackageName);
                String installing_path = "packages/" + modifiedPackageName;
                getCollectionDataFromDatabase(correlationId, modifiedPackageName, installing_path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void getCollectionDataFromDatabase(String correlationId,String fileName,String installing_path) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String query = "SELECT name, correlationId, workspaceId, vendor,name_slug FROM collection WHERE correlationId = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, correlationId);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String name = resultSet.getString("name");
                    String vendor = resultSet.getString("vendor");
                    String name_slug = resultSet.getString("name_slug");
                    String shortDescription = "Transform your applications with our versatile plugin solutions. Simplify integration, enhance functionality, and optimize performance. Seamlessly integrate " + name + " to unlock new capabilities, increase productivity, and drive innovation.";

                    String description = "<p style=\"text-align: justify;\"><strong><span style=\"font-size:13pt;\">Plugin Installation Instructions:</span></strong></p>" +
                            "<p style=\"text-align: justify;\"><span style=\"font-size:11pt;\">1. This package will be installed in&nbsp;</span><strong><span style=\"font-size:11pt;\">package&gt; " + fileName + ".</span></strong></p>" +
                            "<p style=\"text-align: justify;\"><span style=\"font-size:11pt;\">2. All services are placed in the client folder you can invoke these services into your API service.</span></p>" +
                            "<p style=\"text-align: justify;\"><span style=\"font-size:11pt;\">3. To set API global credential/parameters go in&nbsp;</span><strong><span style=\"font-size:11pt;\">package &gt; " + fileName + " &gt; dependency &gt; config</span></strong><span style=\"font-size:11pt;\">&nbsp;&amp; add all credential/parameters in package file.</span></p>" +
                            "<p style=\"text-align: justify;\"><span style=\"font-size:11pt;\">4. Map all the required parameters using mapping lines. Follow the screenshots.</span></p>";


                    int pluginId = insertDataIntoPluginTable(name,name_slug,shortDescription,getCurrentTimestamp(),getCurrentTimestamp(),fileName,vendor,installing_path,correlationId,description);

                    insertDataIntoLicenseTable(pluginId);
                    insertDataIntoMetricsTable(pluginId,"");
                    insertDataIntoOwnerTable(pluginId);
                    insertDataIntoPluginTagsTable(pluginId,6);
                    insertDataIntoPurchaseRuleTable(pluginId);
                    insertDataIntoScreenshotsTable(pluginId,"");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    private static int insertDataIntoPluginTable(String name, String name_slug, String shortDescription, Timestamp created_on, Timestamp modified_on, String file, String vendor, String installing_path,String correlationId,String description) {
        int generatedPluginId = -1;

        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.plugin (name, name_slug, unique_id, short_description,description,latest_version,latest_version_number, digest ,created_on, modified_on, file, vendor,service,installing_path,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setString(1, name);
                preparedStatement.setString(2, name_slug);
                preparedStatement.setString(3, correlationId);
                preparedStatement.setString(4, shortDescription);
                preparedStatement.setString(5, description);
                preparedStatement.setString(6, "v1.0");
                preparedStatement.setInt(7, 1);
                preparedStatement.setObject(8, null);
                preparedStatement.setTimestamp(9, created_on);
                preparedStatement.setTimestamp(10, modified_on);
                preparedStatement.setString(11, file);
                preparedStatement.setString(12, vendor);
                preparedStatement.setString(13, name_slug);
                preparedStatement.setString(14, installing_path);
                preparedStatement.setInt(15, 0);

                int affectedRows = preparedStatement.executeUpdate();

                if (affectedRows > 0) {
                    ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        generatedPluginId = generatedKeys.getInt(1);
                    }

                    String categorySlug = getCategorySlugFromMapping(correlationId);
                    int categoryId = getCategoryIdByName(categorySlug);
                    insertDataIntoPluginCategoriesTable(generatedPluginId, categoryId);

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return generatedPluginId;
    }
    private static void insertDataIntoLicenseTable(int pluginId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.license (plugin_id, description) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setString(2, "No License is required for this item.");
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoMetricsTable(int pluginId, String metricName) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.metrics (plugin_id, metric_name) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setString(2, metricName);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoOwnerTable(int pluginId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.owner (plugin_id, published_by, verified, website, privacy_policy) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setString(2, "Syncloop");
                preparedStatement.setInt(3, 1);
                preparedStatement.setString(4, "https://syncloop.com/");
                preparedStatement.setString(5, "https://www.syncloop.com/privacy-policy.html");
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoPluginTagsTable(int pluginId, int tagId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.plugin_tags (plugin_id, tag_id) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setInt(2, tagId);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoPurchaseRuleTable(int pluginId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.purchase_rule (plugin_id, cost_type) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setString(2, "Free");
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoScreenshotsTable(int pluginId, String screenshot) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.screenshots (plugin_id, screenshot) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setString(2, screenshot);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void insertDataIntoPluginCategoriesTable(List<String> categoryNames) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin.categories (category_name) VALUES (?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                for (String categoryName : categoryNames) {
                    preparedStatement.setString(1, categoryName);
                    preparedStatement.addBatch();
                }

                preparedStatement.executeBatch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<String> retrieveCategoriesFromMarketplace() {
        List<String> categoryNames = new ArrayList<>();

        try (Connection connectionMarketplace = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String queryMarketplace = "SELECT slug FROM categories";
            try (PreparedStatement preparedStatementMarketplace = connectionMarketplace.prepareStatement(queryMarketplace)) {
                ResultSet resultSetMarketplace = preparedStatementMarketplace.executeQuery();

                while (resultSetMarketplace.next()) {
                    String categoryName = resultSetMarketplace.getString("slug");
                    categoryNames.add(categoryName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return categoryNames;
    }

    //-------------------------------------------------------------

    private static void insertDataIntoPluginCategoriesTable(int pluginId, int categoryId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String query = "INSERT INTO plugin_categories (plugin_id, category_id) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, pluginId);
                preparedStatement.setInt(2, categoryId);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
        }
    }

    private static int getCategoryIdByName(String categoryName) {
        int categoryId = -1;

        try (Connection connection = DriverManager.getConnection(JDBC_URL_PLUGIN, USERNAME, PASSWORD)) {
            String checkQuery = "SELECT id FROM categories WHERE category_name = ?";
            try (PreparedStatement checkStatement = connection.prepareStatement(checkQuery)) {
                checkStatement.setString(1, categoryName);
                ResultSet checkResultSet = checkStatement.executeQuery();
                if (checkResultSet.next()) {
                    categoryId = checkResultSet.getInt("id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return categoryId;
    }

    private static String getCategorySlugFromMapping(String correlationId) {
        String categorySlug = "";

        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String query = "SELECT category_slug FROM category_workspace_mapping WHERE workspaceId = ? ";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, correlationId);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    categorySlug = resultSet.getString("category_slug");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return categorySlug;
    }

    private static Timestamp getCurrentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }
}
