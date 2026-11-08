package com.liskovsoft.smartyoutubetv2.tv.ui.personal.channel;

import android.app.Application;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,manifest=Config.NONE,sdk=28)
public class PersonalChannelMetadataTest {
    @Test public void modernHeaderPreservesDisplayFieldsAndFullBannerCanvas() throws Exception {
        JSONObject response=new JSONObject("{\"metadata\":{\"channelMetadataRenderer\":{"
                +"\"externalId\":\"UCexample\",\"title\":\"Example\",\"description\":\"Full description\","
                +"\"vanityChannelUrl\":\"https://www.youtube.com/@Example\","
                +"\"avatar\":{\"thumbnails\":[{\"url\":\"small\",\"width\":88},{\"url\":\"large\",\"width\":900}]}}},"
                +"\"header\":{\"pageHeaderRenderer\":{\"content\":{\"pageHeaderViewModel\":{"
                +"\"banner\":{\"imageBannerViewModel\":{\"image\":{\"sources\":[{\"width\":2560,"
                +"\"url\":\"https://yt3.googleusercontent.com/public-image=w2560-fcrop64=1,00000000ffffffff\"}]}}},"
                +"\"metadata\":{\"contentMetadataViewModel\":{\"metadataRows\":["
                +"{\"metadataParts\":[{\"text\":{\"content\":\"@Example\"}}]},"
                +"{\"metadataParts\":[{\"text\":{\"content\":\"12,3 Mio. Abonnenten\"}},"
                +"{\"text\":{\"content\":\"200 Videos\"}}]}]}}}}}}}");
        PersonalChannelMetadata result=PersonalChannelMetadata.parseBrowse(response,"UCrequested");
        assertEquals("UCexample",result.channelId);
        assertEquals("Example",result.title);
        assertEquals("@Example",result.handle);
        assertEquals("Full description",result.description);
        assertEquals("large",result.avatarUrl);
        assertEquals("12,3 Mio. Abonnenten",result.statisticsText);
        assertEquals("https://yt3.googleusercontent.com/public-image=w1920",result.bannerUrl);
    }

    @Test public void legacyHeaderRetainsItsExplicitTvBanner() throws Exception {
        JSONObject response=new JSONObject("{\"header\":{\"c4TabbedHeaderRenderer\":{"
                +"\"channelId\":\"UClegacy\",\"title\":\"Legacy\","
                +"\"channelHandleText\":{\"runs\":[{\"text\":\"@Legacy\"}]},"
                +"\"subscriberCountText\":{\"simpleText\":\"100 subscribers\"},"
                +"\"tvBanner\":{\"thumbnails\":[{\"width\":1920,\"url\":\"https://example.invalid/tv.png\"}]},"
                +"\"banner\":{\"thumbnails\":[{\"width\":2560,\"url\":\"https://example.invalid/web.png\"}]}}}}");
        PersonalChannelMetadata result=PersonalChannelMetadata.parseBrowse(response,"UCrequested");
        assertEquals("UClegacy",result.channelId);
        assertEquals("@Legacy",result.handle);
        assertEquals("100 subscribers",result.statisticsText);
        assertEquals("https://example.invalid/tv.png",result.bannerUrl);
        assertNull(result.description);
    }

    @Test public void partialChannelMetadataDoesNotInventCountsOrArtwork() throws Exception {
        JSONObject response=new JSONObject("{\"metadata\":{\"channelMetadataRenderer\":{"
                +"\"externalId\":\"UCpartial\",\"title\":\"Partial\","
                +"\"ownerUrls\":[\"https://www.youtube.com/@Partial\"]}}}");
        PersonalChannelMetadata result=PersonalChannelMetadata.parseBrowse(response,"UCrequested");
        assertEquals("@Partial",result.handle);
        assertNull(result.statisticsText);
        assertNull(result.bannerUrl);
        assertNull(result.avatarUrl);
        assertNull(result.description);
    }

    @Test(expected=JSONException.class) public void errorPayloadCannotBecomeEmptySuccessfulMetadata() throws Exception {
        PersonalChannelMetadata.parseBrowse(new JSONObject("{\"error\":{\"code\":503}}"),"UCrequested");
    }
}
