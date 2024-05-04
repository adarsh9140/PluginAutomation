package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;

public class PostmanAutomation {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/marketplace";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    private static final String POSTMAN_API_URL = "https://www.postman.com/_api/ws/proxy";
    private static final String API_KEY = "PMAK-660583b6c532470001864af7-8b1f4139f44046f8b5f271e81769a70dd7";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        try {
            createMappingTable();
            String categoryResponse = getCategoryApiResponse();
            saveCategoriesToDatabase(categoryResponse);
            List<String> categorySlugs = getCategorySlugsFromDatabase();

            for (String slug : categorySlugs) {
                try {
                    if (!slug.equals("others")) {
                        processCategoryWithWorkspaceId(slug);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        int totalWorkspaces = 10;
        int sliceSize = 5;

        ChunkProcess workspaceChunkProcess = new AbstractChunkProcessor((long) totalWorkspaces, sliceSize) {
            @Override
            public void code() {
                int offset = getPager().getPageNumber() * getSlice();
                int limit = getSlice();

                retrieveAndSaveWorkspaces(offset, limit);
            }
        };

        ChunkExecutor.execute(workspaceChunkProcess);
    }

    private static List<String> saveWorkspacesToDatabase(String jsonResponse) {
        List<String> entityIds = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS workspaces (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "workspaceId VARCHAR(255)," +
                    "name VARCHAR(255)," +
                    "iconUrl VARCHAR(255)" +
                    ")";
            try (PreparedStatement preparedStatement = connection.prepareStatement(createTableQuery)) {
                preparedStatement.executeUpdate();
            }

            JsonNode root = objectMapper.readTree(jsonResponse);

            JsonNode metaInfo = root.path("meta").path("publisherInfo").path("team");
            Map<String, String> teamProfileUrls = new HashMap<>();
            for (JsonNode teamNode : metaInfo) {
                String teamId = teamNode.path("id").asText();
                String profileURL = teamNode.path("profileURL").asText();
                teamProfileUrls.put(teamId, profileURL);
            }

            JsonNode data = root.path("data");

            for (JsonNode workspaceNode : data) {
                String entityId = workspaceNode.path("entityId").asText();
                String slug = workspaceNode.path("meta").path("slug").asText();
                String teamId = workspaceNode.path("publisherId").asText();

                String iconUrl = teamProfileUrls.get(teamId);

                saveWorkspaceToDatabase(entityId, slug, iconUrl);
                entityIds.add(entityId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return entityIds;
    }
    private static void saveWorkspaceToDatabase(String entityId,String name,String iconUrl) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String insertQuery = "INSERT INTO workspaces (workspaceId,name,iconUrl) VALUES (?, ?,?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, entityId);
                preparedStatement.setString(2, name);
                preparedStatement.setString(3, iconUrl);
                preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
        }
    }

    private static void processCollectionsForWorkspace(String apiUrl, String entityId) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String jsonResponse = response.body();
            saveCollectionsToDatabase(jsonResponse, entityId);
        } catch (Exception e) {
        }
    }

    private static void saveCollectionsToDatabase(String responseData, String entityId) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(responseData);

            try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
                String createTableQuery = "CREATE TABLE IF NOT EXISTS collection (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "correlationId VARCHAR(512) UNIQUE," +
                        "name VARCHAR(255)," +
                        "workspaceId VARCHAR(255)," +
                        "vendor VARCHAR(255)," +
                        "name_slug VARCHAR(255)" +
                        ")";

                try (PreparedStatement createTableStatement = connection.prepareStatement(createTableQuery)) {
                    createTableStatement.executeUpdate();
                }

                JsonNode workspace = root.path("workspace");
                String vendor = workspace.path("name").asText();

                JsonNode collections = workspace.path("collections");

                for (JsonNode collection : collections) {
                    String collectionId = collection.path("uid").asText();
                    String collectionName = collection.path("name").asText();

                    saveCollectionToDatabase(collectionId, collectionName, entityId, vendor);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void saveCollectionToDatabase(String collectionId, String collectionName, String workspaceId, String vendor) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String checkQuery = "SELECT COUNT(*) AS count FROM category_workspace_mapping WHERE workspaceId = ? AND category_slug = ?";
            String nameSlug = generateSlug(collectionName);

            try (PreparedStatement checkStatement = connection.prepareStatement(checkQuery)) {
                checkStatement.setString(1, workspaceId);
                checkStatement.setString(2, nameSlug);

                ResultSet checkResultSet = checkStatement.executeQuery();
                if (checkResultSet.next()) {
                    int count = checkResultSet.getInt("count");
                    if (count > 0) {
                        return; 
                    }
                }
            }

            String insertQuery = "INSERT INTO collection (correlationId, name, workspaceId, vendor, name_slug) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, collectionId);
                preparedStatement.setString(2, collectionName);
                preparedStatement.setString(3, workspaceId);
                preparedStatement.setString(4, vendor);
                preparedStatement.setString(5, nameSlug);
                preparedStatement.executeUpdate();
            }

            if (!isEntityIdMapped(collectionId)) {
                saveMappingRelationToDatabase(collectionId, "others");
            }

        } catch (SQLException e) {
        }
    }

