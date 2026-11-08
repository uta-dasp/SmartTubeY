package com.liskovsoft.smartyoutubetv2.tv.ui.personal.channel;

import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;
import com.liskovsoft.googlecommon.common.helpers.RetrofitOkHttpHelper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Display fields from the current public channel browse response; no reference artwork is bundled. */
final class PersonalChannelMetadata {
    private static final String TAG = "PersonalChannelMetadata";
    private static final String BANNER_TAG = "STYChannelBanner";
    private static final int MAX_RESPONSE_BYTES = 1048576;
    String channelId,title,handle,description,avatarUrl,bannerUrl,subscribers,statisticsText;

    static Observable<PersonalChannelMetadata> load(String channelId) {
        return Observable.<PersonalChannelMetadata>create(emitter -> {
            AtomicReference<Call> active = new AtomicReference<>();
            emitter.setCancellable(() -> { Call call=active.getAndSet(null); if(call!=null)call.cancel(); });
            try {
                if(channelId==null)throw new MetadataFailure("missing_channel");
                String canonicalId=channelId;
                if(channelId.startsWith("@")) {
                    JSONObject resolved=request("navigation/resolve_url","endpoint",
                            "\"url\":"+JSONObject.quote("https://www.youtube.com/"+channelId),active,emitter);
                    canonicalId=value(at(resolved,"endpoint","browseEndpoint"),"browseId");
                    if(canonicalId==null)throw new MetadataFailure("handle_not_resolved");
                }
                JSONObject response=request("browse","metadata,header",
                        "\"browseId\":"+JSONObject.quote(canonicalId),active,emitter);
                PersonalChannelMetadata result=parseBrowse(response,canonicalId);
                android.util.Log.i(BANNER_TAG,"metadata_ready banner="+(result.bannerUrl!=null));
                Log.d(TAG,"Public channel fields: title=%s handle=%s description=%s banner=%s",
                        result.title!=null,result.handle!=null,result.description!=null,result.bannerUrl!=null);
                if(!emitter.isDisposed()){emitter.onNext(result);emitter.onComplete();}
            } catch(Exception error) {
                if(!emitter.isDisposed()) {
                    // Never include exception messages, request URLs, headers, bodies or credentials.
                    String reason=error instanceof MetadataFailure?((MetadataFailure)error).reason:error.getClass().getSimpleName();
                    Log.w(TAG,"Public channel metadata unavailable: %s",reason);
                    android.util.Log.w(BANNER_TAG,"metadata_failed cause="+error.getClass().getSimpleName());
                    emitter.tryOnError(new IllegalStateException("Channel metadata unavailable"));
                }
            } finally { Call call=active.getAndSet(null); if(call!=null)call.cancel(); }
        }).retry(1);
    }

