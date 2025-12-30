package com.lixiangyu.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apache HttpClient 工具类
 * 提供常用的 HTTP 请求方法封装
 *
 * @author lixiangyu
 */
@Slf4j
public class HttpClientUtil {

    /**
     * 默认连接超时时间（毫秒）
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    /**
     * 默认读取超时时间（毫秒）
     */
    private static final int DEFAULT_SOCKET_TIMEOUT = 10000;

    /**
     * 默认请求超时时间（毫秒）
     */
    private static final int DEFAULT_REQUEST_TIMEOUT = 10000;

    /**
     * 创建默认的 HttpClient
     *
     * @return CloseableHttpClient
     */
    public static CloseableHttpClient createHttpClient() {
        return createHttpClient(DEFAULT_CONNECT_TIMEOUT, DEFAULT_SOCKET_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * 创建自定义超时的 HttpClient
     *
     * @param connectTimeout 连接超时时间（毫秒）
     * @param socketTimeout  读取超时时间（毫秒）
     * @param requestTimeout 请求超时时间（毫秒）
     * @return CloseableHttpClient
     */
    public static CloseableHttpClient createHttpClient(int connectTimeout, int socketTimeout, int requestTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
                .setConnectionRequestTimeout(requestTimeout)
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * GET 请求
     *
     * @param url 请求地址
     * @return 响应内容
     */
    public static String doGet(String url) {
        return doGet(url, null, null);
    }

    /**
     * GET 请求（带请求头）
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return 响应内容
     */
    public static String doGet(String url, Map<String, String> headers) {
        return doGet(url, headers, null);
    }

    /**
     * GET 请求（带参数）
     *
     * @param url    请求地址
     * @param params 请求参数
     * @return 响应内容
     */
    public static String doGet(String url, Map<String, String> headers, Map<String, String> params) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            // 构建 URL（带参数）
            String fullUrl = buildUrlWithParams(url, params);
            HttpGet httpGet = new HttpGet(fullUrl);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpGet::setHeader);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("GET 请求失败，URL: {}", url, e);
            throw new RuntimeException("GET 请求失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * POST 请求（JSON）
     *
     * @param url  请求地址
     * @param json JSON 字符串
     * @return 响应内容
     */
    public static String doPostJson(String url, String json) {
        return doPostJson(url, json, null);
    }

    /**
     * POST 请求（JSON，带请求头）
     *
     * @param url     请求地址
     * @param json    JSON 字符串
     * @param headers 请求头
     * @return 响应内容
     */
    public static String doPostJson(String url, String json, Map<String, String> headers) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            HttpPost httpPost = new HttpPost(url);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpPost::setHeader);
            } else {
                // 默认设置 Content-Type
                httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            }

            // 设置请求体
            if (json != null) {
                StringEntity entity = new StringEntity(json, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("POST 请求失败，URL: {}, JSON: {}", url, json, e);
            throw new RuntimeException("POST 请求失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * POST 请求（表单）
     *
     * @param url    请求地址
     * @param params 表单参数
     * @return 响应内容
     */
    public static String doPostForm(String url, Map<String, String> params) {
        return doPostForm(url, params, null);
    }

    /**
     * POST 请求（表单，带请求头）
     *
     * @param url     请求地址
     * @param params  表单参数
     * @param headers 请求头
     * @return 响应内容
     */
    public static String doPostForm(String url, Map<String, String> params, Map<String, String> headers) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            HttpPost httpPost = new HttpPost(url);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpPost::setHeader);
            } else {
                // 默认设置 Content-Type
                httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            }

            // 设置表单参数
            if (params != null && !params.isEmpty()) {
                List<NameValuePair> formParams = new ArrayList<>();
                params.forEach((key, value) -> formParams.add(new BasicNameValuePair(key, value)));
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("POST 表单请求失败，URL: {}, Params: {}", url, params, e);
            throw new RuntimeException("POST 表单请求失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * POST 请求（文件上传）
     *
     * @param url      请求地址
     * @param file     文件
     * @param fileName 文件名
     * @param params   其他参数
     * @return 响应内容
     */
    public static String doPostFile(String url, File file, String fileName, Map<String, String> params) {
        return doPostFile(url, file, fileName, params, null);
    }

    /**
     * POST 请求（文件上传，带请求头）
     *
     * @param url      请求地址
     * @param file     文件
     * @param fileName 文件名
     * @param params   其他参数
     * @param headers  请求头
     * @return 响应内容
     */
    public static String doPostFile(String url, File file, String fileName, Map<String, String> params, Map<String, String> headers) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            HttpPost httpPost = new HttpPost(url);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpPost::setHeader);
            }

            // 构建 Multipart 实体
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            if (file != null && file.exists()) {
                builder.addPart("file", new FileBody(file, ContentType.DEFAULT_BINARY, fileName));
            }
            if (params != null) {
                params.forEach((key, value) -> {
                    try {
                        builder.addPart(key, new StringBody(value, ContentType.TEXT_PLAIN));
                    } catch (Exception e) {
                        log.error("添加表单参数失败，Key: {}, Value: {}", key, value, e);
                    }
                });
            }
            httpPost.setEntity(builder.build());

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("POST 文件上传失败，URL: {}, File: {}", url, file, e);
            throw new RuntimeException("POST 文件上传失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * PUT 请求（JSON）
     *
     * @param url  请求地址
     * @param json JSON 字符串
     * @return 响应内容
     */
    public static String doPutJson(String url, String json) {
        return doPutJson(url, json, null);
    }

    /**
     * PUT 请求（JSON，带请求头）
     *
     * @param url     请求地址
     * @param json    JSON 字符串
     * @param headers 请求头
     * @return 响应内容
     */
    public static String doPutJson(String url, String json, Map<String, String> headers) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            HttpPut httpPut = new HttpPut(url);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpPut::setHeader);
            } else {
                // 默认设置 Content-Type
                httpPut.setHeader("Content-Type", "application/json;charset=UTF-8");
            }

            // 设置请求体
            if (json != null) {
                StringEntity entity = new StringEntity(json, StandardCharsets.UTF_8);
                httpPut.setEntity(entity);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("PUT 请求失败，URL: {}, JSON: {}", url, json, e);
            throw new RuntimeException("PUT 请求失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * DELETE 请求
     *
     * @param url 请求地址
     * @return 响应内容
     */
    public static String doDelete(String url) {
        return doDelete(url, null);
    }

    /**
     * DELETE 请求（带请求头）
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return 响应内容
     */
    public static String doDelete(String url, Map<String, String> headers) {
        CloseableHttpClient httpClient = createHttpClient();
        try {
            HttpDelete httpDelete = new HttpDelete(url);

            // 设置请求头
            if (headers != null) {
                headers.forEach(httpDelete::setHeader);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpDelete)) {
                return handleResponse(response);
            }
        } catch (Exception e) {
            log.error("DELETE 请求失败，URL: {}", url, e);
            throw new RuntimeException("DELETE 请求失败: " + e.getMessage(), e);
        } finally {
            closeHttpClient(httpClient);
        }
    }

    /**
     * 处理响应
     *
     * @param response HTTP 响应
     * @return 响应内容
     * @throws IOException IO 异常
     */
    private static String handleResponse(CloseableHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return result;
            } else {
                log.warn("HTTP 请求返回非成功状态码: {}, 响应内容: {}", statusCode, result);
                throw new RuntimeException("HTTP 请求失败，状态码: " + statusCode + ", 响应: " + result);
            }
        }
        return null;
    }

    /**
     * 构建带参数的 URL
     *
     * @param url    原始 URL
     * @param params 参数
     * @return 完整 URL
     */
    private static String buildUrlWithParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        if (url.contains("?")) {
            sb.append("&");
        } else {
            sb.append("?");
        }
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * 关闭 HttpClient
     *
     * @param httpClient HttpClient
     */
    private static void closeHttpClient(CloseableHttpClient httpClient) {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("关闭 HttpClient 失败", e);
            }
        }
    }
}

