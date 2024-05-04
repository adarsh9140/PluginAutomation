package org.example;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pub.util.ImportSwagger;
import com.eka.middleware.pub.util.postman.*;
import com.eka.middleware.server.MiddlewareServer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.nimbusds.jose.shaded.gson.Gson;
import com.nimbusds.jose.shaded.gson.GsonBuilder;
import com.nimbusds.jose.shaded.gson.JsonSyntaxException;
import com.nimbusds.jose.shaded.gson.stream.JsonReader;
import net.minidev.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eka.middleware.pub.util.ImportSwagger.*;

public class ImportPostmanV2 {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        PostmanCollection postmanCollection = new Gson().fromJson(new FileReader("E:\\Creatio API.postman_collection.json"), PostmanCollection.class);
        createFlowServicesClient("E:\\Nature9_Work\\ekamw-distributions\\integration\\middleware\\tenants\\default", ""
                , "creatio", postmanCollection.getItem(), postmanCollection);
    }

    public static List<String> createFlowServicesClient(String folder, String servicePath, String packageName, List<PostmanItems> item, PostmanCollection postmanCollection) throws Exception {
        ArrayList<String> list = new ArrayList<String>();
        Set<String> existingNames = new HashSet<>();

        for (PostmanItems postmanItems : item) {
            if (postmanItems.getItem() != null && !postmanItems.getItem().isEmpty()) {
                String slug = toServiceSlug(postmanItems.getName());
                list.addAll(createFlowServicesClient(folder, servicePath.replaceAll("-", "_") + File.separator + slug, packageName, postmanItems.getItem(), postmanCollection));
            }
            if (postmanItems.getRequest() != null) {
                String method = postmanItems.getRequest().getMethod();
                if (method != null) {
                    list.add(generateClientLib(folder, servicePath.replaceAll("-", "_"), packageName, postmanItems, postmanCollection, method,Evaluate.EPV,existingNames));
                }
            }
        }
        return list;
    }

    public static String getRequestBody(PostmanItems item) throws IOException {

        String jsonSchema = null;

        PostmanItemRequest request = item.getRequest();
        if (request != null) {
            PostmanRequestItemBody requestBody = request.getBody();
            if (requestBody != null && StringUtils.isNotBlank(requestBody.getRaw())) {
                String raw = requestBody.getRaw();
                jsonSchema = generateJsonSchema(raw, Object.class);
            }
        }
        return jsonSchema;
    }

    public static String generateJsonSchema(String raw, Class<?> inputClass) throws IOException {
        String jsonSchema = null;

        try {
            Gson gson = new GsonBuilder().setLenient().create();
            JsonReader reader = new JsonReader(new StringReader(raw));
            reader.setLenient(true);
            Object map = gson.fromJson(reader, inputClass);
            jsonSchema = getJsonSchema(map);
        } catch (Exception ex) {
            return "{}";
            // throw new IllegalArgumentException("Invalid JSON input", ex);
        }

        return jsonSchema;
    }


    private static String getJsonSchema(JsonNode properties) throws JsonProcessingException {
        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");

        schema.set("properties", properties);

        ObjectMapper jacksonObjectMapper = new ObjectMapper();
        String schemaString = jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        return schemaString;
    }

    private static ObjectNode createProperty(JsonNode jsonData) throws IOException {

        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        ObjectNode propObject = OBJECT_MAPPER.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = jsonData.fields();

        while (fieldsIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldsIterator.next();

            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();
            JsonNodeType fieldType = fieldValue.getNodeType();

            ObjectNode property = processJsonField(fieldValue, fieldType, fieldName);
            if (!property.isEmpty()) {
                propObject.set(fieldName, property);
            }
        }
        return propObject;
    }

    private static ObjectNode processJsonField(JsonNode fieldValue, JsonNodeType fieldType, String fieldName)
            throws IOException {
        ObjectNode property = OBJECT_MAPPER.createObjectNode();

        switch (fieldType) {

            case ARRAY:
                property.put("type", "array");

                if (fieldValue.isEmpty()) {
                    break;
                }
                JsonNodeType typeOfArrayElements = fieldValue.get(0).getNodeType();
                if (typeOfArrayElements.equals(JsonNodeType.OBJECT)) {
                    ObjectNode arraySchema = OBJECT_MAPPER.createObjectNode();
                    arraySchema.put("type", "object");
                    arraySchema.set("properties", createProperty(fieldValue.get(0)));
                    property.set("items", arraySchema);
                } else {
                    property.set("items", processJsonField(fieldValue.get(0), typeOfArrayElements, fieldName));
                }

                break;
            case BOOLEAN:
                property.put("type", "boolean");
                break;

            case NUMBER:
                property.put("type", "number");
                break;

            case OBJECT:
                property.put("type", "object");
                property.set("properties", createProperty(fieldValue));
                break;

            case STRING:
                property.put("type", "string");
                break;
            default:
                break;
        }
        return property;
    }

    public static String getJsonSchema(Object jsonDocument) throws IllegalArgumentException, IOException {
        final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        JsonNode properties = createProperty(OBJECT_MAPPER.convertValue(jsonDocument, JsonNode.class));
        return getJsonSchema(properties);
    }

    public static String addResponses(PostmanItems postmanItems) throws IOException {
        Map<String, Object> map = new HashMap<>();
        if (postmanItems.getResponse() != null) {
            if (postmanItems.getResponse().isEmpty()) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("response", new HashMap<>());
                map.put("*200", responseMap);
            } else {
                for (PostmanItemResponse response : postmanItems.getResponse()) {
                    if (response != null) {
                        int code = response.getCode() < 100 ? 200 : response.getCode();
                        String requestBody = response.getBody();
                        Object object = new HashMap<>();
                        if (StringUtils.isNotBlank(requestBody)) {
                            Gson gson = new Gson();
                            try {
                                object = gson.fromJson(requestBody, Object.class);
                            } catch (JsonSyntaxException e) {
                                // Handle the exception if needed
                            }
                        }
                        Map<String, Object> responseMap = new HashMap<>();

                        if (code >= 400 && code <= 599) {
                            responseMap.put("error", object);
                        } else {
                            responseMap.put("response", object);
                        }
                        map.put("*" + code, responseMap);

                    }
                }
            }
        }
        return getJsonSchema(map);
    }

    private static Set<String> extractKeys(String input) {
        Set<String> keys = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{\\{(.*?)}}");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            keys.add(matcher.group(1).trim());
        }

        return keys;
    }

    private static Map<String, Object> createDocumentParam(String prefix, String key, String variableName, Evaluate evaluate, String typePath, Boolean IsQueryParameter, String fieldDescription) {
        Map<String, Object> documentParam = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> assignList = new HashMap<>();
        List<Map<String, Object>> assignListItems = new ArrayList<>();

        documentParam.put("text", key);
        documentParam.put("type", "string");

        if(IsQueryParameter && fieldDescription != null){
            data.put("fieldDescription", fieldDescription);
        }
        data.put("isRequiredField", true);
        documentParam.put("data", data);

        Map<String, Object> createVariable = new HashMap<>();
        createVariable.put("path", prefix + key);
        if (evaluate != null) {
            createVariable.put("evaluate", evaluate.name());
        }
        createVariable.put("typePath", typePath);
        createVariable.put("value", variableName);

        assignListItems.add(createVariable);
        assignList.put("assignList", assignListItems);
        documentParam.put("data", assignList);

        return documentParam;
    }

    private static String extractVariableName(String pathSegment) {
        Pattern pattern = Pattern.compile("\\{\\{(.*?)(?::.*?)?}}");
        Matcher matcher = pattern.matcher(pathSegment);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return pathSegment;
    }

    private static UrlVariable findMatchingVariable(List<UrlVariable> variables, String variableName) {
        for (UrlVariable variable : variables) {
            if (variable.getKey().equals(variableName)) {
                return variable;
            }
        }
        return null;
    }

    private static void saveFlow(String filePath, String json) {

        File file = new File(filePath);
        boolean mkdirs = file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            IOUtils.write(json.getBytes(), fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addResponseBody(List<Object> output, PostmanItems rbpostmanItems, boolean isClient)
            throws IOException {

        String responseBody = addResponses(rbpostmanItems);
        if (StringUtils.isNotBlank(responseBody)) {
            GetBody.NodeProperties map = new Gson().fromJson(responseBody, GetBody.NodeProperties.class);
            output.addAll(GetBody.getJstreeFromSchema(map));
        }
    }

    private static String toServiceSlug(String str) {
        if (null==str){
            return "_";
        }
        str = str.replaceAll("^\\d+", "").trim().replace("-", "_");
        str  =   str.toLowerCase().replaceAll("[^a-z0-9\\-]+", "_").replaceAll("^-|-$", "");
        str = str.replaceAll("(?<=\\s|^)\\d+", "");

        Set<String> reservedWords = new HashSet<>(Arrays.asList(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally",
                "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
                "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true",
                "try", "void", "volatile", "while"
        ));

        if (reservedWords.contains(str.toLowerCase())) {
            return "_" + str;
        }

        if (StringUtils.isBlank(str)){
            return "_";
        }
        return str;
    }

    private static String generateUniqueName(String baseName,Set<String> existingNames) {

        String modifiedName = baseName;

        while (!existingNames.add(modifiedName)) {
            int counter = 1;
            String attempt = baseName + "_" + counter;

            while (existingNames.contains(attempt)) {
                counter++;
                attempt = baseName + "_" + counter;
            }
            modifiedName = attempt;
        }
        return modifiedName;
    }

    private static void addClientRequestBody(List<Object> input,Boolean isServer ,List<Object> inputs, PostmanItems rbpostmanItems, boolean isClient,String dependencyFolderPath)
            throws IOException {

        String payloadParam = isClient ? "payload" : "*payload";
        String mode = rbpostmanItems.getRequest().getBody() != null ? rbpostmanItems.getRequest().getBody().getMode() : null;
        if ("raw".equalsIgnoreCase(mode)) {
            String requestBody = getRequestBody(rbpostmanItems);
            if (StringUtils.isNotBlank(requestBody)) {
                GetBody.NodeProperties map = new Gson().fromJson(requestBody, GetBody.NodeProperties.class);
                List<Object> objects = Lists.newArrayList(GetBody.getJstreeFromSchema(map));
                Map<String, Object> objectMap = createDocument(payloadParam, objects, "description");
                inputs.add(objectMap);
            }
        }if ("formdata".equalsIgnoreCase(mode) ) {

            List<FormData> dataList = rbpostmanItems.getRequest().getBody().getFormdata();

            if (dataList != null && !dataList.isEmpty()) {
                List<Object> fileVariables = new ArrayList<>();

                for (FormData dataItem : dataList) {
                    if ("file".equalsIgnoreCase(dataItem.getType())) {
                        if (dataItem.getKey() != null && !dataItem.getKey().isEmpty()) {
                            Map<String, Object> fileVariable = new HashMap<>();
                            fileVariable.put("text", dataItem.getKey());
                            fileVariable.put("type", "javaObject");
                            fileVariables.add(fileVariable);
                        }
                    } else {
                        Map<String, Object> fileVariable = new HashMap<>();
                       /* fileVariable.put("text", dataItem.getKey());
                        fileVariable.put("type", "string");
                        fileVariables.add(fileVariable);*/
                        if (isClient) {
                            if (dataItem.getValue().startsWith("{{") && dataItem.getValue().endsWith("}}")) {
                                String variableName = dataItem.getValue().substring(2, dataItem.getValue().length() - 2);
                                //createVariables(intiMapStep, "formData/" + dataItem.getKey(), "#{" + variableName + "}",  Evaluate.EPV, "document/string");
                                fileVariable = createDocumentParam("formData/" , dataItem.getKey(), "#{" + variableName + "}",  Evaluate.EPV, "document/string", false, null);
                                fileVariables.add(fileVariable);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));
                            } else {

                                //createVariables(intiMapStep, "formData/" + dataItem.getKey(), dataItem.getValue(), null, "document/string");
                                fileVariable = createDocumentParam("formData/" , dataItem.getKey(), dataItem.getValue(), null, "document/string", false, null);
                                fileVariables.add(fileVariable);
                            }
                        }
                    }
                }

                if (!fileVariables.isEmpty()) {
                    Map<String, Object> document = createDocument("formData", fileVariables, null);
                    inputs.add(document);
                }
            }
        }
        else if ("urlencoded".equalsIgnoreCase(mode)) {
            List<UrlEncodedParameter> dataList = rbpostmanItems.getRequest().getBody().getUrlencoded();

            if (dataList != null && !dataList.isEmpty()) {
                List<Object> dataVariables = new ArrayList<>();

                for (UrlEncodedParameter dataItem : dataList) {
                    Map<String, Object> fileVariable = Maps.newHashMap();
                    /*fileVariable.put("text", dataItem.getKey());
                    fileVariable.put("type", "string");
                    dataVariables.add(fileVariable);*/
                    // createVariables(intiMapStep, "urlencoded/" + dataItem.getKey(), dataItem.getValue(), null, "document/string");
                    if (isClient) {
                        if (dataItem.getValue().startsWith("{{") && dataItem.getValue().endsWith("}}")) {
                            String variableName = dataItem.getValue().substring(2, dataItem.getValue().length() - 2);
                            //createVariables(intiMapStep, "urlencoded/" + dataItem.getKey(),"#{" + variableName + "}",  Evaluate.EPV, "document/string");
                            fileVariable = createDocumentParam("urlencoded/" , dataItem.getKey(),"#{" + variableName + "}",  Evaluate.EPV, "document/string", false, null);
                            dataVariables.add(fileVariable);

                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));
                        } else {
                            //createVariables(intiMapStep, "urlencoded/" + dataItem.getKey(), dataItem.getValue(), null, "document/string");
                            fileVariable = createDocumentParam("urlencoded/" , dataItem.getKey(), dataItem.getValue(), null, "document/string", false, null);
                            dataVariables.add(fileVariable);

                        }
                    }
                }
                Map<String, Object> document = createDocument(mode, dataVariables, null);
                inputs.add(document);
            }
        }
        else if ("file".equalsIgnoreCase(mode)) {
            Binary fileData = rbpostmanItems.getRequest().getBody().getFile();
            List<Object> dataVariables = new ArrayList<>();
            if (fileData != null && fileData.getFile() != null) {
                Map<String, Object> fileVariable = Maps.newHashMap();
                fileVariable.put("text", "file");
                fileVariable.put("type", "byte");
                dataVariables.add(fileVariable);
                Map<String, Object> document = createDocument("file", dataVariables, null);
                inputs.add(document);
            }
        }
    }

    private static List<Object> getClientRequestHeaders(String dependencyFolderPath, List<PostmanRequestHeaders> headers, List<Object> inputs, boolean isServer, Evaluate evaluateFrom) {

        if (headers == null) {
            return Lists.newArrayList();
        }
        List<Object> document = new ArrayList<>();
        for (PostmanRequestHeaders header : headers) {
            if ("true".equals(header.getDisabled())) {
                continue;
            }


            if (StringUtils.isNotBlank(header.getValue()) && !isServer) {
                if (header.getValue().startsWith("{{") && header.getValue().endsWith("}}")) {
                    String variableName = header.getValue().substring(2, header.getValue().length() - 2);
                    Map<String, Object> documentParam = new HashMap<>();
                    documentParam = createDocumentParam("requestHeaders/", header.getKey(), "#{" + variableName + "}", Evaluate.EPV, "document/string", false, null);
                    boolean add = document.add(documentParam);
                    createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));
                } else {
                        /*createOverridingInputVariables(inputs, "requestHeaders/" + header.getKey(), postmanToSyncloopProps(header.getValue()),
                                evaluateFrom(header.getValue(), evaluateFrom), "document/string");*/
                    Map<String, Object> documentParam = new HashMap<>();
                    documentParam = createDocumentParam("requestHeaders/", header.getKey(), postmanToSyncloopProps(header.getValue()), evaluateFrom(header.getValue(), evaluateFrom), "document/string", false, null);
                    boolean add = document.add(documentParam);
                    if (header.getValue().contains("{{") || header.getValue().contains("}}") ) {
                        Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                        Matcher matcher = pattern.matcher(header.getValue());
                        if (matcher.find()) {
                            String variableName = matcher.group(1);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                        }
                    }
                }
            }
        }
        return document;
    }

    private static List<Object> getClientRequestPathParameters(List<String> path, List<UrlVariable> variables, List<Object> input,Boolean isServer ,String dependencyFolderPath) {
        if (path == null || variables == null) {
            return new ArrayList<>();
        }

        List<Object> document = new ArrayList<>();
        for (String pathSegment : path) {
            if (pathSegment.startsWith(":")) {
                String variableName = pathSegment.substring(1);
                UrlVariable matchingVariable = findMatchingVariable(variables, variableName);
                if (matchingVariable != null) {
                    Map<String, Object> documentParam = new HashMap<>();
                    Map<String, Object> data = new HashMap<>();

                    Set<String> keys = null;
                    if (matchingVariable.getValue() != null) {
                        keys = extractKeys(matchingVariable.getValue());
                    }

                    if (keys != null) {
                        for (String key : keys) {
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(key, ""));
                        }
                    }

                    if (StringUtils.isNotBlank(matchingVariable.getValue()) && !isServer && input != null) {
                        String variableValue = matchingVariable.getValue();
                        if (variableValue.startsWith("{{") && variableValue.endsWith("}}")) {
                            String formatValue = variableValue.substring(2, variableValue.length() - 2);
                            /*createVariables(firstMap, "pathParameters/" + matchingVariable.getKey(), "#{" + formatValue + "}",
                                    Evaluate.EPV, "document/string");*/
                            documentParam = createDocumentParam("pathParameters/" , matchingVariable.getKey().replace("{{", "").replace("}}", ""), "#{" + formatValue + "}",
                                    Evaluate.EPV, "document/string", false, null);
                            document.add(documentParam);

                            //createFolderStructure(dependencyFolderPath, "package.properties", Map.of(formatValue, ""));
                        } else {
                            /*createVariables(firstMap, "pathParameters/" + matchingVariable.getKey(), postmanToSyncloopProps(variableValue),
                                    evaluateFrom(variableValue, null), "document/string");*/
                            documentParam = createDocumentParam("pathParameters/" , matchingVariable.getKey().replace("{{", "").replace("}}", ""), postmanToSyncloopProps(variableValue),
                                    evaluateFrom(variableValue, null), "document/string", false, null);
                            document.add(documentParam);
                        }
                    }else{
                        documentParam.put("text", variableName.replace("{{", "").replace("}}", ""));
                        documentParam.put("type", "string");

                        data.put("isRequiredField", true);
                        data.put("value", matchingVariable.getValue());

                        documentParam.put("data", data);
                        document.add(documentParam);
                    }
                }
            }
            if (pathSegment.startsWith("{{")) {

                Set<String> keys = null;
                if (pathSegment!= null) {
                    keys = extractKeys(pathSegment);
                }

                if (keys != null) {
                    for (String key : keys) {
                        createFolderStructure(dependencyFolderPath, "package.properties", Map.of(key, ""));
                    }
                }

                //String variableName = pathSegment.substring(2, pathSegment.length() - 2);
                String variableName = extractVariableName(pathSegment);

                Map<String, Object> documentParam = new HashMap<>();
                /*Map<String, Object> data = new HashMap<>();

                documentParam.put("text", variableName);
                documentParam.put("type", "string");

                documentParam.put("data", data);
                document.add(documentParam);*/

                if (!isServer && input != null) {
                    /*createVariables(firstMap,  "pathParameters/" + variableName, "#{" + variableName + "}",
                            Evaluate.EPV, "document/string");*/
                    documentParam = createDocumentParam("pathParameters/" , variableName, "#{" + variableName + "}",
                            Evaluate.EPV, "document/string", false, null);
                    document.add(documentParam);
                    //createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));
                }
            }
        }

        return document;
    }


    private static String generateClientLib(String folder, String servicePath, String packageName, PostmanItems postmanItems, PostmanCollection postmanCollection, String method, Evaluate evaluateFrom, Set<String> existingNames) throws Exception {

        String apiName = toServiceSlug(postmanItems.getName().replace("-", "_"));
        String baseName = generateUniqueName(apiName, existingNames);

        String filePath = folder + File.separator + "packages" + File.separator + packageName
                + File.separator + "client" + File.separator + servicePath + File.separator
                + baseName + ".api";

        String dependencyFolderPath = folder + File.separator + "packages" + File.separator + packageName
                + File.separator + "dependency/config";

        File file = new File(filePath);
        FlowService flowService = new FlowService("", "", Sets.newHashSet("consumers"), Sets.newHashSet("developers")); List<Object> inputs = flowService.getInput();
        List<Object> outputs = flowService.getOutput();
        List<Object> flowSteps = flowService.getFlowSteps();

        Map<String, Object> intiMapStep = createMapStep(flowSteps, "SettingUp BasePath Parameter");

        List<Object> headers = null;
        List<Object> query = null;
        List<Object> path = null;

        boolean requestHeadersAdded = false;
        boolean requestQueryParamsAdded = false;


        PostmanItemRequest request = postmanItems.getRequest();
        List<String> host = null;

        String protocol = null;
        String basePath = null;

        if (request != null && request.getUrl() != null) {
            host = request.getUrl().getHost();
            protocol = request.getUrl().getProtocol();
        }

        if (protocol != null && host != null) {
            basePath = protocol + "://";
            basePath += String.join(".", host) + "/";

        }
        boolean containsVariables = false;
        if (basePath != null && (basePath.contains("{{") && basePath.contains("}}"))) {
            containsVariables = true;
        } else if (host != null && host.stream().anyMatch(h -> h != null && (h.contains("{{") && h.contains("}}")))) {
            containsVariables = true;
        }

        if (containsVariables) {
            createFolderStructure(dependencyFolderPath, "package.properties", Map.of("basePath", ""));
            createVariables(intiMapStep, "basePath", "#{basePath}", Evaluate.EPV, "string");
        } else {
            if (basePath != null) {
                createFolderStructure(dependencyFolderPath, "package.properties", Map.of("basePath", basePath));
                createVariables(intiMapStep, "basePath", basePath, null, "string");
            }
        }

        intiMapStep = createMapStep(flowSteps, "Initializing parameters For HTTP request");

        addClientRequestBody(inputs,false,inputs, postmanItems, true,dependencyFolderPath);
        if (postmanItems.getRequest() != null) {
            headers = getClientRequestHeaders(dependencyFolderPath,postmanItems.getRequest().getHeader(), inputs, false, evaluateFrom);
            if (postmanItems.getRequest().getUrl() != null) {
                query = getClientRequestQuery(dependencyFolderPath,postmanItems.getRequest().getUrl().getQuery(), inputs, false, evaluateFrom);
                // path = getRequestPathParameters(postmanItems.getRequest().getUrl().getPath());
                List<UrlVariable> urlVariable=  postmanItems.getRequest().getUrl().getVariable();
                path = getClientRequestPathParameters(postmanItems.getRequest().getUrl().getPath(),urlVariable,inputs,false,dependencyFolderPath);
            }

            if (headers != null && headers.size() > 0) {
                inputs.add(createDocument("requestHeaders", headers, ""));
                requestHeadersAdded = true;
            }
            if (path != null && path.size() > 0) {
                inputs.add(createDocument("pathParameters", path, ""));
            }
            if (query != null && query.size() > 0) {
                inputs.add(createDocument("queryParameters", query, ""));
                requestQueryParamsAdded = true;
            }
        }

        if (postmanItems.getRequest() != null && postmanItems.getRequest().getBody() != null &&
                postmanItems.getRequest().getBody().getMode() != null && postmanItems.getRequest().getBody().getMode().equals("raw")) {

            //createVariables(intiMapStep, "requestHeaders/Content-Type", "application/json", null, "document/string");
            createOverridingInputVariables(inputs, "requestHeaders/Content-Type", "application/json", null, "document/string");
        }
        String alias = "";
        if (request != null && request.getUrl() != null) {
            alias = StringUtils.join(request.getUrl().getPath(), "/");
        }

        String updatedAlias = "";
        if (StringUtils.isNotBlank(alias)) {
            updatedAlias = replacePathParametersForClient(postmanToSyncloopProps(alias),intiMapStep,dependencyFolderPath);
        }

        createVariables(intiMapStep, "method", method, null, "string");

        String urlPath = basePath + postmanToSyncloopProps(updatedAlias, "pathParameters");
        boolean pathContainsVariables = urlPath != null && (urlPath.contains("{") || urlPath.contains("}"));


        if (basePath != null && !pathContainsVariables && !containsVariables) {
            createVariables(intiMapStep, "url", basePath + postmanToSyncloopProps(updatedAlias, "pathParameters"), null, "string");
        } else if (basePath != null && !pathContainsVariables && containsVariables) {
            createVariables(intiMapStep, "url", "#{basePath}" + postmanToSyncloopProps(updatedAlias, "pathParameters"), null, "string");
        } else if (basePath != null && pathContainsVariables) {
            if (containsVariables) {
                createVariables(intiMapStep, "url", "#{basePath}" + postmanToSyncloopProps(updatedAlias, "pathParameters"), Evaluate.EEV, "string");
            } else {
                createVariables(intiMapStep, "url", basePath + postmanToSyncloopProps(updatedAlias, "pathParameters"), Evaluate.EEV, "string");
            }
        } else if (basePath == null) {
            createVariables(intiMapStep, "url", "#{basePath}" + postmanToSyncloopProps(updatedAlias, "pathParameters"), Evaluate.EEV, "string");
        }
        createVariables(intiMapStep, "sendBlankParams", "true", null, "boolean");

        if (postmanItems.getRequest() != null && postmanItems.getRequest().getBody() != null &&
                postmanItems.getRequest().getBody().getMode() != null && postmanItems.getRequest().getBody().getMode().equals("raw")) {

            Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");

            invokeStepToJson.put("fqn", "packages/middleware/pub/json/toString");

            createPreInvokeMapping(invokeStepToJson, "copy", "document", "/payload", "documentList", "/root");
            createPostInvokeMapping(invokeStepToJson, "copy", "string", "/jsonString", "string", "/body");
        }

        if (null != postmanItems.getRequest() && null != postmanItems.getRequest().getBody() && postmanItems.getRequest().getBody().getMode().equals("file")) {
            Map<String, Object> inputStream = Maps.newHashMap();
            inputStream.put("text", "inputStream");
            inputStream.put("type", "javaObject");
            inputs.add(inputStream);
        }

        PostmanItemAuth auth = postmanItems.getRequest() != null ? postmanItems.getRequest().getAuth() : null;
        PostmanCollection postmanCollectionInstance = new PostmanCollection();
        PostmanItemAuth auth2 = postmanCollection.getAuth();

        if (auth != null || auth2 != null) {
            if (auth == null && auth2 != null) {
                auth = auth2;
            }
            List<Object> basicAuth = Lists.newArrayList();
            Map<String, Object> assignDoc = Maps.newHashMap();

            if (auth.getType() != null) {
                if (postmanItems.getRequest().getAuth() != null && "basic".equalsIgnoreCase(auth.getType())){
                    List<Basic> basicList = postmanItems.getRequest().getAuth().getBasic();
                    if (basicList != null) {
                        for (Basic basic : basicList) {
                            String key = basic.getKey();
                            String value = basic.getValue();

                            if (value != null && value.startsWith("{{") && value.endsWith("}}")) {
                                String variableName = value.substring(2, value.length() - 2);
                                assignDoc = createDocumentParam("basicAuth/" , key, "#{" + variableName + "}", Evaluate.EPV, "document/string", false, null);
                                basicAuth.add(assignDoc);

                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put(variableName, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            } else {
                                if (value != null) {
                                    assignDoc = createDocumentParam("basicAuth/" , key, value, null, "document/string", false, null);
                                    basicAuth.add(assignDoc);
                                }
                            }
                        }
                    }else {
                        Map<String, String> inputVar = Maps.newHashMap();
                        inputVar.put("text", "username");
                        inputVar.put("type", "string");
                        basicAuth.add(inputVar);

                        inputVar = Maps.newHashMap();
                        inputVar.put("text", "password");
                        inputVar.put("type", "string");
                        basicAuth.add(inputVar);
                    }

                    Map<String, Object> inputAuthVar = Maps.newHashMap();
                    inputAuthVar.put("text", "basicAuth");
                    inputAuthVar.put("type", "document");
                    inputAuthVar.put("children", basicAuth);
                    inputs.add(inputAuthVar);

                    Map<String, Object> invokeStepToJson1 = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");
                    invokeStepToJson1.put("fqn", "packages/Wrapper/Authorization/Basic");
                    createPreInvokeMapping(invokeStepToJson1, "copy", "document/string", "/basicAuth/username", "string", "/username");
                    createPreInvokeMapping(invokeStepToJson1, "copy", "document/string", "/basicAuth/password", "string", "/password");
                    createPostInvokeMapping(invokeStepToJson1, "copy", "string", "/Authorization", "document/string", "/requestHeaders/Authorization");
                }

                else if (postmanItems.getRequest().getAuth() != null && "bearer".equalsIgnoreCase(auth.getType())) {

                    String token = null;
                    Map<String, Object> assignDocForBearer = new HashMap<>();
                    if (postmanItems.getRequest().getAuth().getBearer() != null && !postmanItems.getRequest().getAuth().getBearer().isEmpty()) {
                        token = postmanItems.getRequest().getAuth().getBearer().get(0).getValue();
                    }

                    if (token != null && token.startsWith("{{") && token.endsWith("}}")) {
                        String variableName = token.substring(2, token.length() - 2);
                        //createVariables(intiMapStep, "requestHeaders/Authorization", "Bearer #{" + variableName + "}", Evaluate.EPV, "document/string");
                        assignDocForBearer = createDocumentParam( "requestHeaders/" , "Authorization", "Bearer #{" + variableName + "}", Evaluate.EPV, "document/string", false, null);
                        headers.add(assignDocForBearer);
                        Map<String, String> propertiesMap = new HashMap<>();
                        propertiesMap.put(variableName, "");
                        createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                    } else {
                        if (token != null) {
                            //createVariables(intiMapStep, "requestHeaders/Authorization", "Bearer " + postmanToSyncloopProps(token), evaluateFrom(token, evaluateFrom), "document/string");
                            assignDocForBearer = createDocumentParam("requestHeaders/", "Authorization", "Bearer " + postmanToSyncloopProps(token), evaluateFrom(token, evaluateFrom), "document/string", false, null);
                            headers.add(assignDocForBearer);
                        }else {
                            Map<String, Object> inputVar = new HashMap<>();
                            inputVar.put("text", "Authorization");
                            inputVar.put("type", "string");
                            headers.add(inputVar);
                        }
                    }
                    /*Map<String, Object> inputVar = new HashMap<>();
                    inputVar.put("text", "Authorization");
                    inputVar.put("type", "string");
                    headers.add(inputVar);*/
                    if (!requestHeadersAdded) {
                        inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                        requestHeadersAdded = true;
                    }

                }
                else if (postmanItems.getRequest().getAuth() != null && "apikey".equalsIgnoreCase(auth.getType())){
                    String key = null;
                    String value = null;
                    String where = null;
                    List<ApiKey> apiKeyList = postmanItems.getRequest().getAuth().getApikey();

                    Map<String, Object> inputVar = new HashMap<>();

                    for (ApiKey apiKey : apiKeyList) {
                        if ("key".equalsIgnoreCase(apiKey.getKey())) {
                            key = apiKey.getValue();
                        } else if ("value".equalsIgnoreCase(apiKey.getKey())) {
                            value = apiKey.getValue();
                        } else if ("in".equalsIgnoreCase(apiKey.getKey())) {
                            where = apiKey.getValue();
                        }
                    }
                    boolean valueFromVar = false;
                    if ("header".equalsIgnoreCase(where)) {
                        if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                            String variableName = key.substring(2, key.length() - 2);
                            //createVariables(intiMapStep, "requestHeaders/" + variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            if(!value.contains("{{") || !value.contains("}}")){
                                inputVar = createDocumentParam("requestHeaders/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                headers.add(inputVar);
                            }else{
                                inputVar = createDocumentParam("requestHeaders/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                headers.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                propertiesMap.put(adjustedPropertyValue, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                            valueFromVar = true;
                        }
                        else if (key != null && value == null && StringUtils.isNotBlank(key) ) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("requestHeaders/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestHeadersAdded) {
                                inputs.add(createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                            headers.add(inputVariable);
                        }
                        else {
                            if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                //createVariables(intiMapStep,"requestHeaders/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                                inputVar = createDocumentParam("requestHeaders/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                headers.add(inputVar);
                                if (value.contains("{{") || value.contains("}}") ) {
                                    Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                    Matcher matcher = pattern.matcher(value);
                                    if (matcher.find()) {
                                        String variableName = matcher.group(1);
                                        createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                    }
                                }
                            }else{
                                if(key != null && StringUtils.isNotBlank(key)) {
                                    inputVar.put("text", key);
                                    inputVar.put("type", "string");
                                    headers.add(inputVar);
                                }
                            }
                        }
                        /*Map<String, Object> inputVar = new HashMap<>();
                        inputVar.put("text", key);
                        inputVar.put("type", "string");
                        headers.add(inputVar);*/

                        if (!requestHeadersAdded) {
                            inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                            requestHeadersAdded = true;
                        }
                    }

                    if ("query".equalsIgnoreCase(where)) {
                        if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                            String variableName = key.substring(2, key.length() - 2);
                            if(!value.contains("{{") || !value.contains("}}")){
                                inputVar = createDocumentParam("queryParameters/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                query.add(inputVar);
                            }else{
                                inputVar = createDocumentParam("queryParameters/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                query.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                propertiesMap.put(adjustedPropertyValue, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                            valueFromVar = true;
                        }
                        else if (key != null && value == null && StringUtils.isNotBlank(key) ) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("queryParameters/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestQueryParamsAdded) {
                                inputs.add(createDocument("queryParameters", query, ""));
                                requestQueryParamsAdded = true;
                            }
                            query.add(inputVariable);
                        }
                        else {
                            if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                inputVar = createDocumentParam("queryParameters/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                query.add(inputVar);
                                if (value.contains("{{") || value.contains("}}") ) {
                                    Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                    Matcher matcher = pattern.matcher(value);
                                    if (matcher.find()) {
                                        String variableName = matcher.group(1);
                                        createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                    }
                                }
                            }else{
                                if(key != null && StringUtils.isNotBlank(key)) {
                                    inputVar.put("text", key);
                                    inputVar.put("type", "string");
                                    query.add(inputVar);
                                }
                            }
                        }

                        if (!requestQueryParamsAdded) {
                            inputs.add(ImportSwagger.createDocument("queryParameters", query, ""));
                            requestQueryParamsAdded = true;
                        }
                    }

                    /*if ("query".equalsIgnoreCase(where)) {
                        Map<String, Object> inputVarForQuery = new HashMap<>();

                        if (!valueFromVar && key != null) {
                            //createVariables(intiMapStep, "queryParameters/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                            inputVarForQuery = createDocumentParam("queryParameters/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                            query.add(inputVarForQuery);

                        }else{
                            inputVarForQuery.put("text", key);
                            inputVarForQuery.put("type", "string");
                            if (query != null) {
                                query.add(inputVarForQuery);
                            }
                        }

                        if (!requestQueryParamsAdded) {
                            inputs.add(ImportSwagger.createDocument("queryParameters", headers, ""));
                            requestQueryParamsAdded = true;
                        }

                    }*/

                    if (key != null && StringUtils.isNotBlank(key)) {
                        if (value != null && where == null) {
                            if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                                String variableName = key.substring(2, key.length() - 2);
                                //createVariables(intiMapStep, "requestHeaders/" + variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                if (!value.contains("{{") || !value.contains("}}")) {
                                    inputVar = createDocumentParam("requestHeaders/", variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                    headers.add(inputVar);
                                } else {
                                    inputVar = createDocumentParam("requestHeaders/", variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                    headers.add(inputVar);
                                    Map<String, String> propertiesMap = new HashMap<>();
                                    String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                    propertiesMap.put(adjustedPropertyValue, "");
                                    createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                                }
                                valueFromVar = true;
                            } else {
                                if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                    //createVariables(intiMapStep,"requestHeaders/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                                    inputVar = createDocumentParam("requestHeaders/", key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                    if (value.contains("{{") || value.contains("}}")) {
                                        Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                        Matcher matcher = pattern.matcher(value);
                                        if (matcher.find()) {
                                            String variableName = matcher.group(1);
                                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                        }
                                    }
                                    headers.add(inputVar);
                                } else {
                                    if(key != null && StringUtils.isNotBlank(key)){
                                        inputVar.put("text", key);
                                        inputVar.put("type", "string");
                                        headers.add(inputVar);
                                    }
                                }
                            }

                        /*Map<String, Object> inputVar = new HashMap<>();
                        inputVar.put("text", key);
                        inputVar.put("type", "string");
                        headers.add(inputVar);*/

                            if (!requestHeadersAdded) {
                                inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                        }
                        else if (value == null && where == null) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("requestHeaders/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestHeadersAdded) {
                                inputs.add(createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                            headers.add(inputVariable);
                        }
                    }


                }
                else if (postmanItems.getRequest().getAuth() != null && "awsv4".equalsIgnoreCase(auth.getType())) {
                    List<Object> awsAuth = Lists.newArrayList();

                    String service = null;
                    String region = null;
                    String secretKey = null;
                    String accessKey = null;

                    List<AwsSignature> signatureList = postmanItems.getRequest().getAuth().getAwsv4();
                    for (AwsSignature aws : signatureList) {
                        if ("service".equalsIgnoreCase(aws.getKey())) {
                            service = aws.getValue();
                        } else if ("region".equalsIgnoreCase(aws.getKey())) {
                            region = aws.getValue();
                        } else if ("secretKey".equalsIgnoreCase(aws.getKey())) {
                            secretKey = aws.getValue();
                        } else if ("accessKey".equalsIgnoreCase(aws.getKey())) {
                            accessKey = aws.getValue();
                        }
                    }

                    Map<String, Object> serviceMap = Maps.newHashMap();


                    if (service != null) {
                        if (service.startsWith("{{") && service.endsWith("}}")) {

                            //createVariables(intiMapStep, "awsAuth/service", service.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            serviceMap = createDocumentParam("awsAuth/", "service", service.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false ,null);
                            awsAuth.add(serviceMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(service.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/service", postmanToSyncloopProps(service), null, "document/string");
                            serviceMap = createDocumentParam("awsAuth/", "service", postmanToSyncloopProps(service), null, "document/string", false, null);
                            awsAuth.add(serviceMap);
                        }
                    }else{
                        serviceMap.put("text", "service");
                        serviceMap.put("type", "string");
                        awsAuth.add(serviceMap);
                    }

                    Map<String, Object> regionMap = Maps.newHashMap();
                    if (region != null) {
                        if (region.startsWith("{{") && region.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/region", region.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            regionMap = createDocumentParam("awsAuth/", "region", region.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            awsAuth.add(regionMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(region.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/region", postmanToSyncloopProps(region), null, "document/string");
                            regionMap = createDocumentParam("awsAuth/", "region", postmanToSyncloopProps(region), null, "document/string", false, null);
                            awsAuth.add(regionMap);
                        }
                    }else{
                        regionMap.put("text", "region");
                        regionMap.put("type", "string");
                        awsAuth.add(regionMap);
                    }

                    Map<String, Object> secretMap = Maps.newHashMap();
                    if (secretKey != null) {
                        if (secretKey.startsWith("{{") && secretKey.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/secretKey", secretKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            secretMap = createDocumentParam("awsAuth/","secretKey", secretKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(secretKey.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                            awsAuth.add(secretMap);
                        } else {
                            //createVariables(intiMapStep, "awsAuth/secretKey", postmanToSyncloopProps(secretKey), null, "document/string");
                            secretMap = createDocumentParam("awsAuth/","secretKey", postmanToSyncloopProps(secretKey), null, "document/string", false, null);
                            awsAuth.add(secretMap);

                        }
                    }else{
                        secretMap.put("text", "secretKey");
                        secretMap.put("type", "string");
                        awsAuth.add(secretMap);
                    }

                    Map<String, Object> accessKeyMap = Maps.newHashMap();
                    if (accessKey != null) {
                        if (accessKey.startsWith("{{") && accessKey.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/accessKey", accessKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            accessKeyMap = createDocumentParam("awsAuth/", "accessKey", accessKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            awsAuth.add(accessKeyMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(accessKey.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/accessKey", postmanToSyncloopProps(accessKey), null, "document/string");
                            accessKeyMap = createDocumentParam("awsAuth/", "accessKey", postmanToSyncloopProps(accessKey), null, "document/string", false, null);
                            awsAuth.add(accessKeyMap);
                        }
                    }else{
                        accessKeyMap.put("text", "accessKey");
                        accessKeyMap.put("type", "string");
                        awsAuth.add(accessKeyMap);
                    }


                    Map<String, Object> inputVar = Maps.newHashMap();
                    inputVar.put("text", "awsAuth");
                    inputVar.put("type", "document");
                    inputVar.put("children", awsAuth);

                    inputs.add(inputVar);

                    Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");

                    invokeStepToJson.put("fqn", "packages/Wrapper/AWS/credential/CreateSigner");

                    if (!"GET".equalsIgnoreCase(method)) {
                        createPreInvokeMapping(invokeStepToJson, "copy", "string", "/body", "string", "/payload");
                    }

                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/service", "string", "/service");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/region", "string", "/region");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/secretKey", "string", "/secret");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/accessKey", "string", "/accessKey");

                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/url", "string", "/url");
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/method", "string", "/method");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document", "/queryParameters", "document", "/queryParameters");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document", "/requestHeaders", "document", "/headers");
                    createPostInvokeMapping(invokeStepToJson, "copy", "document", "/headers", "document", "/requestHeaders");
                }
                else if (postmanItems.getRequest().getAuth() != null && "jwt".equalsIgnoreCase(auth.getType())) {

                    List<JwtBearer> jwtList = postmanItems.getRequest().getAuth().getJwt();
                    List<Object> jwtAuth = Lists.newArrayList();

                    boolean payloadConditionMet = false;
                    boolean algorithmConditionMet = false;
                    boolean secretConditionMet = false;
                    boolean isSecretBase64EncodedConditionMet = false;
                    boolean addTokenToConditionMet = false;
                    boolean headerPrefixConditionMet = false;
                    boolean queryParamKeyConditionMet = false;
                    boolean headerConditionMet = false;


                    for (JwtBearer jwt : jwtList) {

                        Map<String, Object> inputPayload = Maps.newHashMap();
                        if ("payload".equalsIgnoreCase(jwt.getKey()) && !payloadConditionMet) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/payload", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputPayload = createDocumentParam("jwtAuth/", "payload", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputPayload);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"),"" ));
                                payloadConditionMet = true;
                            } else {
                                String payload  = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/payload", payload, null, "document/string");
                                inputPayload = createDocumentParam("jwtAuth/","payload", payload, null, "document/string", false, null);
                                jwtAuth.add(inputPayload);
                                payloadConditionMet = true;

                            }
                        }/*else{
                            if(!payloadConditionMet) {
                                inputPayload.put("text", "payload");
                                inputPayload.put("type", "string");
                                jwtAuth.add(inputPayload);
                                payloadConditionMet = true;
                            }

                        }*/

                        Map<String, Object> algorithm = Maps.newHashMap();
                        if ("algorithm".equalsIgnoreCase(jwt.getKey()) && !algorithmConditionMet) {
                            //createVariables(intiMapStep, "jwtAuth/algorithm", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            algorithm = createDocumentParam( "jwtAuth/","algorithm", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(algorithm);
                            algorithmConditionMet = true;
                        }/*else{
                            if(!algorithmConditionMet) {
                                algorithm.put("text", "algorithm");
                                algorithm.put("type", "string");
                                jwtAuth.add(algorithm);
                                algorithmConditionMet = true;
                            }
                        }*/

                        Map<String, Object> inputSecret = Maps.newHashMap();
                        if ("secret".equalsIgnoreCase(jwt.getKey()) && !secretConditionMet) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/secret", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputSecret = createDocumentParam( "jwtAuth/","secret", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputSecret);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                                secretConditionMet = true;
                            } else {
                                String secret = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/secret", secret, null, "document/string");
                                inputSecret = createDocumentParam("jwtAuth/","secret", secret, null, "document/string", false, null);
                                jwtAuth.add(inputSecret);
                                secretConditionMet = true;
                            }
                        }/*else{
                            if(!secretConditionMet) {

                                inputSecret.put("text", "secret");
                                inputSecret.put("type", "string");
                                jwtAuth.add(inputSecret);
                                secretConditionMet = true;
                            }
                        }*/

                        Map<String, Object> isSecretBase64Encoded = Maps.newHashMap();
                        if ("isSecretBase64Encoded".equalsIgnoreCase(jwt.getKey()) && !isSecretBase64EncodedConditionMet) {
                            //createVariables(intiMapStep, "jwtAuth/isSecretBase64Encoded", jwt.getValue(), null, "document/string");
                            isSecretBase64Encoded = createDocumentParam( "jwtAuth/","isSecretBase64Encoded", jwt.getValue(), null, "document/string", false, null);
                            jwtAuth.add(isSecretBase64Encoded);
                            isSecretBase64EncodedConditionMet = true;
                        }/*else{
                            if(!isSecretBase64EncodedConditionMet) {
                                isSecretBase64Encoded.put("text", "isSecretBase64Encoded");
                                isSecretBase64Encoded.put("type", "string");
                                jwtAuth.add(isSecretBase64Encoded);
                                isSecretBase64EncodedConditionMet = true;
                            }
                        }*/

                        Map<String, Object> inputHeaderPrefix = Maps.newHashMap();
                        if ("headerPrefix".equalsIgnoreCase(jwt.getKey()) && !headerPrefixConditionMet) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/headerPrefix", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputHeaderPrefix = createDocumentParam("jwtAuth/","headerPrefix", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputHeaderPrefix);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"),""));
                                headerPrefixConditionMet = true;
                            } else {
                                String headerPrefix = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/headerPrefix", headerPrefix, null, "document/string");
                                inputHeaderPrefix = createDocumentParam("jwtAuth/","headerPrefix", headerPrefix, null, "document/string", false, null);
                                jwtAuth.add(inputHeaderPrefix);
                                headerPrefixConditionMet = true;

                            }
                        }/*else{
                            if(!headerPrefixConditionMet) {
                                inputHeaderPrefix.put("text", "headerPrefix");
                                inputHeaderPrefix.put("type", "string");
                                jwtAuth.add(inputHeaderPrefix);
                                headerPrefixConditionMet = true;
                            }
                        }*/

                        Map<String, Object> addTokenTo = Maps.newHashMap();
                        if ("addTokenTo".equalsIgnoreCase(jwt.getKey()) && !addTokenToConditionMet) {
                            //createVariables(intiMapStep, "jwtAuth/addTokenTo", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            addTokenTo = createDocumentParam("jwtAuth/","addTokenTo", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(addTokenTo);
                            addTokenToConditionMet = true;
                        }/*else{
                            if(!addTokenToConditionMet) {
                                addTokenTo.put("text", "addTokenTo");
                                addTokenTo.put("type", "string");
                                jwtAuth.add(addTokenTo);
                                addTokenToConditionMet = true;
                            }
                        }*/

                        Map<String, Object> queryParamKey = Maps.newHashMap();
                        if ("queryParamKey".equalsIgnoreCase(jwt.getKey()) && !queryParamKeyConditionMet) {
                            //createVariables(intiMapStep, "jwtAuth/queryParamKey", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            queryParamKey = createDocumentParam("jwtAuth/","queryParamKey", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(queryParamKey);
                            queryParamKeyConditionMet = true;
                        }/*else{
                            if(!queryParamKeyConditionMet) {
                                queryParamKey.put("text", "queryParamKey");
                                queryParamKey.put("type", "string");
                                jwtAuth.add(queryParamKey);
                                queryParamKeyConditionMet = true;
                            }
                        }*/

                        Map<String, Object> inputHeader = Maps.newHashMap();
                        if ("header".equalsIgnoreCase(jwt.getKey()) && !headerConditionMet) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/header", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputHeader = createDocumentParam( "jwtAuth/","header", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputHeader);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                                headerConditionMet = true;
                            } else {
                                String header = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/header", header, null, "document/string");
                                inputHeader = createDocumentParam( "jwtAuth/","header", header, null, "document/string", false, null);
                                jwtAuth.add(inputHeader);
                                headerConditionMet = true;
                            }
                        }/*else{
                            if(!headerConditionMet) {
                                inputHeader.put("text", "header");
                                inputHeader.put("type", "string");
                                jwtAuth.add(inputHeader);
                                headerConditionMet = true;
                            }
                        }*/

                    }

                    Map<String, Object> inputVar = Maps.newHashMap();
                    inputVar.put("text", "jwtAuth");
                    inputVar.put("type", "document");
                    inputVar.put("children", jwtAuth);

                    inputs.add(inputVar);

                    Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");
                    invokeStepToJson.put("fqn", "packages/Wrapper/Authorization/JwtToken");

                    createPreInvokeMapping(invokeStepToJson, "copy", "document", "/jwtAuth", "document", "/jwtAuth");

                    if (queryParamKeyConditionMet)
                        createPostInvokeMapping(invokeStepToJson, "copy", "string", "/Authorization", "document/string", "/queryParameters/Authorization");
                    else
                        createPostInvokeMapping(invokeStepToJson, "copy", "string", "/Authorization", "document/string", "/requestHeaders/Authorization");
                }
                if (auth != null && "oauth2".equalsIgnoreCase(auth.getType())) {
                    List<OAuth2> oauth2List = null;

                    if (postmanItems != null && postmanItems.getRequest() != null && postmanItems.getRequest().getAuth() != null) {
                        oauth2List = postmanItems.getRequest().getAuth().getOauth2();
                    }

                    if (oauth2List != null && !oauth2List.isEmpty()) {
                        OAuth2 oauth2 = oauth2List.get(0);

                        if ("addTokenTo".equalsIgnoreCase(oauth2.getKey())) {
                            String where = oauth2.getValue();

                            Map<String, Object> inputVar;

                            if ("header".equalsIgnoreCase(where)) {
                                inputVar = createDocumentParam("requestHeaders/", "0Auth2.0", "Bearer #{OAuth2.0} ", Evaluate.EPV, "document/string", false, null);
                                if (!requestHeadersAdded) {
                                    inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                                    requestHeadersAdded = true;
                                }
                                headers.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put("OAuth2.0", "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            } else if ("queryParams".equalsIgnoreCase(where)) {
                                inputVar = createDocumentParam("queryParameters/", "0Auth2.0", "Bearer #{OAuth2.0} ", Evaluate.EPV, "document/string", false, null);
                                if (!requestQueryParamsAdded) {
                                    inputs.add(ImportSwagger.createDocument("queryParameters", query, ""));
                                    requestQueryParamsAdded = true;
                                }
                                query.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put("OAuth2.0", "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                        }
                    } else {
                        Map<String, Object> inputVariable = new HashMap<>();

                        inputVariable = createDocumentParam("requestHeaders/", "Authorization", "Bearer #{OAuth2.0}", Evaluate.EPV, "document/string", false, null);
                        if (!requestHeadersAdded) {
                            inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                            requestHeadersAdded = true;
                        }
                        headers.add(inputVariable);

                        Map<String, String> propertiesMap = new HashMap<>();
                        propertiesMap.put("OAuth2.0", "");
                        createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                    }
                }

            }
        }

        else if(auth2!=null){
            List<Object> basicAuth = Lists.newArrayList();
            Map<String, Object> assignDoc = Maps.newHashMap();

            if (auth.getType() != null) {
                if (postmanItems.getRequest().getAuth() != null && "basic".equalsIgnoreCase(auth.getType())){
                    List<Basic> basicList = postmanItems.getRequest().getAuth().getBasic();
                    if (basicList != null) {
                        for (Basic basic : basicList) {
                            String key = basic.getKey();
                            String value = basic.getValue();

                            if (value != null && value.startsWith("{{") && value.endsWith("}}")) {
                                String variableName = value.substring(2, value.length() - 2);
                                //createVariables(intiMapStep, "basicAuth/" + key, "#{" + variableName + "}", Evaluate.EPV, "document/string");
                                assignDoc = createDocumentParam("basicAuth/" , key, "#{" + variableName + "}", Evaluate.EPV, "document/string", false, null);
                                basicAuth.add(assignDoc);

                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put(variableName, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            } else {
                                if (value != null) {
                                    //createVariables(intiMapStep, "basicAuth/" + key, value, null, "document/string");
                                    assignDoc = createDocumentParam("basicAuth/" , key, value, null, "document/string", false, null);
                                    basicAuth.add(assignDoc);
                                }
                            }
                        }
                    }else {
                        Map<String, String> inputVar = Maps.newHashMap();
                        inputVar.put("text", "username");
                        inputVar.put("type", "string");
                        basicAuth.add(inputVar);

                        inputVar = Maps.newHashMap();
                        inputVar.put("text", "password");
                        inputVar.put("type", "string");
                        basicAuth.add(inputVar);
                    }

                    Map<String, Object> inputAuthVar = Maps.newHashMap();
                    inputAuthVar.put("text", "basicAuth");
                    inputAuthVar.put("type", "document");
                    inputAuthVar.put("children", basicAuth);
                    inputs.add(inputAuthVar);

                    Map<String, Object> invokeStepToJson1 = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");
                    invokeStepToJson1.put("fqn", "packages/Wrapper/Authorization/Basic");
                    createPreInvokeMapping(invokeStepToJson1, "copy", "document/string", "/basicAuth/username", "string", "/username");
                    createPreInvokeMapping(invokeStepToJson1, "copy", "document/string", "/basicAuth/password", "string", "/password");
                    createPostInvokeMapping(invokeStepToJson1, "copy", "string", "/Authorization", "document/string", "/requestHeaders/Authorization");
                }

                else if (postmanItems.getRequest().getAuth() != null && "bearer".equalsIgnoreCase(auth.getType())) {

                    String token = null;
                    Map<String, Object> assignDocForBearer = new HashMap<>();
                    if (postmanItems.getRequest().getAuth().getBearer() != null && !postmanItems.getRequest().getAuth().getBearer().isEmpty()) {
                        token = postmanItems.getRequest().getAuth().getBearer().get(0).getValue();
                    }

                    if (token != null && token.startsWith("{{") && token.endsWith("}}")) {
                        String variableName = token.substring(2, token.length() - 2);
                        //createVariables(intiMapStep, "requestHeaders/Authorization", "Bearer #{" + variableName + "}", Evaluate.EPV, "document/string");
                        assignDocForBearer = createDocumentParam( "requestHeaders/" , "Authorization", "Bearer #{" + variableName + "}", Evaluate.EPV, "document/string", false, null);
                        headers.add(assignDocForBearer);
                        Map<String, String> propertiesMap = new HashMap<>();
                        propertiesMap.put(variableName, "");
                        createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                    } else {
                        if (token != null) {
                            //createVariables(intiMapStep, "requestHeaders/Authorization", "Bearer " + postmanToSyncloopProps(token), evaluateFrom(token, evaluateFrom), "document/string");
                            assignDocForBearer = createDocumentParam("requestHeaders/", "Authorization", "Bearer " + postmanToSyncloopProps(token), evaluateFrom(token, evaluateFrom), "document/string", false, null);
                            headers.add(assignDocForBearer);
                        }else {
                            Map<String, Object> inputVar = new HashMap<>();
                            inputVar.put("text", "Authorization");
                            inputVar.put("type", "string");
                            headers.add(inputVar);
                        }
                    }
                    /*Map<String, Object> inputVar = new HashMap<>();
                    inputVar.put("text", "Authorization");
                    inputVar.put("type", "string");
                    headers.add(inputVar);*/
                    if (!requestHeadersAdded) {
                        inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                        requestHeadersAdded = true;
                    }

                }
                else if (postmanItems.getRequest().getAuth() != null && "apikey".equalsIgnoreCase(auth.getType())){
                    String key = null;
                    String value = null;
                    String where = null;
                    List<ApiKey> apiKeyList = postmanItems.getRequest().getAuth().getApikey();

                    Map<String, Object> inputVar = new HashMap<>();

                    for (ApiKey apiKey : apiKeyList) {
                        if ("key".equalsIgnoreCase(apiKey.getKey())) {
                            key = apiKey.getValue();
                        } else if ("value".equalsIgnoreCase(apiKey.getKey())) {
                            value = apiKey.getValue();
                        } else if ("in".equalsIgnoreCase(apiKey.getKey())) {
                            where = apiKey.getValue();
                        }
                    }
                    boolean valueFromVar = false;
                    if ("header".equalsIgnoreCase(where)) {
                        if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                            String variableName = key.substring(2, key.length() - 2);
                            //createVariables(intiMapStep, "requestHeaders/" + variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            if(!value.contains("{{") || !value.contains("}}")){
                                inputVar = createDocumentParam("requestHeaders/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                headers.add(inputVar);
                            }else{
                                inputVar = createDocumentParam("requestHeaders/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                headers.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                propertiesMap.put(adjustedPropertyValue, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                            valueFromVar = true;
                        }
                        else if (key != null && value == null && StringUtils.isNotBlank(key) ) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("requestHeaders/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestHeadersAdded) {
                                inputs.add(createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                            headers.add(inputVariable);
                        }
                        else {
                            if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                //createVariables(intiMapStep,"requestHeaders/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                                inputVar = createDocumentParam("requestHeaders/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                headers.add(inputVar);
                                if (value.contains("{{") || value.contains("}}") ) {
                                    Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                    Matcher matcher = pattern.matcher(value);
                                    if (matcher.find()) {
                                        String variableName = matcher.group(1);
                                        createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                    }
                                }
                            }else{
                                if(key != null && StringUtils.isNotBlank(key)) {
                                    inputVar.put("text", key);
                                    inputVar.put("type", "string");
                                    headers.add(inputVar);
                                }
                            }
                        }
                        /*Map<String, Object> inputVar = new HashMap<>();
                        inputVar.put("text", key);
                        inputVar.put("type", "string");
                        headers.add(inputVar);*/

                        if (!requestHeadersAdded) {
                            inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                            requestHeadersAdded = true;
                        }
                    }

                    if ("query".equalsIgnoreCase(where)) {
                        if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                            String variableName = key.substring(2, key.length() - 2);
                            if(!value.contains("{{") || !value.contains("}}")){
                                inputVar = createDocumentParam("queryParameters/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                query.add(inputVar);
                            }else{
                                inputVar = createDocumentParam("queryParameters/" , variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                query.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                propertiesMap.put(adjustedPropertyValue, "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                            valueFromVar = true;
                        }
                        else if (key != null && value == null && StringUtils.isNotBlank(key) ) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("queryParameters/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestQueryParamsAdded) {
                                inputs.add(createDocument("queryParameters", query, ""));
                                requestQueryParamsAdded = true;
                            }
                            query.add(inputVariable);
                        }
                        else {
                            if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                inputVar = createDocumentParam("queryParameters/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                query.add(inputVar);
                                if (value.contains("{{") || value.contains("}}") ) {
                                    Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                    Matcher matcher = pattern.matcher(value);
                                    if (matcher.find()) {
                                        String variableName = matcher.group(1);
                                        createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                    }
                                }
                            }else{
                                if(key != null && StringUtils.isNotBlank(key)) {
                                    inputVar.put("text", key);
                                    inputVar.put("type", "string");
                                    query.add(inputVar);
                                }
                            }
                        }

                        if (!requestQueryParamsAdded) {
                            inputs.add(ImportSwagger.createDocument("queryParameters", query, ""));
                            requestQueryParamsAdded = true;
                        }
                    }

                    /*if ("query".equalsIgnoreCase(where)) {
                        Map<String, Object> inputVarForQuery = new HashMap<>();

                        if (!valueFromVar && key != null) {
                            //createVariables(intiMapStep, "queryParameters/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                            inputVarForQuery = createDocumentParam("queryParameters/" , key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                            query.add(inputVarForQuery);

                        }else{
                            inputVarForQuery.put("text", key);
                            inputVarForQuery.put("type", "string");
                            if (query != null) {
                                query.add(inputVarForQuery);
                            }
                        }

                        if (!requestQueryParamsAdded) {
                            inputs.add(ImportSwagger.createDocument("queryParameters", headers, ""));
                            requestQueryParamsAdded = true;
                        }

                    }*/

                    if (key != null && StringUtils.isNotBlank(key)) {
                        if (value != null && where == null) {
                            if (key != null && key.startsWith("{{") && key.endsWith("}}") && StringUtils.isNotBlank(key)) {

                                String variableName = key.substring(2, key.length() - 2);
                                //createVariables(intiMapStep, "requestHeaders/" + variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                if (!value.contains("{{") || !value.contains("}}")) {
                                    inputVar = createDocumentParam("requestHeaders/", variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), null, "document/string", false, null);
                                    headers.add(inputVar);
                                } else {
                                    inputVar = createDocumentParam("requestHeaders/", variableName, value.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                    headers.add(inputVar);
                                    Map<String, String> propertiesMap = new HashMap<>();
                                    String adjustedPropertyValue = value.replaceAll("^\\{\\{(.+?)}}.*", "$1");
                                    propertiesMap.put(adjustedPropertyValue, "");
                                    createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                                }
                                valueFromVar = true;
                            } else {
                                if (key != null && value != null && StringUtils.isNotBlank(key)) {
                                    //createVariables(intiMapStep,"requestHeaders/" + key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string");
                                    inputVar = createDocumentParam("requestHeaders/", key, postmanToSyncloopProps(value), evaluateFrom(value, evaluateFrom), "document/string", false, null);
                                    if (value.contains("{{") || value.contains("}}")) {
                                        Pattern pattern = Pattern.compile("\\{\\{([^}]+)}}");
                                        Matcher matcher = pattern.matcher(value);
                                        if (matcher.find()) {
                                            String variableName = matcher.group(1);
                                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(variableName, ""));

                                        }
                                    }
                                    headers.add(inputVar);
                                } else {
                                    if(key != null && StringUtils.isNotBlank(key)){
                                        inputVar.put("text", key);
                                        inputVar.put("type", "string");
                                        headers.add(inputVar);
                                    }
                                }
                            }

                        /*Map<String, Object> inputVar = new HashMap<>();
                        inputVar.put("text", key);
                        inputVar.put("type", "string");
                        headers.add(inputVar);*/

                            if (!requestHeadersAdded) {
                                inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                        }
                        else if (value == null && where == null) {
                            Map<String, Object> inputVariable = new HashMap<>();
                            inputVariable = createDocumentParam("requestHeaders/" , key, "<Enter value here>", null, "document/string", false, null);
                            if (!requestHeadersAdded) {
                                inputs.add(createDocument("requestHeaders", headers, ""));
                                requestHeadersAdded = true;
                            }
                            headers.add(inputVariable);
                        }
                    }


                }
                else if (postmanItems.getRequest().getAuth() != null && "awsv4".equalsIgnoreCase(auth.getType())) {
                    List<Object> awsAuth = Lists.newArrayList();

                    String service = null;
                    String region = null;
                    String secretKey = null;
                    String accessKey = null;

                    List<AwsSignature> signatureList = postmanItems.getRequest().getAuth().getAwsv4();
                    for (AwsSignature aws : signatureList) {
                        if ("service".equalsIgnoreCase(aws.getKey())) {
                            service = aws.getValue();
                        } else if ("region".equalsIgnoreCase(aws.getKey())) {
                            region = aws.getValue();
                        } else if ("secretKey".equalsIgnoreCase(aws.getKey())) {
                            secretKey = aws.getValue();
                        } else if ("accessKey".equalsIgnoreCase(aws.getKey())) {
                            accessKey = aws.getValue();
                        }
                    }

                    Map<String, Object> serviceMap = Maps.newHashMap();


                    if (service != null) {
                        if (service.startsWith("{{") && service.endsWith("}}")) {

                            //createVariables(intiMapStep, "awsAuth/service", service.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            serviceMap = createDocumentParam("awsAuth/", "service", service.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false ,null);
                            awsAuth.add(serviceMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(service.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/service", postmanToSyncloopProps(service), null, "document/string");
                            serviceMap = createDocumentParam("awsAuth/", "service", postmanToSyncloopProps(service), null, "document/string", false, null);
                            awsAuth.add(serviceMap);
                        }
                    }else{
                        serviceMap.put("text", "service");
                        serviceMap.put("type", "string");
                        awsAuth.add(serviceMap);
                    }

                    Map<String, Object> regionMap = Maps.newHashMap();
                    if (region != null) {
                        if (region.startsWith("{{") && region.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/region", region.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            regionMap = createDocumentParam("awsAuth/", "region", region.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            awsAuth.add(regionMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(region.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/region", postmanToSyncloopProps(region), null, "document/string");
                            regionMap = createDocumentParam("awsAuth/", "region", postmanToSyncloopProps(region), null, "document/string", false, null);
                            awsAuth.add(regionMap);
                        }
                    }else{
                        regionMap.put("text", "region");
                        regionMap.put("type", "string");
                        awsAuth.add(regionMap);
                    }

                    Map<String, Object> secretMap = Maps.newHashMap();
                    if (secretKey != null) {
                        if (secretKey.startsWith("{{") && secretKey.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/secretKey", secretKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            secretMap = createDocumentParam("awsAuth/","secretKey", secretKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(secretKey.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                            awsAuth.add(secretMap);
                        } else {
                            //createVariables(intiMapStep, "awsAuth/secretKey", postmanToSyncloopProps(secretKey), null, "document/string");
                            secretMap = createDocumentParam("awsAuth/","secretKey", postmanToSyncloopProps(secretKey), null, "document/string", false, null);
                            awsAuth.add(secretMap);

                        }
                    }else{
                        secretMap.put("text", "secretKey");
                        secretMap.put("type", "string");
                        awsAuth.add(secretMap);
                    }

                    Map<String, Object> accessKeyMap = Maps.newHashMap();
                    if (accessKey != null) {
                        if (accessKey.startsWith("{{") && accessKey.endsWith("}}")) {
                            //createVariables(intiMapStep, "awsAuth/accessKey", accessKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                            accessKeyMap = createDocumentParam("awsAuth/", "accessKey", accessKey.replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                            awsAuth.add(accessKeyMap);
                            createFolderStructure(dependencyFolderPath, "package.properties", Map.of(accessKey.replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                        } else {
                            //createVariables(intiMapStep, "awsAuth/accessKey", postmanToSyncloopProps(accessKey), null, "document/string");
                            accessKeyMap = createDocumentParam("awsAuth/", "accessKey", postmanToSyncloopProps(accessKey), null, "document/string", false, null);
                            awsAuth.add(accessKeyMap);
                        }
                    }else{
                        accessKeyMap.put("text", "accessKey");
                        accessKeyMap.put("type", "string");
                        awsAuth.add(accessKeyMap);
                    }


                    Map<String, Object> inputVar = Maps.newHashMap();
                    inputVar.put("text", "awsAuth");
                    inputVar.put("type", "document");
                    inputVar.put("children", awsAuth);

                    inputs.add(inputVar);

                    Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");

                    invokeStepToJson.put("fqn", "packages/Wrapper/AWS/credential/CreateSigner");

                    if (!"GET".equalsIgnoreCase(method)) {
                        createPreInvokeMapping(invokeStepToJson, "copy", "string", "/body", "string", "/payload");
                    }

                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/service", "string", "/service");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/region", "string", "/region");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/secretKey", "string", "/secret");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document/string", "/awsAuth/accessKey", "string", "/accessKey");

                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/url", "string", "/url");
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/method", "string", "/method");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document", "/queryParameters", "document", "/queryParameters");
                    createPreInvokeMapping(invokeStepToJson, "copy", "document", "/requestHeaders", "document", "/headers");
                    createPostInvokeMapping(invokeStepToJson, "copy", "document", "/headers", "document", "/requestHeaders");
                }
                else if (postmanItems.getRequest().getAuth() != null && "jwt".equalsIgnoreCase(auth.getType())) {

                    List<JwtBearer> jwtList = postmanItems.getRequest().getAuth().getJwt();
                    List<Object> jwtAuth = Lists.newArrayList();

                    for (JwtBearer jwt : jwtList) {

                        Map<String, Object> inputPayload = Maps.newHashMap();
                        if ("payload".equalsIgnoreCase(jwt.getKey())) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/payload", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputPayload = createDocumentParam("jwtAuth/", "payload", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputPayload);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"),"" ));
                            } else {
                                String payload  = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/payload", payload, null, "document/string");
                                inputPayload = createDocumentParam("jwtAuth/","payload", payload, null, "document/string", false, null);
                                jwtAuth.add(inputPayload);
                            }
                        }else{
                            inputPayload.put("text", "payload");
                            inputPayload.put("type", "string");
                            jwtAuth.add(inputPayload);
                        }

                        Map<String, Object> algorithm = Maps.newHashMap();
                        if ("algorithm".equalsIgnoreCase(jwt.getKey())) {
                            //createVariables(intiMapStep, "jwtAuth/algorithm", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            algorithm = createDocumentParam( "jwtAuth/","algorithm", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(algorithm);
                        }else{
                            algorithm.put("text", "algorithm");
                            algorithm.put("type", "string");
                            jwtAuth.add(algorithm);
                        }

                        Map<String, Object> inputSecret = Maps.newHashMap();
                        if ("secret".equalsIgnoreCase(jwt.getKey())) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/secret", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputSecret = createDocumentParam( "jwtAuth/","secret", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputSecret);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                            } else {
                                String secret = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/secret", secret, null, "document/string");
                                inputSecret = createDocumentParam("jwtAuth/","secret", secret, null, "document/string", false, null);
                                jwtAuth.add(inputSecret);
                            }
                        }else{
                            inputSecret.put("text", "secret");
                            inputSecret.put("type", "string");
                            jwtAuth.add(inputSecret);
                        }

                        Map<String, Object> isSecretBase64Encoded = Maps.newHashMap();
                        if ("isSecretBase64Encoded".equalsIgnoreCase(jwt.getKey())) {
                            //createVariables(intiMapStep, "jwtAuth/isSecretBase64Encoded", jwt.getValue(), null, "document/string");
                            isSecretBase64Encoded = createDocumentParam( "jwtAuth/","isSecretBase64Encoded", jwt.getValue(), null, "document/string", false, null);
                            jwtAuth.add(isSecretBase64Encoded);
                        }else{
                            isSecretBase64Encoded.put("text", "isSecretBase64Encoded");
                            isSecretBase64Encoded.put("type", "string");
                            jwtAuth.add(isSecretBase64Encoded);
                        }

                        Map<String, Object> inputHeaderPrefix = Maps.newHashMap();
                        if ("headerPrefix".equalsIgnoreCase(jwt.getKey())) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/headerPrefix", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputHeaderPrefix = createDocumentParam("jwtAuth/","headerPrefix", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputHeaderPrefix);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"),""));
                            } else {
                                String headerPrefix = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/headerPrefix", headerPrefix, null, "document/string");
                                inputHeaderPrefix = createDocumentParam("jwtAuth/","headerPrefix", headerPrefix, null, "document/string", false, null);
                                jwtAuth.add(inputHeaderPrefix);

                            }
                        }else{
                            inputHeaderPrefix.put("text", "headerPrefix");
                            inputHeaderPrefix.put("type", "string");
                            jwtAuth.add(inputHeaderPrefix);
                        }

                        Map<String, Object> addTokenTo = Maps.newHashMap();
                        if ("addTokenTo".equalsIgnoreCase(jwt.getKey())) {
                            //createVariables(intiMapStep, "jwtAuth/addTokenTo", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            addTokenTo = createDocumentParam("jwtAuth/","addTokenTo", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(addTokenTo);
                        }else{
                            addTokenTo.put("text", "addTokenTo");
                            addTokenTo.put("type", "string");
                            jwtAuth.add(addTokenTo);
                        }

                        Map<String, Object> queryParamKey = Maps.newHashMap();
                        if ("queryParamKey".equalsIgnoreCase(jwt.getKey())) {
                            //createVariables(intiMapStep, "jwtAuth/queryParamKey", postmanToSyncloopProps(jwt.getValue()), null, "document/string");
                            queryParamKey = createDocumentParam("jwtAuth/","queryParamKey", postmanToSyncloopProps(jwt.getValue()), null, "document/string", false, null);
                            jwtAuth.add(queryParamKey);
                        }else{
                            queryParamKey.put("text", "queryParamKey");
                            queryParamKey.put("type", "string");
                            jwtAuth.add(queryParamKey);
                        }

                        Map<String, Object> inputHeader = Maps.newHashMap();
                        if ("header".equalsIgnoreCase(jwt.getKey())) {
                            if (jwt.getValue() != null && jwt.getValue().startsWith("{{") && jwt.getValue().endsWith("}}")) {
                                //createVariables(intiMapStep, "jwtAuth/header", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string");
                                inputHeader = createDocumentParam( "jwtAuth/","header", jwt.getValue().replaceAll("\\{\\{(.+?)}}", "#{$1}"), Evaluate.EPV, "document/string", false, null);
                                jwtAuth.add(inputHeader);
                                createFolderStructure(dependencyFolderPath, "package.properties", Map.of(jwt.getValue().replaceAll("^\\{\\{(.+?)}}.*", "$1"), ""));
                            } else {
                                String header = postmanToSyncloopProps(jwt.getValue());
                                //createVariables(intiMapStep, "jwtAuth/header", header, null, "document/string");
                                inputHeader = createDocumentParam( "jwtAuth/","header", header, null, "document/string", false, null);
                                jwtAuth.add(inputHeader);
                            }
                        }else{
                            inputHeader.put("text", "header");
                            inputHeader.put("type", "string");
                            jwtAuth.add(inputHeader);
                        }

                    }

                    Map<String, Object> inputVar = Maps.newHashMap();
                    inputVar.put("text", "jwtAuth");
                    inputVar.put("type", "document");
                    inputVar.put("children", jwtAuth);

                    inputs.add(inputVar);

                    Map<String, Object> invokeStepToJson = createInvokeStep(flowSteps, "service", "INVOKE", "Initialize");
                    invokeStepToJson.put("fqn", "packages/Wrapper/Authorization/JwtToken");
                    createPreInvokeMapping(invokeStepToJson, "copy", "docuemnt", "/jwtAuth", "document", "/jwtAuth");
                    createPostInvokeMapping(invokeStepToJson, "copy", "string", "/Authorization", "document/string", "/requestHeaders/Authorization");
                }
                else if (auth != null && "oauth2".equalsIgnoreCase(auth.getType())) {
                    List<OAuth2> oauth2List = null;
                    if (postmanItems != null && postmanItems.getRequest() != null && postmanItems.getRequest().getAuth() != null) {
                        oauth2List = postmanItems.getRequest().getAuth().getOauth2();
                    }
                    if (oauth2List != null && !oauth2List.isEmpty()) {
                        OAuth2 oauth2 = oauth2List.get(0);

                        if ("addTokenTo".equalsIgnoreCase(oauth2.getKey())) {
                            String where = oauth2.getValue();

                            Map<String, Object> inputVar;

                            if ("header".equalsIgnoreCase(where)) {
                                inputVar = createDocumentParam("requestHeaders/", "0Auth2.0", "Bearer #{OAuth2.0} ", Evaluate.EPV, "document/string", false, null);
                                if (!requestHeadersAdded) {
                                    inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                                    requestHeadersAdded = true;
                                }
                                headers.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put("OAuth2.0", "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            } else if ("queryParams".equalsIgnoreCase(where)) {
                                inputVar = createDocumentParam("queryParameters/", "0Auth2.0", "Bearer #{OAuth2.0} ", Evaluate.EPV, "document/string", false, null);
                                if (!requestQueryParamsAdded) {
                                    inputs.add(ImportSwagger.createDocument("queryParameters", query, ""));
                                    requestQueryParamsAdded = true;
                                }
                                query.add(inputVar);
                                Map<String, String> propertiesMap = new HashMap<>();
                                propertiesMap.put("OAuth2.0", "");
                                createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                            }
                        }
                    } else {
                        Map<String, Object> inputVariable = new HashMap<>();

                        inputVariable = createDocumentParam("requestHeaders/", "Authorization", "Bearer #{OAuth2.0}", Evaluate.EPV, "document/string", false, null);
                        if (!requestHeadersAdded) {
                            inputs.add(ImportSwagger.createDocument("requestHeaders", headers, ""));
                            requestHeadersAdded = true;
                        }
                        headers.add(inputVariable);

                        Map<String, String> propertiesMap = new HashMap<>();
                        propertiesMap.put("OAuth2.0", "");
                        createFolderStructure(dependencyFolderPath, "package.properties", propertiesMap);
                    }
                }
            }
        }

        Map<String, Object> invokeStepRequest = createInvokeStep(flowSteps, "service", "INVOKE", "SettingUp Request Payload");

        invokeStepRequest.put("fqn", "packages/middleware/pub/client/http/request");

        createPreInvokeMapping(invokeStepRequest, "copy", "document", "/requestHeaders", "document", "/headers");
        createPreInvokeMapping(invokeStepRequest, "copy", "document", "/queryParameters", "document", "/urlParameters");
        createPreInvokeMapping(invokeStepRequest, "copy", "string", "/url", "string", "/url");
        createPreInvokeMapping(invokeStepRequest, "copy", "string", "/method", "string", "/method");
        createPreInvokeMapping(invokeStepRequest, "copy", "boolean", "/sendBlankParams", "document/boolean", "/settings/sendBlankParams");

        createPostInvokeMapping(invokeStepRequest, "copy", "document", "/respHeaders", "document", "/respHeaders");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/respPayload", "string", "/respPayload");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/error", "string", "/error");
        createPostInvokeMapping(invokeStepRequest, "copy", "byteList", "/bytes", "byteList", "/bytes");
        createPostInvokeMapping(invokeStepRequest, "copy", "javaObject", "/inputStream", "javaObject", "/inputStream");
        createPostInvokeMapping(invokeStepRequest, "copy", "string", "/statusCode", "string", "/statusCode");

        if (null != postmanItems.getRequest() && null != postmanItems.getRequest().getBody() && postmanItems.getRequest().getBody().getMode().equals("file")) {
            createPreInvokeMapping(invokeStepRequest, "copy", "javaObject", "/inputStream", "javaObject", "/inputStream");
        }

        if (null != postmanItems.getRequest() && null != postmanItems.getRequest().getBody() && postmanItems.getRequest().getBody().getMode().equals("formdata")) {
            List<FormData> dataList = postmanItems.getRequest().getBody().getFormdata();
            for (FormData dataItem : dataList) {
                if ("file".equalsIgnoreCase(dataItem.getType())) {
                    createPreInvokeMapping(invokeStepRequest, "copy", "document/javaObject", "/formData/"+dataItem.getKey(), "document/javaObject", "/formData/"+dataItem.getKey());
                }
                else {
                    createPreInvokeMapping(invokeStepRequest, "copy", "document/string", "/formData/"+dataItem.getKey(), "document/string", "/formData/"+dataItem.getKey());
                }
            }
        }

        if (null != postmanItems.getRequest() && null != postmanItems.getRequest().getBody() && postmanItems.getRequest().getBody().getMode().equals("urlencoded")) {
            List<UrlEncodedParameter> dataList = postmanItems.getRequest().getBody().getUrlencoded();
            if (dataList != null && !dataList.isEmpty()) {
                for (UrlEncodedParameter dataItem : dataList) {
                    createPreInvokeMapping(invokeStepRequest, "copy", "document/string", "/urlencoded/"+dataItem.getKey(), "document/string", "/formData/"+dataItem.getKey());
                }
            }
        }

        dropVariables(invokeStepRequest, "method", "string");
        dropVariables(invokeStepRequest, "url", "string");
        dropVariables(invokeStepRequest, "headers", "document");
        dropVariables(invokeStepRequest, "urlParameters", "document");


        if (!"GET".equalsIgnoreCase(method)) {
            createPreInvokeMapping(invokeStepRequest, "copy", "string", "/body", "string", "/payload");
            dropVariables(invokeStepRequest, "payload", "string");
        }


        Map<String, Object> switchMapping = createSwitch(flowSteps, "switch", "SWITCH", "Checking status for response");
        addData(switchMapping, "switch", "statusCode");

        List<PostmanItemResponse> response = postmanItems.getResponse();
        List<Object> cases = Lists.newArrayList();
        if (null != response) {
            if (response.isEmpty()) {
                PostmanItemResponse postmanItemResponse = new PostmanItemResponse();
                postmanItemResponse.setCode(200);
                response.add(postmanItemResponse);
            }
            for (PostmanItemResponse resp : response) {

                String code = resp.getCode() < 100 ? "200": resp.getCode() + "";
                Map<String, Object> sequenceMapping = createCase(cases, "CASE", "Handling " + code + " response");
                addData(sequenceMapping, "case", code);

                List<Object> contentTypeSwitch = Lists.newArrayList();
                addContentTypeHandler(contentTypeSwitch, intiMapStep, resp);
                addChildren(sequenceMapping, contentTypeSwitch);

            }
        }

        addChildren(switchMapping, cases);
        createDefaultCase(cases,flowService);

        Map<String, Object> commonResponse = createMapStep(flowSteps, "Mapping Common Response & HTTP Status Code");
        createMapping(commonResponse, "copy", "string", "/respPayload", "string", "/rawResponse");

        addResponseBody(outputs, postmanItems, true);

        Map<String, String> commonOutputRawResponse = Maps.newHashMap();
        commonOutputRawResponse.put("text", "rawResponse");
        commonOutputRawResponse.put("type", "string");
        outputs.add(commonOutputRawResponse);

        Map<String, String> commonOutputStatusCode = Maps.newHashMap();
        commonOutputStatusCode.put("text", "statusCode");
        commonOutputStatusCode.put("type", "string");
        outputs.add(commonOutputStatusCode);

        // Add multiple consumer groups
        String json = new Gson().toJson(flowService.getFlow());
        Map<String, Object> map = new Gson().fromJson(json, Map.class);

        List<String> consumerGroups = new ArrayList<>();
        consumerGroups.add("consumers");
        map.put("consumers", StringUtils.join(consumerGroups, ","));


        List<String> developerGroups = new ArrayList<>();
        developerGroups.add("developers");
        map.put("developers", StringUtils.join(developerGroups, ","));

        JSONObject jsonObject = new JSONObject(map);
        String updatedJson = jsonObject.toString();
        saveFlow(filePath, updatedJson);
        generateJavaClass(file, "packages" + File.separator + packageName
                + File.separator  + "client" + servicePath, null, null);
        return filePath;
    }

    public static void createDefaultCase(List<Object> cases, FlowService flowService) {

        Map<String, Object> defaultContentTypeMapping = createCase(cases, "CASE", "Handling default cases");
        addData(defaultContentTypeMapping, "case", "#default");

        List<Object> defaultTypeSwitch = Lists.newArrayList();
        Map<String, Object> defaultMapStep = createMapStep(defaultTypeSwitch, "configuring generic status code");
        createVariables(defaultMapStep, "statusCodeType", "#{statusCode}/100", Evaluate.EEV, "integer");
        addChildren(defaultContentTypeMapping, defaultTypeSwitch);

        Map<String, Object> innerSwitch = createSwitch(defaultTypeSwitch, "switch", "SWITCH", "handling generic status code");
        addData(innerSwitch, "switch", "statusCodeType");

        List<Object> contentTypeCases = com.beust.jcommander.internal.Lists.newArrayList();

        //------------2xx

        Map<String, Object> create2Case = createCase(contentTypeCases, "CASE", "Handling JSON and XML response for *2xx status code");
        addData(create2Case, "case", "2");

        List<Object> jsonSwitch2xx = Lists.newArrayList();
        List<Object> jsonSwitch2xxType = Lists.newArrayList();
        Map<String, Object> jsonCase2xx = createSwitch(jsonSwitch2xx, "switch", "SWITCH", "Checking content type for response");
        addData(jsonCase2xx, "switch", "respHeaders/content-type");

        Map<String, Object> jsonCase = createCase(jsonSwitch2xxType, "CASE", "Handling JSON response");
        addData(jsonCase, "case", "#regex:.*json.*");

        List<Object> jsonConversionCaseFor2xx = com.beust.jcommander.internal.Lists.newArrayList();
        Map<String, Object> invokeStepToJsonFor2xx = createInvokeStep(jsonConversionCaseFor2xx, "service", "INVOKE",
                "Converting raw Json to JSON Object");

        invokeStepToJsonFor2xx.put("fqn", "packages/middleware/pub/json/fromJson");

        createPreInvokeMapping(invokeStepToJsonFor2xx, "copy", "string", "/respPayload", "string", "/jsonString");
        createPreInvokeMapping(invokeStepToJsonFor2xx, "copy", "string", "/rootName_Response", "string", "/rootName");
        createPostInvokeMapping(invokeStepToJsonFor2xx, "copy", "document", "/output", "document", "/*2xx" );
        addChildren(jsonCase, jsonConversionCaseFor2xx);

        Map<String, Object> xmlCase = createCase(jsonSwitch2xxType, "CASE", "Handling XML response");
        addData(xmlCase, "case", "#regex:.*xml.*");

        List<Object> xmlConversionFor2xx = com.beust.jcommander.internal.Lists.newArrayList();

        Map<String, Object> invokeStepToXmlFor2xx = createInvokeStep(xmlConversionFor2xx, "service", "INVOKE", "Converting raw xml to JSON Object");

        invokeStepToXmlFor2xx.put("fqn", "packages/middleware/pub/xml/fromXML");

        createPreInvokeMapping(invokeStepToXmlFor2xx, "copy", "string", "/respPayload", "string", "/xml");
        createPostInvokeMapping(invokeStepToXmlFor2xx, "copy", "document/document", "/output/root", "document/document", "/*2xx" + "/root");
        addChildren(xmlCase, xmlConversionFor2xx);

        addChildren(create2Case, jsonSwitch2xx);
        addChildren(jsonCase2xx, jsonSwitch2xxType);

        //------------4xx

        Map<String, Object> create4Case = createCase(contentTypeCases, "CASE", "Handling JSON and XML response for *4xx status code");
        addData(create4Case, "case", "4");

        List<Object> jsonSwitch4xx = Lists.newArrayList();
        List<Object> jsonSwitch4xxType = Lists.newArrayList();
        Map<String, Object> jsonCase4xx = createSwitch(jsonSwitch4xx, "switch", "SWITCH", "Checking content type for response");
        addData(jsonCase4xx, "switch", "respHeaders/content-type");

        Map<String, Object> jsonCase4xxInner = createCase(jsonSwitch4xxType, "CASE", "Handling JSON response");
        addData(jsonCase4xxInner, "case", "#regex:.*json.*");

        List<Object> jsonConversionCaseFor4xx = com.beust.jcommander.internal.Lists.newArrayList();
        Map<String, Object> invokeStepToJsonFor4xx = createInvokeStep(jsonConversionCaseFor4xx, "service", "INVOKE",
                "Converting raw Json to JSON Object");

        invokeStepToJsonFor4xx.put("fqn", "packages/middleware/pub/json/fromJson");

        createPreInvokeMapping(invokeStepToJsonFor4xx, "copy", "string", "/respPayload", "string", "/jsonString");
        createPreInvokeMapping(invokeStepToJsonFor4xx, "copy", "string", "/rootName_Response", "string", "/rootName");
        createPostInvokeMapping(invokeStepToJsonFor4xx, "copy", "document", "/output", "document", "/*4xx" );
        addChildren(jsonCase4xxInner, jsonConversionCaseFor4xx);

        Map<String, Object> xmlCase4xxInner = createCase(jsonSwitch4xxType, "CASE", "Handling XML response");
        addData(xmlCase4xxInner, "case", "#regex:.*xml.*");

        List<Object> xmlConversionFor4xx = com.beust.jcommander.internal.Lists.newArrayList();

        Map<String, Object> invokeStepToXmlFor4xx = createInvokeStep(xmlConversionFor4xx, "service", "INVOKE", "Converting raw xml to JSON Object");

        invokeStepToXmlFor4xx.put("fqn", "packages/middleware/pub/xml/fromXML");

        createPreInvokeMapping(invokeStepToXmlFor4xx, "copy", "string", "/respPayload", "string", "/xml");
        createPostInvokeMapping(invokeStepToXmlFor4xx, "copy", "document/document", "/output/root", "document/document", "/*4xx" + "/root");
        addChildren(xmlCase4xxInner, xmlConversionFor4xx);

        addChildren(jsonCase4xx, jsonSwitch4xxType);
        addChildren(create4Case, jsonSwitch4xx);

        //------------5xx

        Map<String, Object> create5Case = createCase(contentTypeCases, "CASE", "Handling JSON and XML response for *5xx status code");
        addData(create5Case, "case", "5");

        List<Object> jsonSwitch5xx = Lists.newArrayList();
        List<Object> jsonSwitch5xxType = Lists.newArrayList();
        Map<String, Object> jsonCase5xx = createSwitch(jsonSwitch5xx, "switch", "SWITCH", "Handling JSON and XML response");
        addData(jsonCase5xx, "switch", "respHeaders/content-type");

        Map<String, Object> jsonCase5xxInner = createCase(jsonSwitch5xxType, "CASE", "Handling JSON response");
        addData(jsonCase5xxInner, "case", "#regex:.*json.*");

        List<Object> jsonConversionCaseFor5xx = com.beust.jcommander.internal.Lists.newArrayList();
        Map<String, Object> invokeStepToJsonFor5xx = createInvokeStep(jsonConversionCaseFor5xx, "service", "INVOKE",
                "Converting raw Json to JSON Object");

        invokeStepToJsonFor5xx.put("fqn", "packages/middleware/pub/json/fromJson");

        createPreInvokeMapping(invokeStepToJsonFor5xx, "copy", "string", "/respPayload", "string", "/jsonString");
        createPreInvokeMapping(invokeStepToJsonFor5xx, "copy", "string", "/rootName_Response", "string", "/rootName");
        createPostInvokeMapping(invokeStepToJsonFor5xx, "copy", "document", "/output", "document", "/*5xx" );
        addChildren(jsonCase5xxInner, jsonConversionCaseFor5xx);

        Map<String, Object> xmlCase5xxInner = createCase(jsonSwitch5xxType, "CASE", "Handling XML response");
        addData(xmlCase5xxInner, "case", "#regex:.*xml.*");

        List<Object> xmlConversionFor5xx = com.beust.jcommander.internal.Lists.newArrayList();

        Map<String, Object> invokeStepToXmlFor5xx = createInvokeStep(xmlConversionFor5xx, "service", "INVOKE", "Converting raw xml to JSON Object");

        invokeStepToXmlFor5xx.put("fqn", "packages/middleware/pub/xml/fromXML");

        createPreInvokeMapping(invokeStepToXmlFor5xx, "copy", "string", "/respPayload", "string", "/xml");
        createPostInvokeMapping(invokeStepToXmlFor5xx, "copy", "document/document", "/output/root", "document/document", "/*5xx" + "/root");
        addChildren(xmlCase5xxInner, xmlConversionFor5xx);

        addChildren(jsonCase5xx, jsonSwitch5xxType);
        addChildren(create5Case, jsonSwitch5xx);

        addChildren(innerSwitch, contentTypeCases);

        List<Object> outputs = flowService.getOutput();

        Map<String, String> OutputRawResponseFor2xx = Maps.newHashMap();
        OutputRawResponseFor2xx.put("text", "*2xx");
        OutputRawResponseFor2xx.put("type", "document");
        outputs.add(OutputRawResponseFor2xx);

        Map<String, String> OutputRawResponseFor4xx = Maps.newHashMap();
        OutputRawResponseFor4xx.put("text", "*4xx");
        OutputRawResponseFor4xx.put("type", "document");
        outputs.add(OutputRawResponseFor4xx);

        Map<String, String> OutputRawResponseFor5xx = Maps.newHashMap();
        OutputRawResponseFor5xx.put("text", "*5xx");
        OutputRawResponseFor5xx.put("type", "document");
        outputs.add(OutputRawResponseFor5xx);


    }

    private static List<Object> getClientRequestQuery(String dependencyFolderPath, List<UrlQuery> queries, List<Object> input, boolean isServer, Evaluate evaluateFrom) {
        if (null == queries) {
            return Lists.newArrayList();
        }
        List<Object> document = new ArrayList<>();
        for (UrlQuery query : queries) {
            if (query.isDisabled()) {
                continue;
            }
            Set<String> keys = null;
            if (query.getValue() != null) {
                keys = extractKeys(query.getValue());
            }
            if (keys != null) {
                for (String key : keys) {
                    createFolderStructure(dependencyFolderPath, "package.properties", Map.of(key, ""));
                }
            }

            if (StringUtils.isNotBlank(query.getValue()) && !isServer && null != input) {
                if (query.getValue().startsWith("{{") && query.getValue().endsWith("}}")) {
                    String variableName = query.getValue().substring(2, query.getValue().length() - 2);
                    Map<String, Object> documentParam = createDocumentParam("queryParameters/", query.getKey(), "#{" + variableName + "}",
                            Evaluate.EPV, "document/string", true, encodeBas64(query.getDescription()));
                    document.add(documentParam);
                } else {
                    Map<String, Object> documentParam = createDocumentParam("queryParameters/", query.getKey(), postmanToSyncloopProps(query.getValue()),
                            evaluateFrom(query.getValue(), evaluateFrom), "document/string", true, encodeBas64(query.getDescription()));
                    document.add(documentParam);
                }
            } else if (StringUtils.isNotBlank(query.getKey()) && !isServer) {
                Map<String, Object> documentParam = createDocumentParam("queryParameters/", query.getKey(), "",
                        null, "document/string", true, encodeBas64(query.getDescription()));
                document.add(documentParam);
            }
        }
        return document;
    }

    public static Map<String, Object> addContentTypeHandler(List<Object> contentTypeSwitch, Map<String, Object> intiMapStep, PostmanItemResponse postmanItemResponse) {

        int code = postmanItemResponse.getCode() < 100 ? 200 : postmanItemResponse.getCode() ;

        Map<String, Object> switchContentTypeMapping = createSwitch(contentTypeSwitch, "switch", "SWITCH", "Checking content type for response");
        addData(switchContentTypeMapping, "switch", "respHeaders/content-type");

        List<Object> contentTypeCases = com.beust.jcommander.internal.Lists.newArrayList();
        String body = postmanItemResponse.getBody();

        if (null != body) {
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases, "CASE", "Handling JSON response");
                addData(sequenceContentTypeMapping, "case", "#regex:.*json.*");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                createVariables(intiMapStep, "rootName_Response", "response", null, "string");
                createVariables(intiMapStep, "rootName_Error", "error", null, "string");

                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase, "service", "INVOKE",
                        "Converting raw Json to JSON Object");

                invokeStepToJson.put("fqn", "packages/middleware/pub/json/fromJson");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/jsonString");
                if (code >=400 && code <= 599) {
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/rootName_Error", "string", "/rootName");
                } else {
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/rootName_Response", "string", "/rootName");
                }

                createPostInvokeMapping(invokeStepToJson, "copy", "document", "/output", "document", "/*" + code);
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases, "CASE", "Handling XML response");
                addData(sequenceContentTypeMapping, "case", "#regex:.*xml.*");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase, "service", "INVOKE", "Initialize");

                invokeStepToJson.put("fqn", "packages/middleware/pub/xml/fromXML");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/xml");
                createPostInvokeMapping(invokeStepToJson, "copy", "document/document", "/output/root", "document/document", "/*" + code + "/root");
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
        } else {
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases, "CASE", "Handling JSON response");
                addData(sequenceContentTypeMapping, "case", "#regex:.*json.*");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                createVariables(intiMapStep, "rootName_Response", "response", null, "string");
                createVariables(intiMapStep, "rootName_Error", "error", null, "string");

                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase, "service", "INVOKE", "Converting raw Json to JSON Object");

                invokeStepToJson.put("fqn", "packages/middleware/pub/json/fromJson");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/jsonString");
                if (code >=400 && code <= 599) {
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/rootName_Error", "string", "/rootName");
                } else {
                    createPreInvokeMapping(invokeStepToJson, "copy", "string", "/rootName_Response", "string", "/rootName");
                }
                createPostInvokeMapping(invokeStepToJson, "copy", "document", "/output", "document", "/*" + code);
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
            {
                Map<String, Object> sequenceContentTypeMapping = createCase(contentTypeCases, "CASE", "Handling XML response");
                addData(sequenceContentTypeMapping, "case", "#regex:.*xml.*");

                List<Object> jsonConversionCase = com.beust.jcommander.internal.Lists.newArrayList();
                Map<String, Object> invokeStepToJson = createInvokeStep(jsonConversionCase, "service", "INVOKE", "Initialize");

                invokeStepToJson.put("fqn", "packages/middleware/pub/xml/fromXML");

                createPreInvokeMapping(invokeStepToJson, "copy", "string", "/respPayload", "string", "/xml");
                createPostInvokeMapping(invokeStepToJson, "copy", "document/document", "/output/root", "document/document", "/*" + code + "/root");
                addChildren(sequenceContentTypeMapping, jsonConversionCase);
            }
        }
        addChildren(switchContentTypeMapping, contentTypeCases);
        return switchContentTypeMapping;
    }

    private static String postmanToSyncloopProps(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return str.replaceAll(Pattern.quote("{{"), "#{").replaceAll(Pattern.quote("}}"), "}");
    }

    private static String postmanToSyncloopProps(String str, String parentDocument) {
        return str.replaceAll(Pattern.quote("{{"), "#{" + parentDocument + "/").replaceAll(Pattern.quote("}}"), "}");
    }

    private static String replacePathParametersForClient(String inputURL, Map<String, Object> initMapStep, String dependencyFolderPath) {

        int queryStartIndex = inputURL.indexOf('?');
        String baseURL = queryStartIndex != -1 ? inputURL.substring(0, queryStartIndex) : inputURL;
        String queryString = queryStartIndex != -1 ? inputURL.substring(queryStartIndex) : "";

        StringBuilder modifiedURL = new StringBuilder();

        String[] segments = baseURL.split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.startsWith(":")) {
                String paramName = segment.substring(1);
                segment = "#{pathParameters/" + paramName + "}";
            } else if (segment.startsWith("#{")) {
                int closingBraceIndex = segment.indexOf("}");
                if (closingBraceIndex != -1) {
                    String paramName = segment.substring(2, closingBraceIndex);
                    segment = "#{pathParameters/" + paramName + "}" + segment.substring(closingBraceIndex + 1);
                }
            }
            modifiedURL.append("/").append(segment);
        }

        if (modifiedURL.length() > 0 && modifiedURL.charAt(0) == '/') {
            modifiedURL.deleteCharAt(0);
        }
        modifiedURL.append(queryString);
        return modifiedURL.toString();
    }

    private static Evaluate evaluateFrom(String key, Evaluate evaluateFrom) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        Evaluate markEvaluation = evaluateFrom;
        if (!key.contains("{{")) {
            markEvaluation = null;
        }
        return markEvaluation;
    }

    public static void generateJavaClass(File file, String flowRef, String fqn, String alias) throws Exception {
        String flowJavaTemplatePath = MiddlewareServer.getConfigFolderPath() + "apiJava.template";

        String className = file.getName().replace(".api", "");

        String fullCode = "";
        String pkg = StringUtils.strip(flowRef.replace("/" + file.getName(), "").replace("/", ".")
                .replace("\\" + file.getName(), "").replace("\\", "."), ".");
        List<String> lines = FileUtils.readLines(new File(flowJavaTemplatePath), "UTF-8");
        for (String line : lines) {
            String codeLine = (line.replace("#flowRef", flowRef.replaceAll(Pattern.quote(File.separator), "/") + "/" + file.getName()).replace("#package", pkg).replace("#className", className));
            fullCode += codeLine + "\n";

        }

        String javaFilePath = file.getAbsolutePath().replace(className + ".api", className + ".java");
        File javaFile = new File(javaFilePath);
        if (!javaFile.exists()) {
            javaFile.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(javaFile);
        fos.write(fullCode.getBytes());
        fos.flush();
        fos.close();

        //dataPipeline.log("fqn: " + fqn);


       /* if (StringUtils.isNotBlank(alias)) {
            ServiceUtils.registerURLAlias(fqn, alias.replaceAll("#", ""), dataPipeline);
        }*/

    }

    public static void createFolderStructure(String filePath, String fileName, Map<String, String> properties) {
        File mainFolderPath = new File(filePath);
        if (!mainFolderPath.exists()) {
            boolean folderCreated = mainFolderPath.mkdirs();
            if (!folderCreated) {
                return;
            }
        }

        File propertiesFile = new File(mainFolderPath, fileName);
        Map<String, String> existingProperties = new java.util.HashMap<>();

        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(propertiesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        existingProperties.put(parts[0], parts[1]);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileWriter writer = new FileWriter(propertiesFile)) {
            for (Map.Entry<String, String> entry : existingProperties.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!existingProperties.containsKey(entry.getKey())) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}