    private static void saveCategoryToDatabase(String name, String description, String slug) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String insertQuery = "INSERT INTO categories (name, description, slug) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, name);
                preparedStatement.setString(2, description);
                preparedStatement.setString(3, slug);

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
        }
    }

    private static void createOthersCategory() {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String insertQuery = "INSERT INTO categories (name, description, slug) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, "Others");
                preparedStatement.setString(2, "Other category");
                preparedStatement.setString(3, "others");

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void saveCategoriesToDatabase(String responseData) {
        try {

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(responseData);

            try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
                String createTableQuery = "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "name VARCHAR(255)," +
                        "description TEXT," +
                        "slug VARCHAR(255)" +
                        ")";

                try (PreparedStatement createTableStatement = connection.prepareStatement(createTableQuery)) {
                    createTableStatement.executeUpdate();
                }

                JsonNode categories = root.path("data");

                for (JsonNode category : categories) {
                    String name = category.path("name").asText();
                    String description = category.path("summary").asText();
                    String slug = category.path("slug").asText();

                    saveCategoryToDatabase(name, description, slug);
                }
                createOthersCategory();
            }
        } catch (Exception e) {
        }
    }

    private static String getCategoryApiResponse() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        String categoryRequest = "{\"service\":\"publishing\",\"method\":\"get\",\"path\":\"/v2/api/category?sort=name&order=asc\"}";

        HttpRequest categoryApiRequest = HttpRequest.newBuilder()
                .uri(URI.create(POSTMAN_API_URL))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(categoryRequest))
                .build();

        HttpResponse<String> categoryApiResponse = httpClient.send(categoryApiRequest, HttpResponse.BodyHandlers.ofString());

        return categoryApiResponse.body();
    }

    private static List<String> getCategorySlugsFromDatabase() {
        List<String> categorySlugs = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String query = "SELECT slug FROM categories";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    String slug = resultSet.getString("slug");
                    categorySlugs.add(slug);
                }
            }
        } catch (SQLException e) {
        }

        return categorySlugs;
    }

    private static void processCategoryWithWorkspaceId(String categorySlug) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String requestBody = String.format("{\"service\":\"publishing\",\"method\":\"get\",\"path\":\"/v2/api/category/%s?limit=10&offset=0&referrer=explore&flattenAPIVersions=true\"}", categorySlug);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(POSTMAN_API_URL))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String jsonResponse = response.body();
            saveCategoryWithWorkspaceId(jsonResponse, categorySlug);
        } catch (Exception e) {
        }
    }

    private static void saveCategoryWithWorkspaceId(String responseData, String categorySlug) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(responseData);

            try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
                JsonNode data = root.path("data");
                JsonNode entities = data.path("entities");
                JsonNode collections = entities.path("collection");

                for (JsonNode collection : collections) {
                    String entityId = collection.path("entityId").asText();
                    String categoryName = data.path("name").asText();

                    saveCategoryWithWorkspaceIdToDatabase(categoryName, categorySlug, entityId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void saveCategoryWithWorkspaceIdToDatabase(String categoryName, String categorySlug, String entityId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String checkQuery = "SELECT COUNT(*) AS count FROM category_workspace_mapping WHERE workspaceId = ? AND category_slug = ?";
            try (PreparedStatement checkStatement = connection.prepareStatement(checkQuery)) {
                checkStatement.setString(1, entityId);
                checkStatement.setString(2, categorySlug);

                ResultSet checkResultSet = checkStatement.executeQuery();
                if (checkResultSet.next()) {
                    int count = checkResultSet.getInt("count");
                    if (count > 0) {
                        return;
                    }
                }
            }

            String insertQuery = "INSERT INTO category_workspace_mapping (workspaceId, category_slug) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, entityId);
                preparedStatement.setString(2, categorySlug);

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private static void createMappingTable() {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS category_workspace_mapping (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "workspaceId VARCHAR(255) UNIQUE," +
                    "category_slug VARCHAR(255)" +
                    ")";
            try (PreparedStatement preparedStatement = connection.prepareStatement(createTableQuery)) {
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private static void retrieveAndSaveWorkspaces(int offset, int limit) {
        List<String> entityIds = new ArrayList<>();

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String requestBody = String.format("{\"service\":\"publishing\",\"method\":\"get\",\"path\":\"/v1/api/networkentity?limit=%d&type=public&referrer=explore&entityType=workspace&flattenAPIVersions=true&category=&sort=watchCount&filter=this_week&offset=%d\"}", limit, offset);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(POSTMAN_API_URL))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String jsonResponse = response.body();
            entityIds.addAll(saveWorkspacesToDatabase(jsonResponse));

            for (String entityId : entityIds) {
                processCollectionsForWorkspace("https://api.getpostman.com/workspaces/" + entityId + "?include=elements", entityId);
            }
        } catch (Exception e) {
        }
    }

    private static boolean isEntityIdMapped(String entityId) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String query = "SELECT COUNT(*) AS count FROM category_workspace_mapping WHERE workspaceId = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, entityId);

                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    int count = resultSet.getInt("count");
                    return count > 0;
                }
            }
        } catch (SQLException e) {
        }

        return false;
    }

    private static void saveMappingRelationToDatabase(String workspaceId, String categorySlug) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD)) {
            String insertQuery = "INSERT INTO category_workspace_mapping (workspaceId, category_slug) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, workspaceId);
                preparedStatement.setString(2, categorySlug);

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
        }
    }

    public static String generateSlug(String input) {
        String nameWithoutNumbers = input.replaceAll("^\\d+\\.", "");

        String slug = nameWithoutNumbers
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-{2,}", "-");

        slug = slug.replaceAll("^-|-$", "");

        slug = Normalizer.normalize(slug, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return slug;
    }

}
