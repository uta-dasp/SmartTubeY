package com.liskovsoft.smartyoutubetv2.tv.ui.personal.channel;

import android.net.Uri;
import com.liskovsoft.googlecommon.common.constants.ConstantsService;
import com.liskovsoft.googlecommon.common.constants.data.ConstantsResult;
import com.liskovsoft.sharedutils.helpers.NetworkHelpers;
import io.reactivex.Observable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Public channel display data only. No account tokens, cookies or copied artwork are stored here. */
final class PersonalChannelMetadata {
    String channelId,title,handle,description,avatarUrl,bannerUrl,subscribers;

    static Observable<PersonalChannelMetadata> load(String channelId) {
        return Observable.create(emitter->{
            AtomicReference<HttpURLConnection> active=new AtomicReference<>();
            emitter.setCancellable(()->{HttpURLConnection connection=active.getAndSet(null);if(connection!=null)connection.disconnect();});
            try {
                // The same runtime API configuration used by SmartTube's YouTubeDataService.
                // The existing snippet adapter drops description/handle and does not request brandingSettings.
                ConstantsResult constants=ConstantsService.getConstants();
                String apiKey=constants==null?null:constants.getYoutubeDataApiKey();
                if(emitter.isDisposed())return;
                if(apiKey==null||channelId==null)throw new IllegalStateException("Channel metadata unavailable");
                Uri.Builder address=Uri.parse("https://www.googleapis.com/youtube/v3/channels").buildUpon()
                        .appendQueryParameter("part","snippet,statistics,brandingSettings")
                        .appendQueryParameter("key",apiKey);
                if(channelId.startsWith("@"))address.appendQueryParameter("forHandle",channelId);
                else address.appendQueryParameter("id",channelId);
                HttpURLConnection connection=NetworkHelpers.getHttpsURLConnection(new URL(address.build().toString()));
                active.set(connection);connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");
                if(emitter.isDisposed()){connection.disconnect();return;}
                if(connection.getResponseCode()!=200)throw new IllegalStateException("Channel metadata unavailable");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()){
                    byte[] buffer=new byte[8192];int count;
                    while((count=input.read(buffer))!=-1){
                        if(emitter.isDisposed())return;
                        if(bytes.size()+count>1048576)throw new IllegalStateException("Channel metadata response too large");
                        bytes.write(buffer,0,count);
                    }
                }
                JSONObject response=new JSONObject(bytes.toString("UTF-8"));
                JSONArray items=response.optJSONArray("items");
                if(items==null||items.length()==0)throw new IllegalStateException("Channel metadata unavailable");
                JSONObject item=items.getJSONObject(0),snippet=item.optJSONObject("snippet");
                PersonalChannelMetadata result=new PersonalChannelMetadata();result.channelId=value(item,"id");
                result.title=value(snippet,"title");result.description=value(snippet,"description");result.handle=value(snippet,"customUrl");
                JSONObject thumbnails=snippet==null?null:snippet.optJSONObject("thumbnails");
                for(String size:new String[]{"high","medium","default"}){
                    result.avatarUrl=value(thumbnails==null?null:thumbnails.optJSONObject(size),"url");if(result.avatarUrl!=null)break;
                }
                JSONObject branding=item.optJSONObject("brandingSettings");
                // Banner URLs come from the current public API response; no reference media is bundled.
                result.bannerUrl=value(branding==null?null:branding.optJSONObject("image"),"bannerExternalUrl");
                JSONObject statistics=item.optJSONObject("statistics");
                if(statistics!=null&&!statistics.optBoolean("hiddenSubscriberCount",false))result.subscribers=value(statistics,"subscriberCount");
                if(!emitter.isDisposed()){emitter.onNext(result);emitter.onComplete();}
            } catch(Exception error) {
                // Do not propagate request URLs or runtime API configuration into UI/log output.
                if(!emitter.isDisposed())emitter.tryOnError(new IllegalStateException("Channel metadata unavailable"));
            } finally {HttpURLConnection connection=active.getAndSet(null);if(connection!=null)connection.disconnect();}
        });
    }

    private static String value(JSONObject object,String key){
        String value=object==null?null:object.optString(key,null);
        return value==null||value.isEmpty()||"null".equals(value)?null:value;
    }
}
