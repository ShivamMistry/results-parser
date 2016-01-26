package com.speed.evision;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.print.Doc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SitsAuth {

    protected static final String YORK_URL = "https://evision.york.ac.uk/urd/sits.urd/run/siw_sso.signon";

    public final HttpClient client;
    public final Document homepage;

    private SitsAuth(final HttpClient client, final Document homepage) {
        this.client = client;
        this.homepage = homepage;
    }

    public static SitsAuth create(final String url, final String username, final String password) throws IOException {
        // Sorry
        HttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
        HttpGet get = new HttpGet(url);
        HttpResponse response = httpClient.execute(get);
        HttpPost post = new HttpPost("https://evision.york.ac.uk/urd/sits.urd/run/SIW_LGN");
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("MUA_CODE.DUMMY.MENSYS.1", username));
        params.add(new BasicNameValuePair("PASSWORD.DUMMY.MENSYS.1",  password));
        params.add(new BasicNameValuePair("BP101.DUMMY_B.MENSYS.1", "Login"));
        Document doc = Jsoup.parse(EntityUtils.toString(response.getEntity()));
        String runtime = doc.select("input[name=\"RUNTIME.DUMMY.MENSYS.1\"]").first().attr("value");
        params.add(new BasicNameValuePair("RUNTIME.DUMMY.MENSYS.1", runtime));
        post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        response = httpClient.execute(post);
        String str = EntityUtils.toString(response.getEntity());
        doc = Jsoup.parse(str);
        String nextUrl = doc.select("#url").first().val();
        HttpGet frontPage = new HttpGet("https://evision.york.ac.uk/urd/sits.urd/run/" + nextUrl);
        response = httpClient.execute(frontPage);
        str = EntityUtils.toString(response.getEntity());
        return new SitsAuth(httpClient, Jsoup.parse(str));
    }

}