    private static JSONObject request(String endpoint,String fields,String data,AtomicReference<Call> active,
                                      ObservableEmitter<?> emitter) throws IOException,JSONException {
        // Reuse the same current client/query and configured DNS, proxy, TLS and decompression as BrowseService2.
        Request request=new Request.Builder().url("https://www.youtube.com/youtubei/v1/"+endpoint+"?fields="+fields)
                .header("User-Agent",DefaultHeaders.USER_AGENT_WEB).header("Referer","https://www.youtube.com/")
                .post(RequestBody.create(MediaType.parse("application/json"),PostDataHelper.createQueryWeb(data))).build();
        RetrofitOkHttpHelper.addAuthSkip(request); // Display metadata is public, including for signed-in accounts.
        Call call=ClientHolder.CLIENT.newCall(request);active.set(call);
        if(emitter.isDisposed()){call.cancel();throw new MetadataFailure("cancelled");}
        try(Response response=call.execute()) {
            Log.d(TAG,"Public channel metadata HTTP status=%s",response.code());
            android.util.Log.i(BANNER_TAG,"metadata_http status="+response.code());
            if(!response.isSuccessful())throw new MetadataFailure("http_"+response.code());
            ResponseBody body=response.body();
            if(body==null)throw new MetadataFailure("empty_response");
            if(body.contentLength()>MAX_RESPONSE_BYTES)throw new MetadataFailure("response_too_large");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=body.byteStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1) {
                    if(emitter.isDisposed())throw new MetadataFailure("cancelled");
                    if(bytes.size()+count>MAX_RESPONSE_BYTES)throw new MetadataFailure("response_too_large");
                    bytes.write(buffer,0,count);
                }
            }
            return new JSONObject(bytes.toString("UTF-8"));
        } finally { active.compareAndSet(call,null); }
    }

    private static final class ClientHolder {
        static final OkHttpClient CLIENT=createClient();
        private static OkHttpClient createClient() {
            OkHttpClient.Builder builder=RetrofitOkHttpHelper.getClient().newBuilder()
                    .connectTimeout(12,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).writeTimeout(12,TimeUnit.SECONDS);
            // Keep the shared network configuration, but no payload/URL debug logging for this adapter.
            Iterator<Interceptor> interceptors=builder.interceptors().iterator();
            while(interceptors.hasNext()) {
                String name=interceptors.next().getClass().getSimpleName();
                if("HttpLoggingInterceptor".equals(name)||"OkHttpProfilerInterceptor".equals(name))interceptors.remove();
            }
            return builder.build();
        }
    }

    static PersonalChannelMetadata parseBrowse(JSONObject response,String requestedId) throws JSONException {
        JSONObject metadata=at(response,"metadata","channelMetadataRenderer");
        JSONObject header=at(response,"header");
        JSONObject page=at(header,"pageHeaderRenderer");
        JSONObject model=at(page,"content","pageHeaderViewModel");
        JSONObject legacy=at(header,"c4TabbedHeaderRenderer");
        if(legacy==null)legacy=at(header,"interactiveTabbedHeaderRenderer");
        PersonalChannelMetadata result=new PersonalChannelMetadata();
        result.channelId=first(value(metadata,"externalId"),value(legacy,"channelId"),requestedId);
        result.title=first(value(metadata,"title"),value(page,"pageTitle"),value(legacy,"title"),text(at(legacy,"title")));
        result.description=first(value(metadata,"description"),text(at(model,"description","descriptionPreviewViewModel","description")));
        result.handle=handle(value(metadata,"vanityChannelUrl"));
        JSONArray ownerUrls=metadata==null?null:metadata.optJSONArray("ownerUrls");
        if(result.handle==null&&ownerUrls!=null)for(int index=0;index<ownerUrls.length();index++) {
            result.handle=handle(ownerUrls.optString(index,null));if(result.handle!=null)break;
        }
        result.handle=first(result.handle,text(at(legacy,"channelHandleText")));
        result.avatarUrl=first(image(at(metadata,"avatar")),image(at(model,"image","decoratedAvatarViewModel","avatar","avatarViewModel","image")),image(at(legacy,"avatar")));
        String tvBanner=image(at(legacy,"tvBanner"));
        result.bannerUrl=tvBanner!=null?tvBanner:fullBanner(first(image(at(model,"banner","imageBannerViewModel","image")),image(at(legacy,"banner"))));
        result.statisticsText=text(at(legacy,"subscriberCountText"));
        JSONObject displayMetadata=at(model,"metadata","contentMetadataViewModel");
        JSONArray rows=displayMetadata==null?null:displayMetadata.optJSONArray("metadataRows");
        if(rows!=null)for(int row=0;row<rows.length();row++) {
            JSONObject entry=rows.optJSONObject(row);JSONArray parts=entry==null?null:entry.optJSONArray("metadataParts");
            if(parts==null)continue;
            for(int part=0;part<parts.length();part++) {
                JSONObject item=parts.optJSONObject(part);String label=text(at(item,"text"));
                if(label==null)continue;
                if(label.startsWith("@"))result.handle=first(result.handle,label);
                else if(part==0&&result.statisticsText==null)result.statisticsText=label;
            }
        }
        if(result.title==null||result.channelId==null)throw new JSONException("Channel display metadata missing");
        return result;
    }

    private static String image(JSONObject object) {
        if(object==null)return null;
        JSONArray images=object.optJSONArray("sources");if(images==null)images=object.optJSONArray("thumbnails");
        if(images==null)return value(object,"url");
        String result=null;int width=-1;
        for(int index=0;index<images.length();index++) {
            JSONObject candidate=images.optJSONObject(index);String url=value(candidate,"url");
            if(url!=null&&candidate.optInt("width",0)>width){result=url;width=candidate.optInt("width",0);}
        }
        return result;
    }

    private static String fullBanner(String url) {
        if(url==null)return null;
        // WEB specifies a shallow crop; the same live image also serves its full TV canvas.
        if(url.startsWith("https://yt3.googleusercontent.com/")||url.startsWith("https://yt3.ggpht.com/")) {
            int transform=url.indexOf("=w");if(transform>=0)return url.substring(0,transform)+"=w1920";
        }
        return url;
    }

    private static String handle(String url) {
        if(url==null)return null;if(url.startsWith("@"))return url;
        try{String path=new URI(url).getPath();return path!=null&&path.startsWith("/@")?path.substring(1):null;}
        catch(Exception ignored){return null;}
    }

    private static String text(JSONObject object) {
        String direct=first(value(object,"content"),value(object,"simpleText"));if(direct!=null)return direct;
        JSONArray runs=object==null?null:object.optJSONArray("runs");if(runs==null)return null;
        StringBuilder result=new StringBuilder();for(int index=0;index<runs.length();index++) {
            String part=value(runs.optJSONObject(index),"text");if(part!=null)result.append(part);
        }
        return result.length()==0?null:result.toString();
    }

    private static JSONObject at(JSONObject object,String...path) {
        for(String name:path){if(object==null)return null;object=object.optJSONObject(name);}return object;
    }
    private static String first(String...values){for(String value:values)if(value!=null)return value;return null;}
    private static String value(JSONObject object,String key){Object raw=object==null?null:object.opt(key);String value=raw instanceof String?(String)raw:null;return value==null||value.isEmpty()||"null".equals(value)?null:value;}
    private static final class MetadataFailure extends IOException {final String reason;MetadataFailure(String reason){this.reason=reason;}}
}
