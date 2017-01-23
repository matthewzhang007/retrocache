package com.andiag.retrocache;

import android.support.annotation.NonNull;

import com.andiag.retrocache.interfaces.CachedCall;
import com.andiag.retrocache.utils.MockCachingSystem;
import com.andiag.retrocache.utils.ToStringConverterFactory;
import com.andiag.retrocache.utils.Utils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by IagoCanalejas on 10/01/2017.
 */

public class CallbackTest {
    /***
     * Builds a Retrofit SmartCache factory without Android executor
     */
    private CallAdapter.Factory buildSmartCacheFactory() {
        return new CachedCallFactory(mMockCachingSystem, new MainThreadExecutor());
    }

    @Rule
    public final MockWebServer mServer = new MockWebServer();
    private Retrofit mRetrofit;
    private MockCachingSystem mMockCachingSystem = new MockCachingSystem();

    @Before
    public void setUp() {
        mRetrofit = new Retrofit.Builder()
                .baseUrl(mServer.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .addCallAdapterFactory(buildSmartCacheFactory())
                .build();
    }

    @Test
    public void simpleCall() throws InterruptedException {
        /* Set up the mock webserver */
        MockResponse resp = new MockResponse().setBody("VERY_BASIC_BODY");
        mServer.enqueue(resp);

        DemoService demoService = mRetrofit.create(DemoService.class);

        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                assertEquals(response.body(), "VERY_BASIC_BODY");
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
                fail("Failure executing the request");
            }
        });

    }

    @Test
    public void cachedCall() throws Exception {
        /* Set up the mock webserver */
        MockResponse resp = new MockResponse().setBody("VERY_BASIC_BODY");
        mServer.enqueue(resp);

        DemoService demoService = mRetrofit.create(DemoService.class);
        CachedCall<String> call = demoService.getHome();

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                assertEquals(response.body(), "VERY_BASIC_BODY");
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });

        assertNotNull(mMockCachingSystem.get(Utils.urlToKey(call.request().url())));
    }

    @Test
    public void removedCall() throws Exception {
        /* Set up the mock webserver */
        MockResponse resp = new MockResponse().setBody("VERY_BASIC_BODY");
        mServer.enqueue(resp);

        Retrofit r = new Retrofit.Builder()
                .baseUrl(mServer.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .addCallAdapterFactory(buildSmartCacheFactory())
                .build();
        DemoService demoService = r.create(DemoService.class);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Response<String>> responseRef = new AtomicReference<>();
        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(responseRef.get().body(), "VERY_BASIC_BODY");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<Response<String>> response2Ref = new AtomicReference<>();
        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                latch2.countDown();
                if (latch2.getCount() == 0) { // the cache hit one.
                    response2Ref.set(response);
                } else { // the network one.
                    assertEquals(response.body(), response2Ref.get().body());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch2.await(1, TimeUnit.SECONDS));

        // Remove cached call
        demoService.getHome().remove();
        final CountDownLatch latch3 = new CountDownLatch(1);
        final AtomicReference<Response<String>> response3Ref = new AtomicReference<>();
        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                response3Ref.set(response);
                latch3.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch3.await(1, TimeUnit.SECONDS));
        assertEquals(response3Ref.get().body(), "VERY_BASIC_BODY");
        mServer.shutdown();
    }

    @Test
    public void refreshCall() throws Exception {
        /* Set up the mock webserver */
        MockResponse resp = new MockResponse().setBody("VERY_BASIC_BODY");
        mServer.enqueue(resp);

        Retrofit r = new Retrofit.Builder()
                .baseUrl(mServer.url("/"))
                .addConverterFactory(new ToStringConverterFactory())
                .addCallAdapterFactory(buildSmartCacheFactory())
                .build();
        DemoService demoService = r.create(DemoService.class);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Response<String>> responseRef = new AtomicReference<>();
        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(responseRef.get().body(), "VERY_BASIC_BODY");

        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<Response<String>> response2Ref = new AtomicReference<>();
        demoService.getHome().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                latch2.countDown();
                if (latch2.getCount() == 0) { // the cache hit one.
                    response2Ref.set(response);
                } else { // the network one.
                    assertEquals(response.body(), response2Ref.get().body());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch2.await(1, TimeUnit.SECONDS));

        // Remove cached call
        final CountDownLatch latch3 = new CountDownLatch(1);
        final AtomicReference<Response<String>> response3Ref = new AtomicReference<>();
        demoService.getHome().refresh(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                response3Ref.set(response);
                latch3.countDown();
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                fail("Failure executing the request: " + t.getMessage());
            }
        });
        assertTrue(latch3.await(1, TimeUnit.SECONDS));
        assertEquals(response3Ref.get().body(), "VERY_BASIC_BODY");
        mServer.shutdown();
    }

    private static class MainThreadExecutor implements Executor {
        @Override
        public void execute(@NonNull Runnable command) {
            command.run();
        }
    }

    interface DemoService {
        @GET("/")
        CachedCall<String> getHome();
    }
}
