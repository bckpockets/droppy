package com.droppy;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DroppyApiClientTest
{
    @Test
    public void lookupEncodesPlayerNameAsQueryParameter()
    {
        AtomicReference<String> requestedName = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain ->
            {
                requestedName.set(chain.request().url().queryParameter("name"));
                return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(MediaType.parse("text/plain"), "dry stats"))
                    .build();
            })
            .build();

        DroppyApiClient api = new DroppyApiClient(client, new Gson(), "https://example.com");
        assertEquals("dry stats", api.lookupDry("SIP YE & Friends"));
        assertEquals("SIP YE & Friends", requestedName.get());
    }
}
