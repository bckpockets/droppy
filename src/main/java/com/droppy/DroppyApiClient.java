package com.droppy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class DroppyApiClient
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiBase;

    public DroppyApiClient(OkHttpClient httpClient, Gson gson, String apiBase)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }

    public void submitDry(String playerName, String response)
    {
        JsonObject body = new JsonObject();
        body.addProperty("name", playerName);
        body.addProperty("response", response);

        Request request = new Request.Builder()
            .url(apiBase + "/dry")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();

        try (Response resp = httpClient.newCall(request).execute())
        {
            if (!resp.isSuccessful())
            {
                log.debug("Failed to submit dry stats: {}", resp.code());
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to submit dry stats: {}", e.getMessage());
        }
    }

    public String lookupDry(String playerName)
    {
        Request request = new Request.Builder()
            .url(apiBase + "/dry?name=" + playerName)
            .get()
            .build();

        try (Response resp = httpClient.newCall(request).execute())
        {
            if (resp.isSuccessful() && resp.body() != null)
            {
                return resp.body().string();
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to lookup dry stats for {}: {}", playerName, e.getMessage());
        }

        return null;
    }
}
